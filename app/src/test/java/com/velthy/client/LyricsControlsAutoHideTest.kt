package com.velthy.client

import com.velthy.client.ui.player.autoHideLyricsControls
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsControlsAutoHideTest {

    private fun autoHide(
        lyricsOpen: Boolean = true,
        queueOpen: Boolean = false,
        hasLyrics: Boolean = true,
        overlayOpen: Boolean = false,
        controlsHidden: Boolean = false,
        controlsTouching: Boolean = false,
    ) = autoHideLyricsControls(
        lyricsOpen = lyricsOpen,
        queueOpen = queueOpen,
        hasLyrics = hasLyrics,
        overlayOpen = overlayOpen,
        controlsHidden = controlsHidden,
        controlsTouching = controlsTouching,
    )

    @Test
    fun `idle controls hide once full lyrics are open with words`() {
        assertTrue(autoHide())
    }

    @Test
    fun `nothing happens while the lyrics panel is closed`() {
        assertFalse(autoHide(lyricsOpen = false))
    }

    @Test
    fun `the queue keeps the controls`() {
        assertFalse(autoHide(queueOpen = true))
    }

    @Test
    fun `a track without lyrics keeps the controls`() {
        assertFalse(autoHide(hasLyrics = false))
    }

    @Test
    fun `an open sheet keeps the controls`() {
        assertFalse(autoHide(overlayOpen = true))
    }

    @Test
    fun `controls already hidden are left alone`() {
        assertFalse(autoHide(controlsHidden = true))
    }

    @Test
    fun `a finger on the screen keeps the controls`() {
        assertFalse(autoHide(controlsTouching = true))
    }
}
