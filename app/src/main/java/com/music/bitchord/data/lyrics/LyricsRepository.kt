package com.music.bitchord.data.lyrics

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Intelligent Multi-Source Lyrics Cascade Engine.
 *
 * Parallel competitive resolution across 4 global lyrics providers:
 *  1. [BetterLyrics] — Apple Music TTML (per-syllable / per-word sync)
 *  2. [LyricsPlus] — YouLy+ backend (fine syllable timing)
 *  3. [SimpMusicLyrics] — Direct YouTube videoId sync
 *  4. [LrcLib] — Key-less public synced LRC database with multi-step search
 *
 * Features:
 *  - High-performance thread-safe in-memory cache to prevent re-fetching and "disappearing lyrics".
 *  - Word-synced priority: If any provider returns word-synced lyrics, it takes precedence.
 *  - Seamless fallback to line-synced lyrics when word-timing is unavailable.
 */
object LyricsRepository {

    data class Result(val source: LyricsSource, val lines: List<LyricLine>)

    private val cacheByVideoId = ConcurrentHashMap<String, Result>()
    private val cacheByMetadata = ConcurrentHashMap<String, Result>()

    suspend fun lyrics(
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String? = null,
        sources: Set<LyricsSource> = LyricsSource.entries.toSet(),
    ): Result? = coroutineScope {
        if (sources.isEmpty() || (title.isBlank() && videoId.isBlank())) return@coroutineScope null

        val cleaned = LyricsCleaner.clean(title, artist)
        val metaKey = "${cleaned.cleanTitle.lowercase()}|${cleaned.cleanArtist.lowercase()}"

        // 1. Check in-memory cache first (instant hit)
        if (videoId.isNotBlank()) {
            cacheByVideoId[videoId]?.let { cached ->
                if (cached.source in sources) return@coroutineScope cached
            }
        }
        cacheByMetadata[metaKey]?.let { cached ->
            if (cached.source in sources) return@coroutineScope cached
        }

        // 2. Launch all enabled providers concurrently in parallel
        val tasks = mutableListOf<Pair<LyricsSource, Deferred<List<LyricLine>?>>>()

        if (LyricsSource.BETTER_LYRICS in sources) {
            tasks += LyricsSource.BETTER_LYRICS to async(Dispatchers.IO) {
                runCatching { BetterLyrics.lyrics(title, artist, durationMs, album) }.getOrNull()
            }
        }

        if (LyricsSource.LYRICS_PLUS in sources) {
            tasks += LyricsSource.LYRICS_PLUS to async(Dispatchers.IO) {
                runCatching { LyricsPlus.lyrics(title, artist, durationMs, album) }.getOrNull()
            }
        }

        if (LyricsSource.SIMP_MUSIC in sources && videoId.isNotBlank()) {
            tasks += LyricsSource.SIMP_MUSIC to async(Dispatchers.IO) {
                runCatching { SimpMusicLyrics.lyrics(videoId, durationMs) }.getOrNull()
            }
        }

        if (LyricsSource.LRCLIB in sources) {
            tasks += LyricsSource.LRCLIB to async(Dispatchers.IO) {
                runCatching { LrcLib.lyrics(title, artist, durationMs) }.getOrNull()
            }
        }

        try {
            val results = tasks.map { (source, job) ->
                source to runCatching { job.await() }.getOrNull()
            }

            // Priority 1: Word-synced lyrics from highest priority provider
            for ((source, lines) in results) {
                if (lines != null && lines.any { it.isWordSynced }) {
                    val winner = Result(source, lines)
                    saveToCache(videoId, metaKey, winner)
                    return@coroutineScope winner
                }
            }

            // Priority 2: Line-synced lyrics in order of reliability
            val lineSyncedPriority = listOf(
                LyricsSource.BETTER_LYRICS,
                LyricsSource.LYRICS_PLUS,
                LyricsSource.SIMP_MUSIC,
                LyricsSource.LRCLIB,
            )

            for (pSource in lineSyncedPriority) {
                val candidate = results.firstOrNull { it.first == pSource && !it.second.isNullOrEmpty() }
                if (candidate?.second != null) {
                    val winner = Result(pSource, candidate.second!!)
                    saveToCache(videoId, metaKey, winner)
                    return@coroutineScope winner
                }
            }

            null
        } finally {
            tasks.forEach { it.second.cancel() }
        }
    }

    private fun saveToCache(videoId: String, metaKey: String, result: Result) {
        if (videoId.isNotBlank()) {
            cacheByVideoId[videoId] = result
        }
        if (metaKey.isNotBlank()) {
            cacheByMetadata[metaKey] = result
        }
    }

    fun clearCache() {
        cacheByVideoId.clear()
        cacheByMetadata.clear()
    }
}