package com.velthy.client.playback

import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class NotificationControlsTest {

    @Test
    fun `repeat cycles off to all to one and back`() {
        assertEquals(
            Player.REPEAT_MODE_ALL,
            nextRepeatMode(Player.REPEAT_MODE_OFF),
        )
        assertEquals(
            Player.REPEAT_MODE_ONE,
            nextRepeatMode(Player.REPEAT_MODE_ALL),
        )
        assertEquals(
            Player.REPEAT_MODE_OFF,
            nextRepeatMode(Player.REPEAT_MODE_ONE),
        )
    }

    @Test
    fun `notification keeps repeat on the left and favorite on the right`() {
        assertEquals(
            listOf(NotificationControl.REPEAT, NotificationControl.FAVORITE),
            notificationControls(showFavorite = true),
        )
        assertEquals(
            listOf(NotificationControl.REPEAT),
            notificationControls(showFavorite = false),
        )
    }

    @Test
    fun `favorite is only offered when the service can actually rate`() {
        assertTrue(shouldShowNotificationFavorite(signedIn = true, isLocal = false))
        assertFalse(shouldShowNotificationFavorite(signedIn = false, isLocal = false))
        assertFalse(shouldShowNotificationFavorite(signedIn = true, isLocal = true))
    }

    @Test
    fun `repeat button follows the current mode`() {
        assertEquals(
            CommandButton.ICON_REPEAT_OFF,
            repeatButtonIcon(Player.REPEAT_MODE_OFF),
        )
        assertEquals(
            CommandButton.ICON_REPEAT_ALL,
            repeatButtonIcon(Player.REPEAT_MODE_ALL),
        )
        assertEquals(
            CommandButton.ICON_REPEAT_ONE,
            repeatButtonIcon(Player.REPEAT_MODE_ONE),
        )
        assertEquals("Repeat off", repeatButtonLabel(Player.REPEAT_MODE_OFF))
        assertEquals("Repeat all", repeatButtonLabel(Player.REPEAT_MODE_ALL))
        assertEquals("Repeat one", repeatButtonLabel(Player.REPEAT_MODE_ONE))
    }
}
