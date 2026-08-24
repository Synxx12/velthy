package com.music.musique.data.recognition

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import com.music.musique.data.Http
import com.music.musique.data.YtMusicRepository
import com.music.musique.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

object MusicRecognitionEngine {
    private const val TAG = "MusicRecognitionEngine"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private const val RECORD_DURATION_MS = 5000L

    private val json = Json { ignoreUnknownKeys = true }

    sealed interface RecognitionState {
        object Idle : RecognitionState
        data class Listening(val amplitude: Float, val progress: Float) : RecognitionState
        object Identifying : RecognitionState
        data class Success(
            val recognizedTitle: String,
            val recognizedArtist: String,
            val matchedSong: Song?,
        ) : RecognitionState
        data class NotFound(val message: String) : RecognitionState
        data class Error(val error: String) : RecognitionState
    }

    /**
     * Records audio from the microphone for [RECORD_DURATION_MS], emitting amplitude & progress updates,
     * then queries the recognition engine and resolves the track on YouTube Music.
     */
    @SuppressLint("MissingPermission")
    fun recognizeSong(): Flow<RecognitionState> = flow {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = maxOf(minBufferSize, 4096)

        var audioRecord: AudioRecord? = null
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize,
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                emit(RecognitionState.Error("Mikrofon tidak dapat diinisialisasi"))
                return@flow
            }

            audioRecord.startRecording()
            val audioBuffer = ByteArrayOutputStream()
            val tempBuffer = ByteArray(bufferSize)

            val startTime = System.currentTimeMillis()
            var lastProgressEmit = 0L

