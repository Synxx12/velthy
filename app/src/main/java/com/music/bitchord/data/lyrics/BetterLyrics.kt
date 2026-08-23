package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Word-timed lyrics from BetterLyrics — Apple Music TTML.
 */
object BetterLyrics {

    private const val BASE = "https://lyrics-api.boidu.dev/getLyrics"

    suspend fun lyrics(
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
    ): List<LyricLine>? = withContext(Dispatchers.IO) {
        val cleaned = LyricsCleaner.clean(title, artist)

        // 1. Try clean metadata
        var result = fetch(cleaned.cleanTitle, cleaned.cleanArtist, durationMs, album)

        // 2. Try primary artist if different
        if (result == null && cleaned.primaryArtist != cleaned.cleanArtist) {
            result = fetch(cleaned.cleanTitle, cleaned.primaryArtist, durationMs, album)
        }

        // 3. Try raw metadata fallback
        if (result == null && cleaned.rawTitle != cleaned.cleanTitle) {
            result = fetch(cleaned.rawTitle, cleaned.rawArtist, durationMs, album)
        }

        result
    }

    private fun fetch(
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
    ): List<LyricLine>? {
        if (title.isBlank()) return null
        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("s", title)
            .addQueryParameter("a", artist)
            .apply {
                val seconds = durationMs / 1000
                if (seconds > 0) addQueryParameter("d", seconds.toString())
                if (!album.isNullOrBlank()) addQueryParameter("al", album)
            }
            .build()

        val body = lyricsGet(url.toString()) ?: return null
        val ttml = runCatching {
            (lyricsJson.parseToJsonElement(body) as? JsonObject)
                ?.get("ttml")?.jsonPrimitive?.contentOrNull
        }.getOrNull() ?: return null

        return TtmlLyrics.parse(ttml).takeIf { it.isNotEmpty() }
    }
}