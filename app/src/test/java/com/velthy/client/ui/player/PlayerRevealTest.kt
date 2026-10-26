package com.velthy.client.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRevealTest {

    @Test
    fun `player chrome runs from hidden to visible`() {
        assertEquals(0f, playerReveal(0f), 0.0001f)
        assertEquals(1f, playerReveal(1f), 0.0001f)
    }

    @Test
    fun `mini chrome is the exact complement of the player`() {
        for (step in 0..10) {
            val progress = step / 10f
            assertEquals(1f, playerReveal(progress) + miniReveal(progress), 0.0001f)
        }
    }

    @Test
    fun `both surfaces only move in one direction`() {
        var previousPlayer = 0f
        var previousMini = 1f
        for (step in 0..100) {
            val progress = step / 100f
            val player = playerReveal(progress)
            val mini = miniReveal(progress)
            assertTrue(player >= previousPlayer)
            assertTrue(mini <= previousMini)
            previousPlayer = player
            previousMini = mini
        }
    }

    @Test
    fun `artwork scales settle at their resting sizes`() {
        assertEquals(0.96f, playerArtScale(0f), 0.0001f)
        assertEquals(1f, playerArtScale(1f), 0.0001f)
        assertEquals(1f, miniArtScale(0f), 0.0001f)
        assertEquals(0.96f, miniArtScale(1f), 0.0001f)
    }
}