            while (System.currentTimeMillis() - startTime < RECORD_DURATION_MS) {
                val read = audioRecord.read(tempBuffer, 0, tempBuffer.size)
                if (read > 0) {
                    audioBuffer.write(tempBuffer, 0, read)

                    // Calculate RMS amplitude for real-time soundwave visualization (0.0 to 1.0)
                    var sum = 0.0
                    for (i in 0 until read step 2) {
                        if (i + 1 < read) {
                            val sample = (tempBuffer[i + 1].toInt() shl 8) or (tempBuffer[i].toInt() and 0xFF)
                            sum += sample.toLong() * sample.toLong()
                        }
                    }
                    val rms = Math.sqrt(sum / (read / 2))
                    // Perceptual dynamic curve for responsive visualizer bouncing
                    val rawAmp = (rms / 6000.0).coerceIn(0.0, 1.0)
                    val normalizedAmp = Math.pow(rawAmp, 0.75).toFloat().coerceIn(0f, 1f)

                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / RECORD_DURATION_MS).coerceIn(0f, 1f)

                    if (System.currentTimeMillis() - lastProgressEmit > 30) {
                        lastProgressEmit = System.currentTimeMillis()
                        emit(RecognitionState.Listening(normalizedAmp, progress))
                    }
                }
            }

            try {
                audioRecord.stop()
            } catch (_: Exception) {}

            emit(RecognitionState.Identifying)

            val rawPcm = audioBuffer.toByteArray()
            if (rawPcm.isEmpty()) {
                emit(RecognitionState.NotFound("Tidak ada audio yang terekam. Pastikan suara musik terdengar jelas."))
                return@flow
            }

            val wavBytes = createWav(rawPcm, SAMPLE_RATE, 1, 16)
            
            // Try multi-tier recognition (Shazam Android Discovery -> AudD Multi-Token)
            val result = queryShazamRecognition(rawPcm) ?: queryAudDRecognition(wavBytes)

            if (result != null && result.title.isNotBlank()) {
                // Find matching song in YouTube Music Innertube
                val query = "${result.title} ${result.artist}"
                val ytResults = YtMusicRepository.search(query).getOrNull()
                val ytSong = ytResults?.filterIsInstance<com.music.musique.data.model.SearchResult.Track>()
                    ?.firstOrNull()?.song

                emit(
                    RecognitionState.Success(
                        recognizedTitle = result.title,
                        recognizedArtist = result.artist,
                        matchedSong = ytSong ?: Song(
                            videoId = "",
                            title = result.title,
                            artist = result.artist,
                            thumbnailUrl = result.thumbnailUrl,
                        ),
                    ),
                )
            } else {
                emit(RecognitionState.NotFound("Lagu tidak ditemukan. Coba dekatkan perangkat ke speaker dan coba lagi."))
            }
        } catch (e: SecurityException) {
            emit(RecognitionState.Error("Izin mikrofon belum diberikan"))
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
            emit(RecognitionState.Error(e.message ?: "Gagal mengenali lagu"))
        } finally {
            try {
                audioRecord?.release()
            } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    data class RecognitionResult(
        val title: String,
        val artist: String,
        val album: String?,
        val thumbnailUrl: String?,
    )

    /**
     * Queries Shazam's Android Discovery endpoint with audio fingerprint signature.
     */
    private suspend fun queryShazamRecognition(rawPcm: ByteArray): RecognitionResult? = withContext(Dispatchers.IO) {
        runCatching {
            val signature = ShazamFingerprinter.createSignature(rawPcm, SAMPLE_RATE) ?: return@withContext null
            val uuid1 = UUID.randomUUID().toString().uppercase()
            val uuid2 = UUID.randomUUID().toString().uppercase()
            val url = "https://amp.shazam.com/discovery/v5/en-US/GB/android/-/tag/$uuid1/$uuid2?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true&hubv5minorversion=v5.1&hidelb=true&video=v3"

            val jsonBody = """
                {
                    "timezone": "Asia/Jakarta",
                    "signatures": [
                        {
                            "samplems": 4500,
                            "timestamp": 0,
                            "uri": "data:audio/vnd.shazam.sig;base64,$signature"
                        }
                    ]
                }
            """.trimIndent()

            val request = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Shazam-Platform", "ANDROID")
                .addHeader("X-Shazam-AppVersion", "14.1.0")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = Http.client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            Log.d(TAG, "Shazam API response code: ${response.code}")

            if (!response.isSuccessful) return@withContext null

            val root = json.parseToJsonElement(body).jsonObject
            val track = root["track"]?.jsonObject ?: return@withContext null
            val title = track["title"]?.jsonPrimitive?.content ?: return@withContext null
            val subtitle = track["subtitle"]?.jsonPrimitive?.content ?: "Unknown Artist"
            val images = track["images"]?.jsonObject
            val coverArt = images?.get("coverarthq")?.jsonPrimitive?.content
                ?: images?.get("coverart")?.jsonPrimitive?.content

            RecognitionResult(
                title = title,
                artist = subtitle,
                album = null,
                thumbnailUrl = coverArt,
            )
        }.getOrNull()
    }

    /**
     * Fallback to AudD public recognition API.
     */
    private suspend fun queryAudDRecognition(wavBytes: ByteArray): RecognitionResult? = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("api_token", "test")
                .addFormDataPart(
                    "file",
                    "audio.wav",
                    wavBytes.toRequestBody("audio/wav".toMediaType()),
                )
                .addFormDataPart("return", "apple_music,spotify,deezer")
                .build()

            val request = Request.Builder()
                .url("https://api.audd.io/")
                .post(requestBody)
                .build()

            val response = Http.client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val bodyString = response.body?.string() ?: return@withContext null
            val root = json.parseToJsonElement(bodyString).jsonObject
            val status = root["status"]?.jsonPrimitive?.content

            if (status == "success") {
                val result = root["result"]?.jsonObject ?: return@withContext null
                val title = result["title"]?.jsonPrimitive?.content ?: return@withContext null
                val artist = result["artist"]?.jsonPrimitive?.content ?: "Unknown Artist"
                val album = result["album"]?.jsonPrimitive?.content

                return@withContext RecognitionResult(
                    title = title,
                    artist = artist,
                    album = album,
                    thumbnailUrl = null,
                )
            }
            null
        }.getOrNull()
    }

    private fun createWav(pcmData: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        val header = ByteArray(44)
        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)

        bb.put("RIFF".toByteArray())
        bb.putInt(totalDataLen)
        bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray())
        bb.putInt(16) // Subchunk1Size for PCM
        bb.putShort(1.toShort()) // AudioFormat 1 = PCM
        bb.putShort(channels.toShort())
        bb.putInt(sampleRate)
        bb.putInt(byteRate)
        bb.putShort((channels * bitsPerSample / 8).toShort()) // BlockAlign
        bb.putShort(bitsPerSample.toShort())
        bb.put("data".toByteArray())
        bb.putInt(totalAudioLen)

        val out = ByteArrayOutputStream(44 + pcmData.size)
        out.write(header)
        out.write(pcmData)
        return out.toByteArray()
    }
}

