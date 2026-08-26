package com.velthy.client.playback

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.velthy.client.data.model.NOTIFICATION_ART_PX
import com.velthy.client.data.model.Song
import com.velthy.client.data.model.artworkAt
import com.velthy.client.data.sources.SourceRegistry
import com.velthy.client.data.sources.TrackMatcher
import com.velthy.client.download.Downloads
import kotlinx.coroutines.delay
import java.io.File

/** Snapshot of playback state, driven by the MediaController. */
data class PlayerState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    /** True while ExoPlayer is buffering — including our own stream-URL resolution. */
    val isLoading: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = 0,
    /**
     * Whether the queue has somewhere to go either side of the current track.
     * Taken from the player rather than [queueIndex], so the wrap-around of
     * repeat-all is already accounted for.
     */
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)

/** Binds to [PlaybackService] for the lifetime of the composition. */
@Composable
fun rememberMediaController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }
    return controller
}

/** Routes the player-screen AutoPlay button through the playback service. */
fun MediaController.toggleAutoplay() {
    sendCustomCommand(
        SessionCommand(PlaybackService.ACTION_TOGGLE_AUTOPLAY, Bundle.EMPTY),
        Bundle.EMPTY,
    )
}

/** Mirrors the controller into Compose state, polling position while playing. */
@Composable
fun rememberPlayerState(controller: MediaController?): PlayerState {
    var state by remember { mutableStateOf(PlayerState()) }

    DisposableEffect(controller) {
        val player = controller ?: return@DisposableEffect onDispose {}

        fun sync(error: String? = null) {
            val item = player.currentMediaItem
            state = state.copy(
                song = item?.toSong(),
                isPlaying = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING),
                // Sync position here too, so seeking while paused or buffering
                // still moves the scrubber (the poll loop only runs on play).
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = player.duration.coerceAtLeast(0L),
                error = error,
                isLoading = player.playbackState == Player.STATE_BUFFERING,
                repeatMode = player.repeatMode,
                queue = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).toSong() },
                queueIndex = player.currentMediaItemIndex,
                hasPrevious = player.hasPreviousMediaItem(),
                hasNext = player.hasNextMediaItem(),
            )
        }

        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) = sync(state.error)
            override fun onPlayerErrorChanged(error: androidx.media3.common.PlaybackException?) {
                sync(error?.let { "Playback failed: ${it.errorCodeName}" })
            }
        }
        player.addListener(listener)
        sync()
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(controller, state.isPlaying) {
        while (controller != null && state.isPlaying) {
            state = state.copy(
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.coerceAtLeast(0L),
            )
            delay(500)
        }
    }
    return state
}

/**
 * The inverse of [toMediaItem], as far as a MediaItem can carry a [Song].
 *
 * It has to round-trip losslessly for everything [LastPlayed] stores, because
 * the queue it saves is read back out of the *player* — so a field dropped here
 * is a field that does not survive a restart, however carefully it is
 * persisted. That is what happened to [Song.durationText]: stored, restored,
 * and always null, because this function never carried it back off the item in
 * the first place.
 */
fun MediaItem.toSong() = Song(
    videoId = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    thumbnailUrl = mediaMetadata.artworkUri?.toString(),
    durationText = mediaMetadata.extras?.getString(EXTRA_DURATION),
    fromAutoplay = this.fromAutoplay,
    localUri = mediaMetadata.extras?.getString(EXTRA_LOCAL_URI),
    localPath = mediaMetadata.extras?.getString(EXTRA_LOCAL_PATH),
)

/** @see Song.fromAutoplay */
val MediaItem.fromAutoplay: Boolean
    get() = mediaMetadata.extras?.getBoolean(EXTRA_FROM_AUTOPLAY) == true

/**
 * Marks a queue entry as AutoPlay's rather than the user's. Carried on the
 * MediaItem so it survives the trip through the session — the queue belongs to
 * the player, and the UI only ever sees it back through a MediaController.
 */
private const val EXTRA_FROM_AUTOPLAY = "velthy.fromAutoplay"
private const val EXTRA_LOCAL_URI = "velthy.localUri"
private const val EXTRA_LOCAL_PATH = "velthy.localPath"
private const val EXTRA_DURATION = "velthy.durationText"

/**
 * Where AutoPlay's section of the queue begins, and so where a track queued by
 * hand belongs — above the mix, below everything the user picked.
 *
 * Read as "the first of AutoPlay's tracks still to come", which is what keeps
 * it below the playing track even when the mix itself is what's playing: the
 * tracks of it already behind you count as played, and the section starts
 * again below the needle. Tracks put in by hand there — "Play next" while the
 * mix runs — stay above it too, for the same reason.
 *
 * The queue panel draws its AutoPlay heading at this same index.
 */
