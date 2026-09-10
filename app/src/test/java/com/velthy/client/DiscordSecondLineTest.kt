package com.velthy.client

import com.velthy.client.data.discord.DiscordRPC
import com.velthy.client.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Discord card's second line.
 *
 * Worth pinning down because it is the only place an album reaches the card,
 * and because both the live presence and the settings preview read this one
 * function — a regression here would show up as the two disagreeing.
 */
class DiscordSecondLineTest {

    private fun song(artist: String = "Radiohead", album: String? = "In Rainbows") =
        Song(
            videoId = "abc123",
            title = "Weird Fishes",
            artist = artist,
            thumbnailUrl = null,
            albumName = album,
        )

    @Test
    fun artist_mode_is_the_artist_alone() {
        assertEquals(
            "Radiohead",
            DiscordRPC.secondLine(song(), DiscordRPC.SECOND_LINE_ARTIST),
        )
    }

    @Test
    fun artist_album_mode_joins_the_two() {
        assertEquals(
            "Radiohead · In Rainbows",
            DiscordRPC.secondLine(song(), DiscordRPC.SECOND_LINE_ARTIST_ALBUM),
        )
    }

    @Test
    fun album_mode_is_the_album_alone() {
        assertEquals(
            "In Rainbows",
            DiscordRPC.secondLine(song(), DiscordRPC.SECOND_LINE_ALBUM),
        )
    }

    @Test
    fun album_artist_mode_reverses_the_pair() {
        assertEquals(
            "In Rainbows · Radiohead",
            DiscordRPC.secondLine(song(), DiscordRPC.SECOND_LINE_ALBUM_ARTIST),
        )
    }

    /**
     * A local file, or a track YouTube never grouped. Every album-bearing mode
     * has to fall back to the artist rather than leaving the line empty or
     * ending on a separator — the title owns the first line, so a blank second
     * one is a card with a gap under it.
     */
    @Test
    fun a_missing_album_falls_back_to_the_artist() {
        val noAlbum = song(album = null)
        assertEquals(
            "Radiohead",
            DiscordRPC.secondLine(noAlbum, DiscordRPC.SECOND_LINE_ARTIST_ALBUM),
        )
        assertEquals(
            "Radiohead",
            DiscordRPC.secondLine(noAlbum, DiscordRPC.SECOND_LINE_ALBUM),
        )
        assertEquals(
            "Radiohead",
            DiscordRPC.secondLine(noAlbum, DiscordRPC.SECOND_LINE_ALBUM_ARTIST),
        )
    }

    /** An empty or whitespace album is a missing album, not a blank field. */
    @Test
    fun a_blank_album_is_treated_as_missing() {
        assertEquals(
            "Radiohead",
            DiscordRPC.secondLine(song(album = ""), DiscordRPC.SECOND_LINE_ARTIST_ALBUM),
        )
        assertEquals(
            "Radiohead",
            DiscordRPC.secondLine(song(album = "   "), DiscordRPC.SECOND_LINE_ALBUM),
        )
    }

    /** A stored mode from a newer build must not produce a broken line. */
    @Test
    fun an_unknown_mode_reads_as_the_artist() {
        assertEquals(
            "Radiohead",
            DiscordRPC.secondLine(song(), "something-else"),
        )
    }
}