/**
 * Pure Kotlin implementation of the Shazam audio fingerprint signature generator.
 */
object ShazamFingerprinter {
    private const val FFT_SIZE = 2048
    private const val STEP_SIZE = 128

    // Frequency bands (Hz): 250-520, 520-1450, 1450-3500, 3500-5500
    private val BAND_RANGES = arrayOf(
        Pair(32, 66),    // ~250Hz - 515Hz
        Pair(66, 185),   // ~515Hz - 1445Hz
        Pair(185, 448),  // ~1445Hz - 3500Hz
        Pair(448, 704),  // ~3500Hz - 5500Hz
    )

    fun createSignature(pcmData: ByteArray, sampleRate: Int): String? {
        val shortCount = pcmData.size / 2
        if (shortCount < FFT_SIZE) return null

        val samples = FloatArray(shortCount)
        val bb = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until shortCount) {
            samples[i] = bb.short.toFloat() / 32768.0f
        }

        // Hanning Window
        val window = FloatArray(FFT_SIZE) { i ->
            (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (FFT_SIZE - 1)))).toFloat()
        }

        val peakPoints = mutableListOf<Triple<Int, Int, Float>>() // frameIndex, binIndex, magnitude

        val frameCount = (shortCount - FFT_SIZE) / STEP_SIZE
        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)

        for (frame in 0 until frameCount) {
            val offset = frame * STEP_SIZE
            for (i in 0 until FFT_SIZE) {
                real[i] = samples[offset + i] * window[i]
                imag[i] = 0f
            }

            fft(real, imag)

            // Find peak in each frequency band
            for (band in BAND_RANGES) {
                var maxMag = 0f
                var maxBin = -1
                for (bin in band.first until band.second) {
                    val mag = real[bin] * real[bin] + imag[bin] * imag[bin]
                    if (mag > maxMag) {
                        maxMag = mag
                        maxBin = bin
                    }
                }
                if (maxBin != -1 && maxMag > 0.05f) {
                    peakPoints.add(Triple(frame, maxBin, maxMag))
                }
            }
        }

        if (peakPoints.isEmpty()) return null

        // Build Shazam binary signature
        val out = ByteArrayOutputStream()
        val writer = ByteBuffer.allocate(48 + peakPoints.size * 8).order(ByteOrder.LITTLE_ENDIAN)

        // Magic 0xcafe2800
        writer.putInt(0xcafe2800.toInt())
        writer.putInt(0)
        writer.putInt(0)
        writer.putInt(1) // version
        writer.putInt(3) // 16kHz
        writer.putInt(0)
        writer.putInt(0)
        writer.putInt(0)
        writer.putInt(0)
        writer.putInt(0)
        writer.putInt(0)
        writer.putInt(peakPoints.size)

        for (peak in peakPoints) {
            val encodedFreq = (peak.second and 0x3FF)
            writer.putShort(peak.first.toShort()) // frame index
            writer.putShort(encodedFreq.toShort()) // frequency bin
            writer.putInt((peak.third * 1000).toInt()) // amplitude
        }

        return Base64.encodeToString(writer.array(), Base64.NO_WRAP)
    }

    /** Cooley-Tukey Radix-2 In-Place FFT */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var l = 2
        while (l <= n) {
            val l2 = l shr 1
            val angle = (-2.0 * Math.PI / l).toFloat()
            val wStepR = Math.cos(angle.toDouble()).toFloat()
            val wStepI = Math.sin(angle.toDouble()).toFloat()
            var wR = 1.0f
            var wI = 0.0f
            for (m in 0 until l2) {
                var i = m
                while (i < n) {
                    val jIdx = i + l2
                    val tR = wR * real[jIdx] - wI * imag[jIdx]
                    val tI = wR * imag[jIdx] + wI * real[jIdx]
                    real[jIdx] = real[i] - tR
                    imag[jIdx] = imag[i] - tI
                    real[i] += tR
                    imag[i] += tI
                    i += l
                }
                val nextWR = wR * wStepR - wI * wStepI
                wI = wR * wStepI + wI * wStepR
                wR = nextWR
            }
            l = l shl 1
        }
    }
}
