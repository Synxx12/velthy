package com.velthy.client.data.discord

import android.content.Context
import com.velthy.client.R
import com.velthy.client.data.model.Song
import com.velthy.client.data.model.artworkAt
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage

/**
 * Publishes what's playing to Discord as a Rich Presence activity.
 *
 * This talks to Discord as a *user*, over the same gateway its own client
 * uses — there is no official API for a third-party app to set a user's
 * presence, so the token in [token] is the account's own bearer token and the
 * socket identifies itself as Discord Android (see [SuperProperties]). That is
 * the only way this feature can exist, and it is why the settings screen warns
 * about it before asking for a login.
 *
 * The presence Discord renders from one [updateSong] call:
 *
 * ```
 *   Listening to Velthy          <- activityName, or the app's own name
 *   ┌────┐  Song title             <- details
 *   │art │  Artist                 <- state
 *   └────┘  Album                  <- the artwork's text, see [showAlbum]
 *           ▁▁▁▁▁▁ 1:04 / 3:47     <- from the timestamps
 *   [ Listen on YouTube Music ]    <- button 1
 *   [ Visit Velthy           ]   <- button 2
 * ```
 *
 * The album is a line of its own, under the artist, rather than something
 * folded into the artist's line. Discord draws that third line from the
 * artwork's text, which is where an album has always belonged — and a card
 * told to say "Artist · Album" on the second line had no line left to put the
 * album on, so the two halves of the same fact were being spent on one row.
 */
