package com.velthy.client.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.atomic.AtomicReference

/**
 * Syllable-timed lyrics from LyricsPlus.
 */
object LyricsPlus {

    private val MIRRORS = listOf(
        "https://lyricsplus.prjktla.my.id",
        "https://lyricsplus.atomix.one",
        "https://lyricsplus.binimum.org",
        "https://lyricsplus.prjktla.workers.dev",
        "https://lyricsplus-seven.vercel.app",
        "https://lyrics-plus-backend.vercel.app",
    )

    private val lastGood = AtomicReference<String?>(null)

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ): List<LyricLine>? = coroutineScope {
        val cleaned = LyricsCleaner.clean(title, artist)

        // 1. Try clean metadata
        var result = queryMirrors(cleaned.cleanTitle, cleaned.cleanArtist, durationMs, album)

        // 2. Try primary artist if different
        if (result == null && cleaned.primaryArtist != cleaned.cleanArtist) {
            result = queryMirrors(cleaned.cleanTitle, cleaned.primaryArtist, durationMs, album)
        }

        // 3. Try raw metadata fallback
        if (result == null && cleaned.rawTitle != cleaned.cleanTitle) {
            result = queryMirrors(cleaned.rawTitle, cleaned.rawArtist, durationMs, album)
        }

        result
    }

    private suspend fun queryMirrors(
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
    ): List<LyricLine>? = coroutineScope {
        val hosts = lastGood.get()
            ?.let { listOf(it) + MIRRORS.filterNot { mirror -> mirror == it } }
            ?: MIRRORS

        val pending = hosts.map { host ->
            host to async(Dispatchers.IO) { fetch(host, title, artist, durationMs, album) }
        }.toMutableList()

        try {
            while (pending.isNotEmpty()) {
                val (host, lines) = select {
                    pending.forEach { (host, job) -> job.onAwait { host to it } }
                }
                pending.removeAll { it.first == host }
                if (!lines.isNullOrEmpty()) {
                    lastGood.set(host)
                    return@coroutineScope lines
                }
            }
            null
        } finally {
            pending.forEach { it.second.cancel() }
        }
    }

    private suspend fun fetch(
        host: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val url = "$host/v2/lyrics/get".toHttpUrl().newBuilder()
            .addQueryParameter("title", title)
            .addQueryParameter("artist", artist)
            .apply {
                val seconds = durationMs / 1000
                if (seconds > 0) addQueryParameter("duration", seconds.toString())
                if (!album.isNullOrBlank()) addQueryParameter("album", album)
            }
            .build()

        val body = lyricsGet(url.toString()) ?: return@withContext null
        val response = runCatching { lyricsJson.decodeFromString<Response>(body) }.getOrNull()
            ?: return@withContext null
        parse(response).takeIf { it.isNotEmpty() }
    }

    internal fun parse(response: Response): List<LyricLine> =
        response.lyrics.orEmpty().mapNotNull { line ->
            val start = line.time ?: return@mapNotNull null
            val words = mergeSyllables(line.syllabus.orEmpty())
            when {
                words.isNotEmpty() -> LyricLine(
                    timeMs = minOf(start, words.first().startMs),
                    text = words.joinToString(" ") { it.text },
                    words = words,
                )
                // Some sources are only line-synced; still worth showing.
                // The line's duration is the only end it gets, and without it
                // an interlude can't be told from a slowly sung line.
                !line.text.isNullOrBlank() -> LyricLine(
                    timeMs = start,
                    text = line.text.trim(),
                    sungUntilMs = line.duration?.takeIf { it > 0 }?.let { start + it },
                )
                else -> null
            }
        }.sortedBy { it.timeMs }.withInstrumentalGaps()

    private fun mergeSyllables(syllables: List<Syllable>): List<LyricWord> {
        val words = mutableListOf<LyricWord>()
        val current = StringBuilder()
        var start = 0L
        var end = 0L

        syllables.forEach { syllable ->
            val text = syllable.text ?: return@forEach
            if (text.isBlank()) return@forEach
            val time = syllable.time ?: return@forEach
            if (current.isEmpty()) start = time
            current.append(text.trim())
            end = time + (syllable.duration ?: 0L)
            if (text.last().isWhitespace()) {
                words += LyricWord(start, end, current.toString())
                current.setLength(0)
            }
        }
        if (current.isNotEmpty()) words += LyricWord(start, end, current.toString())
        return words
    }

    @Serializable
    internal data class Response(
        val type: String? = null,
        val lyrics: List<Line>? = null,
    )

    @Serializable
    internal data class Line(
        val time: Long? = null,
        val duration: Long? = null,
        val text: String? = null,
        @SerialName("syllabus") val syllabus: List<Syllable>? = null,
    )

    @Serializable
    internal data class Syllable(
        val time: Long? = null,
        val duration: Long? = null,
        val text: String? = null,
    )
}