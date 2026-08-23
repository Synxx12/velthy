package com.music.bitchord.data.lyrics

import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlin.math.abs

/**
 * Lyrics from LRCLIB — a free, key-less, community lyrics database.
 *
 * Implements multi-layered retrieval:
 *  1. Exact lookup with clean track name, artist name, and duration.
 *  2. Search with separate track name and artist name fields.
 *  3. Fuzzy global search query (`q = "$title $artist"`) with closest duration matching.
 */
object LrcLib {

    private const val BASE = "https://lrclib.net/api"
    private const val AGENT = "BitChord (https://github.com/bitchord)"
    private const val DURATION_TOLERANCE_SECONDS = 12

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Synced lyrics for a track, or null when nothing usable is published. */
    suspend fun lyrics(title: String, artist: String, durationMs: Long): List<LyricLine>? =
        withContext(Dispatchers.IO) {
            val cleaned = LyricsCleaner.clean(title, artist)
            val seconds = if (durationMs > 0) (durationMs / 1000).toInt() else 0

            // 1. Exact match with clean metadata
            var synced = runCatching { exactMatch(cleaned.cleanTitle, cleaned.cleanArtist, seconds) }.getOrNull()

            // 2. Exact match with primary artist if multiple artists exist
            if (synced.isNullOrBlank() && cleaned.primaryArtist != cleaned.cleanArtist) {
                synced = runCatching { exactMatch(cleaned.cleanTitle, cleaned.primaryArtist, seconds) }.getOrNull()
            }

            // 3. Exact match with raw metadata if clean failed
            if (synced.isNullOrBlank() && cleaned.rawTitle != cleaned.cleanTitle) {
                synced = runCatching { exactMatch(cleaned.rawTitle, cleaned.rawArtist, seconds) }.getOrNull()
            }

            // 4. Best hit from field-based search
            if (synced.isNullOrBlank()) {
                synced = runCatching { searchHit(cleaned.cleanTitle, cleaned.cleanArtist, seconds) }.getOrNull()
            }

            // 5. Best hit from fuzzy query search ("title artist")
            if (synced.isNullOrBlank()) {
                synced = runCatching { queryHit("${cleaned.cleanTitle} ${cleaned.cleanArtist}", seconds) }.getOrNull()
            }

            // 6. Best hit from fuzzy query search ("title primaryArtist")
            if (synced.isNullOrBlank() && cleaned.primaryArtist != cleaned.cleanArtist) {
                synced = runCatching { queryHit("${cleaned.cleanTitle} ${cleaned.primaryArtist}", seconds) }.getOrNull()
            }

            synced?.let(::parseLrc)?.takeIf { it.isNotEmpty() }
        }

    private fun exactMatch(title: String, artist: String, seconds: Int): String? {
        if (title.isBlank() || artist.isBlank()) return null
        val builder = "$BASE/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
        if (seconds > 0) {
            builder.addQueryParameter("duration", seconds.toString())
        }
        val body = get(builder.build().toString()) ?: return null
        return (json.parseToJsonElement(body) as? JsonObject)
            ?.get("syncedLyrics")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }

    private fun searchHit(title: String, artist: String, seconds: Int): String? {
        if (title.isBlank()) return null
        val url = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .apply { if (artist.isNotBlank()) addQueryParameter("artist_name", artist) }
            .build()
        val body = get(url.toString()) ?: return null
        return selectBestSynced(body, seconds)
    }

    private fun queryHit(query: String, seconds: Int): String? {
        if (query.isBlank()) return null
        val url = "$BASE/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        val body = get(url.toString()) ?: return null
        return selectBestSynced(body, seconds)
    }

    private fun selectBestSynced(body: String, seconds: Int): String? {
        val hits = runCatching { json.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return null
        val candidates = hits.mapNotNull { it as? JsonObject }
            .filter { it["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true }

        if (candidates.isEmpty()) return null

        val filtered = if (seconds > 0) {
            candidates.filter {
                val d = it["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                abs(d - seconds) <= DURATION_TOLERANCE_SECONDS
            }.ifEmpty { candidates }
        } else {
            candidates
        }

        return filtered.minByOrNull {
            val d = it["duration"]?.jsonPrimitive?.doubleOrNull ?: 0.0
            if (seconds > 0) abs(d - seconds) else 0.0
        }?.get("syncedLyrics")?.jsonPrimitive?.contentOrNull
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", AGENT).build()
        return runCatching {
            Http.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null else response.body?.string()
            }
        }.getOrNull()
    }

    /**
     * `[mm:ss.xx] words`. Metadata tags carry no timestamp and fall out on
     * their own.
     */
    internal fun parseLrc(lrc: String): List<LyricLine> {
        val all = lrc.lineSequence().mapNotNull { line ->
            val match = STAMP.find(line) ?: return@mapNotNull null
            val (minutes, seconds, fraction) = match.destructured
            val fractionMs = when (fraction.length) {
                2 -> fraction.toLong() * 10
                3 -> fraction.toLong()
                else -> 0L
            }
            LyricLine(
                timeMs = minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs,
                text = line.substring(match.range.last + 1).trim(),
            )
        }.sortedBy { it.timeMs }.toList()

        val kept = all.filterIndexed { index, line ->
            if (!line.isGap) return@filterIndexed true
            val next = all.getOrNull(index + 1) ?: return@filterIndexed true
            next.timeMs - line.timeMs >= MIN_GAP_MS
        }

        val first = kept.firstOrNull() ?: return kept
        return if (!first.isGap && first.timeMs >= MIN_GAP_MS) {
            listOf(LyricLine(0L, "")) + kept
        } else {
            kept
        }
    }

    private const val MIN_GAP_MS = 4_000L
    private val STAMP = Regex("""\[(\d{1,2}):(\d{2})[.:](\d{2,3})]""")
}
