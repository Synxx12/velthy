package com.music.bitchord.data

import android.util.Log
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * 📡 Live Stats Reporter
 *
 * Broadcasts an anonymous now-playing ping to Musique's live stats endpoint
 * (https://mp3.movique.site/api/stats/ping) when a track starts playing.
 *
 * 🛡️ Privacy Guarantees:
 * - 100% Anonymous: Only track title, artist, thumbnail URL, and songId are sent.
 * - Zero Personal Data: No IP address, Google account, device ID, or location is ever tracked.
 * - User Controlled: Can be enabled/disabled at any time via AppSettings.shareLiveStats.
 * - Fire-and-Forget: Completely non-blocking and backgrounded; failures or offline state
 *   never interrupt audio playback.
 */
object LiveStatsReporter {

    private const val TAG = "LiveStatsReporter"
    private const val PING_URL = "https://mp3.movique.site/api/stats/ping"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var lastReportedVideoId: String? = null
    private var lastReportedTimeMs: Long = 0L

    fun report(song: Song?) {
        if (song == null || song.videoId.isBlank()) return
        if (!AppSettings.shareLiveStats.value) return

        val now = System.currentTimeMillis()
        // Deduplicate rapid duplicate pings within 10 seconds for the same song
        if (song.videoId == lastReportedVideoId && (now - lastReportedTimeMs) < 10_000L) {
            return
        }
        lastReportedVideoId = song.videoId
        lastReportedTimeMs = now

        scope.launch {
            runCatching {
                val json = JSONObject().apply {
                    put("songId", song.videoId)
                    put("title", song.title.ifBlank { "Unknown" })
                    put("artist", song.artist.ifBlank { "Unknown Artist" })
                    put("thumbnail", song.thumbnailUrl.orEmpty())
                    put("edition", "native")
                }

                val request = Request.Builder()
                    .url(PING_URL)
                    .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .header("User-Agent", "MusiqueNativeAndroid/1.3")
                    .build()

                Http.client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        Log.d(TAG, "Live stats ping sent successfully for: ${song.title}")
                    } else {
                        Log.w(TAG, "Live stats ping returned HTTP ${response.code}")
                    }
                }
            }.onFailure { e ->
                Log.w(TAG, "Live stats ping failed (non-critical): ${e.message}")
            }
        }
    }
}
