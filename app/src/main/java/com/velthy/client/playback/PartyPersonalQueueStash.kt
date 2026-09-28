package com.velthy.client.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.Player
import com.velthy.client.data.model.PlaybackSourceType
import com.velthy.client.data.model.Song
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The listener's own queue, put aside for the length of a party and handed back
 * when they leave it.
 *
 * Joining a jam replaces whatever is playing with what the party is playing —
 * that is the point of it — and until this existed, that replacement was
 * permanent: the album, the playlist or the station somebody had queued up was
 * simply gone, and the only way back to it was to find the page again and start
 * over. So the queue is written down before the party takes the player over,
 * and put back when it gives the player back.
 *
 * Backed by [SharedPreferences] rather than held in memory, and that is the
 * whole reason it is a file on disk: the interesting case is not leaving a party
 * politely, it is the process being killed while in one. The next launch finds
 * the stash, restores it in place of the last-played queue, and the listener
 * never learns their music was ever at risk.
 */
object PartyPersonalQueueStash {

    /** A queue as it was left: what was playing, where, and whether it was moving. */
    data class Snapshot(
        val songs: List<Song>,
        val index: Int,
        val positionMs: Long,
        val wasPlaying: Boolean,
    )

    private lateinit var prefs: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Stash the queue [player] is holding, or the last-played one when it is
     * holding nothing.
     *
     * The empty-player case is not hypothetical: a party is often joined from a
     * cold start, where the service has restored a queue but never prepared it,
     * or straight after the queue ran out. [LastPlayed] is what the app would
     * have restored anyway, so it is what gets put aside.
     *
     * @return true when something was written. False means there was nothing
     *   worth keeping, which is not a failure — there is simply no personal
     *   queue to protect.
     */
    fun stashFromPlayer(player: Player): Boolean {
        if (!::prefs.isInitialized) return false
        if (player.mediaItemCount == 0) {
            val fallback = LastPlayed.load()
            if (fallback != null && fallback.songs.isNotEmpty()) {
                stash(
                    songs = fallback.songs,
                    index = fallback.index,
                    positionMs = fallback.positionMs,
                    wasPlaying = false,
                )
                return true
            }
            return false
        }
        val songs = (0 until player.mediaItemCount).mapNotNull { idx ->
            player.getMediaItemAt(idx).toSong().takeUnless { it.isDeviceFile() }
        }
        if (songs.isEmpty()) return false
        // Found in what is left rather than in the player's own list: device
        // files were dropped above, so every index past the first of them is off
        // by one, and using the player's would point the restore at the wrong row.
        val currentId = player.currentMediaItem?.toSong()?.videoId
        val safeIndex = songs.indexOfFirst { it.videoId == currentId }
            .takeIf { it >= 0 }
            ?: 0
        stash(
            songs = songs,
            index = safeIndex,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            wasPlaying = player.playWhenReady,
        )
        return true
    }

    fun stash(songs: List<Song>, index: Int, positionMs: Long, wasPlaying: Boolean) {
        if (!::prefs.isInitialized || songs.isEmpty()) return
        val safeIndex = index.coerceIn(songs.indices)
        val stored = StoredQueue(
            tracks = songs.map { StoredTrack.from(it) },
            index = safeIndex,
            positionMs = positionMs.coerceAtLeast(0L),
            wasPlaying = wasPlaying,
        )
        val encoded = runCatching {
            json.encodeToString(StoredQueue.serializer(), stored)
        }.getOrNull() ?: return
        // commit(), not apply(): the point of this write is to survive the
        // process going away, and an async one may not have reached disk when it
        // does.
        prefs.edit().putString(KEY_STASH, encoded).commit()
    }

    fun hasStash(): Boolean {
        if (!::prefs.isInitialized) return false
        return prefs.contains(KEY_STASH)
    }

    /** The stashed queue, or null when there is none or it cannot be read. */
    fun load(): Snapshot? {
        if (!::prefs.isInitialized) return null
        val raw = prefs.getString(KEY_STASH, null) ?: return null
        val stored = runCatching { json.decodeFromString<StoredQueue>(raw) }.getOrNull()
            ?: return null
        if (stored.tracks.isEmpty()) return null
        val songs = stored.tracks.map(StoredTrack::toSong)
        return Snapshot(
            songs = songs,
            index = stored.index.coerceIn(songs.indices),
            positionMs = stored.positionMs.coerceAtLeast(0L),
            wasPlaying = stored.wasPlaying,
        )
    }

    fun clear() {
        if (!::prefs.isInitialized) return
        prefs.edit().remove(KEY_STASH).commit()
    }

    @Serializable
    private data class StoredQueue(
        val tracks: List<StoredTrack>,
        val index: Int = 0,
        val positionMs: Long = 0L,
        val wasPlaying: Boolean = false,
    )

    /**
     * A [Song] in a form that survives being written down and read back.
     *
     * Every field the player and the queue rows read is here, because a field
     * left out is a field the restored queue has lost — and the loss is silent:
     * the song plays, it just plays with no album, no duration and no way back
     * to the page it came from.
     *
     * Enums are stored by name rather than by ordinal, so reordering one later
     * cannot silently change what a stashed queue means.
     */
    @Serializable
    private data class StoredTrack(
        val id: String,
        val title: String,
        val artist: String,
        val artwork: String? = null,
        val duration: String? = null,
        val album: String? = null,
        val albumId: String? = null,
        val artistId: String? = null,
        val video: Boolean = false,
        val auto: Boolean = false,
        val local: String? = null,
        val path: String? = null,
        val radio: String? = null,
        val source: String? = null,
        val sourceType: String? = null,
        val sourceId: String? = null,
        val setVideoId: String? = null,
        val quality: String? = null,
    ) {
        fun toSong(): Song = Song(
            videoId = id,
            title = title,
            artist = artist,
            thumbnailUrl = artwork,
            durationText = duration,
            albumName = album,
            albumId = albumId,
            artistId = artistId,
            isVideo = video,
            fromAutoplay = auto,
            localUri = local,
            localPath = path,
            radioName = radio,
            playbackSource = source,
            playbackSourceType = sourceType?.let {
                runCatching { PlaybackSourceType.valueOf(it) }.getOrNull()
            },
            playbackSourceId = sourceId,
            setVideoId = setVideoId,
            sourceQuality = quality,
        )

        companion object {
            fun from(song: Song) = StoredTrack(
                id = song.videoId,
                title = song.title,
                artist = song.artist,
                artwork = song.thumbnailUrl,
                duration = song.durationText,
                album = song.albumName,
                albumId = song.albumId,
                artistId = song.artistId,
                video = song.isVideo,
                auto = song.fromAutoplay,
                local = song.localUri,
                path = song.localPath,
                radio = song.radioName,
                source = song.playbackSource,
                sourceType = song.playbackSourceType?.name,
                sourceId = song.playbackSourceId,
                setVideoId = song.setVideoId,
                quality = song.sourceQuality,
            )
        }
    }

    /** A file on this device means nothing on anybody else's. See [stashFromPlayer]. */
    private fun Song.isDeviceFile(): Boolean =
        videoId.startsWith("content://") || videoId.startsWith("file://")

    private const val PREFS_NAME = "velthy_party_queue_stash"
    private const val KEY_STASH = "stashed_queue"
}
