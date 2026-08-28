package com.velthy.client.download

import android.util.Log
import com.velthy.client.data.lyrics.LyricsRepository
import com.velthy.client.data.lyrics.toLrc
import com.velthy.client.data.model.Song
import com.velthy.client.data.model.durationMillis
import com.velthy.client.data.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal object LyricsTag {

    private const val TAG = "VelthyLyricsTag"

    suspend fun forTrack(track: Song): String? {
        val sources = if (AppSettings.syncedLyrics.value) {
            AppSettings.lyricsSources.value
        } else {
            emptySet()
        }
        if (sources.isEmpty()) return null

        val durationMs = track.durationMillis()
        if (durationMs <= 0L) {
            Log.d(TAG, "no duration for ${track.videoId}; skipping lyrics")
            return null
        }

        val found = try {
            withTimeoutOrNull(LOOKUP_MS) {
                LyricsRepository.lyrics(
                    videoId = track.videoId,
                    title = track.title,
                    artist = track.artist,
                    durationMs = durationMs,
                    album = track.albumName,
                    sources = sources,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "no lyrics for ${track.videoId}: ${e.message}")
            return null
        } ?: return null

        if (found.lines.none { it.text.isNotBlank() }) return null

        val lrc = found.lines.toLrc()
        if (lrc.length > MAX_LRC_CHARS) {
            Log.w(TAG, "lyrics for ${track.videoId} are ${lrc.length} chars; not embedding")
            return null
        }
        Log.d(TAG, "embedding ${found.source.label} lyrics for ${track.videoId}")
        return lrc.takeIf { it.isNotBlank() }
    }

    private const val MAX_LRC_CHARS = 64_000
    private const val LOOKUP_MS = 15_000L
}
