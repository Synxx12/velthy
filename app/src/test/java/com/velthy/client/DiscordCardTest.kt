package com.velthy.client

import com.velthy.client.data.discord.DiscordRPC
import com.velthy.client.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pieces of the Discord card that are pure functions.
 *
 * The presence payload itself is built in `updateSong` and needs a Context, so
 * what is pinned down here is the URL a card links to and the text templates —
 * both are what a listener actually reads on the card, and both regressed
 * silently in the past.
 */
class DiscordCardTest {

    private fun song(artist: String = "Radiohead", album: String? = "In Rainbows") =
        Song(
            videoId = "abc123",
            title = "Weird Fishes",
            artist = artist,
            thumbnailUrl = null,
            albumName = album,
        )

    @Test
    fun watch_url_points_at_the_video_id() {
        assertEquals(
            "https://music.youtube.com/watch?v=abc123",
            DiscordRPC.watchUrl(song()),
        )
    }

    @Test
    fun templates_substitute_every_supported_variable() {
        assertEquals(
            "Weird Fishes — Radiohead — In Rainbows",
            DiscordRPC.resolveVariables("{song_name} — {artist_name} — {album_name}", song()),
        )
    }

    /**
     * A local file, or a track YouTube never grouped, has no album. The
     * placeholder must collapse to nothing rather than leaving a stray
     * separator on the card.
     */
    @Test
    fun a_missing_album_substitutes_an_empty_string() {
        assertEquals(
            "Weird Fishes — Radiohead — ",
            DiscordRPC.resolveVariables("{song_name} — {artist_name} — {album_name}", song(album = null)),
        )
    }

    @Test
    fun unmatched_text_is_left_alone() {
        assertEquals("Just an artist name", DiscordRPC.resolveVariables("Just an artist name", song()))
    }
}
