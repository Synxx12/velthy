package com.velthy.client.data.lyrics

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Intelligent Multi-Source Lyrics Cascade Engine.
 *
 * Parallel competitive resolution across 7 global lyrics providers:
 *  1. [PaxSenix] — Apple Music TTML via PaxSenix proxy (per-word sync)
 *  2. [LyricsPlus] — YouLy+ backend (fine syllable timing)
 *  3. [BetterLyrics] — Apple Music TTML (per-syllable / per-word sync)
 *  4. [SimpMusicLyrics] — Direct YouTube videoId sync
 *  5. [KuGou] — KuGou global/Asian synchronized LRC database
 *  6. [LrcLib] — Key-less public synced LRC database
 *  7. [Musixmatch] — World's largest lyrics catalog
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
        order: List<LyricsSource> = LyricsSource.entries,
    ): Result? = coroutineScope {
        if (sources.isEmpty() || (title.isBlank() && videoId.isBlank())) return@coroutineScope null

        val cleaned = LyricsCleaner.clean(title, artist)
        val metaKey = "${cleaned.cleanTitle.lowercase()}|${cleaned.cleanArtist.lowercase()}"

        // 1. Check in-memory cache
        if (videoId.isNotBlank()) {
            cacheByVideoId[videoId]?.let { cached ->
                if (cached.source in sources) return@coroutineScope cached
            }
        }
        cacheByMetadata[metaKey]?.let { cached ->
            if (cached.source in sources) return@coroutineScope cached
        }

        val sequence = order.filter { it in sources } +
            LyricsSource.entries.filter { it in sources && it !in order }

        val racing: List<Pair<LyricsSource, Deferred<List<LyricLine>?>>> = sequence.map { source ->
            source to async(Dispatchers.IO) { fetch(source, videoId, title, artist, durationMs, album) }
        }

        try {
            var lineSynced: Result? = null
            for ((source, job) in racing) {
                val lines = runCatching { job.await() }.getOrNull() ?: continue
                if (lines.any { it.isWordSynced }) {
                    val winner = result(source, lines)
                    saveToCache(videoId, metaKey, winner)
                    return@coroutineScope winner
                }
                if (lineSynced == null) {
                    lineSynced = result(source, lines)
                }
            }
            if (lineSynced != null) {
                saveToCache(videoId, metaKey, lineSynced)
                return@coroutineScope lineSynced
            }
            null
        } finally {
            racing.forEach { it.second.cancel() }
        }
    }

    private suspend fun fetch(
        source: LyricsSource,
        videoId: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String?,
    ): List<LyricLine>? = when (source) {
        LyricsSource.PAXSENIX -> PaxSenix.lyrics(title, artist, durationMs, album)
        LyricsSource.LYRICS_PLUS -> LyricsPlus.lyrics(title, artist, durationMs, album)
        LyricsSource.BETTER_LYRICS -> BetterLyrics.lyrics(title, artist, durationMs, album)
        LyricsSource.SIMP_MUSIC -> if (videoId.isNotBlank()) SimpMusicLyrics.lyrics(videoId, durationMs) else null
        LyricsSource.KUGOU -> KuGou.lyrics(title, artist, durationMs, album)
        LyricsSource.LRCLIB -> LrcLib.lyrics(title, artist, durationMs)
        LyricsSource.MUSIXMATCH -> Musixmatch.lyrics(title, artist, durationMs)
    }

    private fun result(source: LyricsSource, lines: List<LyricLine>) =
        Result(source, lines.withBackgroundVocals())

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