class DiscordRPC(
    val context: Context,
    token: String,
) : KizzyRPC(
    token = token,
    os = "Android",
    browser = "Discord Android",
    device = android.os.Build.DEVICE,
    userAgent = SuperProperties.userAgent,
    superPropertiesBase64 = SuperProperties.superPropertiesBase64,
) {
    /**
     * Pushes [song] to Discord as the current activity.
     *
     * [currentPlaybackTimeMillis] and [durationMillis] are turned into a
     * start/end timestamp pair rather than a progress value, because Discord
     * counts the bar down on its own clock from those two instants. So a
     * presence set once stays correct for the rest of the track, and the only
     * reason to send another is that something about the track *changed* —
     * which is also why [playbackSpeed] has to be divided out of both: at 1.5x
     * the wall-clock time left is not the media time left, and a presence that
     * ignored it would finish its countdown while the song was still playing.
     */
    suspend fun updateSong(
        song: Song,
        currentPlaybackTimeMillis: Long,
        durationMillis: Long,
        playbackSpeed: Float = 1.0f,
        useDetails: Boolean = false,
        status: String = "online",
        button1Text: String = "",
        button1Visible: Boolean = true,
        button2Text: String = "",
        button2Visible: Boolean = true,
        activityType: String = "listening",
        activityName: String = "",
        /** Whether the album is drawn as its own line under the artist. */
        showAlbum: Boolean = true,
    ) = runCatching {
        val currentTime = System.currentTimeMillis()

        val adjustedPlaybackTime = if (playbackSpeed > 0f) (currentPlaybackTimeMillis / playbackSpeed).toLong() else currentPlaybackTimeMillis
        val calculatedStartTime = (currentTime - adjustedPlaybackTime).coerceAtLeast(0L)

        val songTitleWithRate = if (playbackSpeed != 1.0f) {
            "${song.title} [${String.format("%.2fx", playbackSpeed)}]"
        } else {
            song.title
        }

        val remainingDuration = if (durationMillis > 0) (durationMillis - currentPlaybackTimeMillis).coerceAtLeast(0L) else 0L
        val adjustedRemainingDuration = if (playbackSpeed > 0f) (remainingDuration / playbackSpeed).toLong() else remainingDuration
        val hasValidDuration = durationMillis > 0 && remainingDuration > 0

        val buttonsList = mutableListOf<Pair<String, String>>()
        if (button1Visible) {
            val resolvedText = resolveVariables(
                button1Text.ifEmpty { DEFAULT_BUTTON_1 },
                song,
            )
            buttonsList.add(resolvedText to watchUrl(song))
        }
        if (button2Visible) {
            val resolvedText = resolveVariables(
                button2Text.ifEmpty { DEFAULT_BUTTON_2 },
                song,
            )
            buttonsList.add(resolvedText to PROJECT_URL)
        }

        val type = when (activityType) {
            "playing" -> Type.PLAYING
            "watching" -> Type.WATCHING
            "competing" -> Type.COMPETING
            else -> Type.LISTENING
        }

        val name = activityName.ifEmpty { appName() }

        // Discord draws the large image in a square, contain-fit slot, so any
        // non-square source — the 4:3 YouTube frame in particular — comes back
        // letterboxed with black bars top and bottom. Prefer the track's own
        // square artwork (YouTube Music's size-hinted thumbnails are square)
        // and only fall back to a video frame when there is nothing else.
        val artworkUrl = when {
            song.videoId.isEmpty() || song.videoId.startsWith("local:") -> song.artworkAt(ART_PX)
            song.thumbnailUrl != null && song.thumbnailUrl.contains(SQUARE_SIZE_HINT) ->
                song.artworkAt(ART_PX)
            else -> "https://i.ytimg.com/vi/${song.videoId}/hqdefault.jpg"
        }

        setActivity(
            name = name,
            details = songTitleWithRate,
            // The artist, alone. The album is not folded in here: it has a line
            // of its own below, and this row saying both is exactly what left
            // the album with nowhere to be.
            state = song.artist,
            detailsUrl = watchUrl(song),
            largeImage = artworkUrl?.let { RpcImage.ExternalImage(it) },
            smallImage = null,
            // The album's own line, under the artist. Null rather than an empty
            // string when there is nothing to say — an empty one draws a blank
            // row, not none — and null when the listener has turned the line
            // off.
            largeText = song.albumName?.takeIf { showAlbum && it.isNotBlank() },
            smallText = null,
            buttons = if (buttonsList.isNotEmpty()) buttonsList else null,
            type = type,
            statusDisplayType = if (useDetails) StatusDisplayType.DETAILS else StatusDisplayType.STATE,
            since = currentTime,
            startTime = calculatedStartTime.takeIf { it > 0 } ?: currentTime,
            endTime = if (hasValidDuration) (currentTime + adjustedRemainingDuration) else null,
            applicationId = APPLICATION_ID,
            status = status,
        )
    }

    /**
     * The name Discord puts after "Listening to". Taken from the app's own
     * label so it tracks a rename, with the dev flavor's suffix dropped —
     * a side-by-side dev install should still look like Velthy to everyone
     * else on Discord.
     */
    private fun appName(): String =
        context.getString(R.string.app_name).removeSuffix(" Dev")

    companion object {
        /**
         * The Discord application this presence is attributed to.
         *
         * Two things need it: the endpoint that mirrors an arbitrary artwork
         * URL onto Discord's CDN (Discord will not render a `large_image` it
         * does not host), and the buttons, which it drops entirely from an
         * activity with no application id.
         *
         * It does *not* decide the name shown on the profile — that is
         * `name` in the activity payload, which [appName] fills in. Register
         * an application at https://discord.com/developers/applications and
         * paste its id here to have the artwork proxied and the buttons
         * attributed under your own app rather than the upstream project's.
         */
        private const val APPLICATION_ID = "1541308554173227080"

        const val PROJECT_URL = "https://velthy.my.id"

        const val DEFAULT_BUTTON_1 = "Listen on YouTube Music"
        const val DEFAULT_BUTTON_2 = "Visit Velthy"

        /** Discord draws the sleeve at roughly 96dp; 480px covers it on any density. */
        private const val ART_PX = 480

        /** YouTube Music's artwork URLs carry a `w<N>-h<N>` size hint and are square. */
        private val SQUARE_SIZE_HINT = Regex("""w\d+-h\d+""")

        fun watchUrl(song: Song): String =
            "https://music.youtube.com/watch?v=${song.videoId}"

        /**
         * Resolves template variables in text.
         * Supported: {song_name}, {artist_name}, {album_name}
         */
        fun resolveVariables(text: String, song: Song): String {
            return text
                .replace("{song_name}", song.title)
                .replace("{artist_name}", song.artist)
                .replace("{album_name}", song.albumName ?: "")
        }
    }
}