fun autoplaySectionStart(fromAutoplay: List<Boolean>, currentIndex: Int): Int {
    val after = (currentIndex + 1).coerceIn(0, fromAutoplay.size)
    return (after until fromAutoplay.size).firstOrNull { fromAutoplay[it] }
        ?: fromAutoplay.size
}

fun MediaController.autoplaySectionStart(): Int = autoplaySectionStart(
    fromAutoplay = (0 until mediaItemCount).map { getMediaItemAt(it).fromAutoplay },
    currentIndex = currentMediaItemIndex,
)

/**
 * Takes back what AutoPlay queued and hasn't played yet — what switching
 * AutoPlay off means for a queue it has already been extending. Removed from
 * the bottom up so the indexes ahead of each removal still hold.
 */
fun MediaController.dropAutoplayTracks() {
    for (i in mediaItemCount - 1 downTo currentMediaItemIndex + 1) {
        if (getMediaItemAt(i).fromAutoplay) removeMediaItem(i)
    }
}

/**
 * Custom scheme; PlaybackService resolves the real stream URL at play time.
 *
 * A video-tagged [Song] is expected to already have been swapped for its
 * catalogue audio release by [com.velthy.client.data.YtMusicRepository.resolveAudio]
 * before this is called — the queue, history and the notification should
 * never see the video upload's id or title, only whatever the audio match
 * resolved to (or the video's own audio, as the deliberate fallback when no
 * match was found).
 */
/**
 * MP4-family containers (m4a/aac/amr/wma/...) store their header or trailing
 * metadata in a way that needs backward seeking to parse, which the
 * content:// route (ContentDataSource) doesn't reliably support — the same
 * bytes read fine as a plain file. Formats like flac/mp3/ogg/webm already
 * seek correctly through content:// and are left alone.
 */
private val DIRECT_FILE_URI_EXTENSIONS = setOf(
    "m4a", "m4b", "m4p", "mp4", "aac", "3ga", "3gp", "3gpp",
    "alac", "amr", "awb", "wma", "aif", "aiff", "ac3", "dts",
)

private fun resolvePlaybackUri(uriString: String, localPath: String?): String {
    if (localPath.isNullOrBlank() || !uriString.startsWith("content://")) return uriString
    val ext = localPath.substringAfterLast('.', "").lowercase()
    if (ext !in DIRECT_FILE_URI_EXTENSIONS) return uriString
    val file = java.io.File(localPath)
    if (file.exists() && file.canRead()) {
        return "file://$localPath"
    }
    return uriString
}

/**
 * The `&n=&a=&d=` tail every playback URI carries: what this track is, in the
 * terms [com.velthy.client.data.sources.TrackMatcher] compares recordings on.
 *
 * The runtime is the one of the three that can rule a candidate *out* on its
 * own, and it is only ever a hint here — a row that never carried a duration
 * simply omits it and the match is made on title and artist alone, as it was
 * before.
 */
private fun Song.matchQuery(): String = buildString {
    append("&n=").append(Uri.encode(title))
    append("&a=").append(Uri.encode(artist))
    TrackMatcher.secondsOf(durationText)?.let { append("&d=").append(it) }
}

fun Song.toMediaItem(): MediaItem {
    val sourceTrack = SourceRegistry.parseTrackKey(videoId)
    val offlineUri = localUri ?: Downloads.saved.value[videoId]
    val uriString = offlineUri ?: when {
        videoId.startsWith("content://") || videoId.startsWith("file://") -> videoId
        sourceTrack != null -> SourceRegistry.trackUri(sourceTrack.first, sourceTrack.second)
            .let { "$it${matchQuery()}" }
        else -> "velthy://watch?v=$videoId${matchQuery()}"
    }
    return MediaItem.Builder()
        .setMediaId(videoId)
        .setUri(resolvePlaybackUri(uriString, localPath))
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(artworkAt(NOTIFICATION_ART_PX)?.toUri())
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .apply {
                if (fromAutoplay || offlineUri != null || durationText != null) {
                    setExtras(
                        bundleOf(
                            EXTRA_FROM_AUTOPLAY to fromAutoplay,
                            EXTRA_LOCAL_URI to offlineUri,
                            EXTRA_LOCAL_PATH to localPath,
                            EXTRA_DURATION to durationText,
                        ),
                    )
                }
            }
            .build(),
    )
    .build()
}

fun MediaController.playSongs(songs: List<Song>, startIndex: Int) {
    if (songs.isEmpty()) return
    // A queue started while shuffle is on goes in shuffled rather than being
    // played out of order — see [QueueShuffle]. The track the user picked still
    // leads, so it ends up at the top instead of at [startIndex].
    val shuffled = QueueShuffle.enabled.value
    val queue = if (shuffled) QueueShuffle.startingOrder(songs, startIndex) else songs
    setMediaItems(queue.map { it.toMediaItem() }, if (shuffled) 0 else startIndex, 0L)
    prepare()
    play()
}
