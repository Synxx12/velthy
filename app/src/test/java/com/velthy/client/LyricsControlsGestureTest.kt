package com.velthy.client

import com.velthy.client.ui.player.LyricsControlsReveal
import com.velthy.client.ui.player.lyricsControlsReveal
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsControlsGestureTest {

    private fun reveal(
        downY: Float,
        currentY: Float,
        released: Boolean = false,
        pointerCount: Int = 1,
    ) = lyricsControlsReveal(
        downY = downY,
        currentY = currentY,
        height = 1000f,
        tapSlopPx = 24f,
        swipeThresholdPx = 32f,
        released = released,
        pointerCount = pointerCount,
    )

    @Test
    fun `dragging back toward earlier lyrics reveals before release`() {
        assertEquals(LyricsControlsReveal.SWIPE, reveal(downY = 760f, currentY = 800f))
    }

    @Test
    fun `reading ahead never reveals the transport`() {
        assertEquals(LyricsControlsReveal.NONE, reveal(downY = 800f, currentY = 740f))
        assertEquals(
            LyricsControlsReveal.NONE,
            reveal(downY = 800f, currentY = 740f, released = true),
        )
    }

    @Test
    fun `small backward movement waits for more`() {
        assertEquals(LyricsControlsReveal.NONE, reveal(downY = 800f, currentY = 816f))
    }

    @Test
    fun `short tap on the lower half reveals on release`() {
        assertEquals(
            LyricsControlsReveal.TAP,
            reveal(downY = 800f, currentY = 804f, released = true),
        )
    }

    @Test
    fun `tap on the upper half keeps reading undisturbed`() {
        assertEquals(
            LyricsControlsReveal.NONE,
            reveal(downY = 200f, currentY = 200f, released = true),
        )
    }

    @Test
    fun `long forward drag is neither a tap nor a reveal`() {
        assertEquals(
            LyricsControlsReveal.NONE,
            reveal(downY = 500f, currentY = 400f, released = true),
        )
    }

    @Test
    fun `multi touch never reveals`() {
        assertEquals(
            LyricsControlsReveal.NONE,
            reveal(downY = 760f, currentY = 800f, pointerCount = 2),
        )
    }
}
