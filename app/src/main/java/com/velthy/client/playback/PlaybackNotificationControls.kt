@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.velthy.client.playback

import androidx.media3.common.Player
import androidx.media3.session.CommandButton

internal enum class NotificationControl {
    REPEAT,
    FAVORITE,
}

internal fun notificationControls(showFavorite: Boolean): List<NotificationControl> =
    if (showFavorite) {
        listOf(NotificationControl.REPEAT, NotificationControl.FAVORITE)
    } else {
        listOf(NotificationControl.REPEAT)
    }

internal fun shouldShowNotificationFavorite(signedIn: Boolean, isLocal: Boolean): Boolean =
    signedIn && !isLocal

internal fun nextRepeatMode(current: Int): Int = when (current) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}

internal fun repeatButtonIcon(repeatMode: Int): Int = when (repeatMode) {
    Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
    Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
    else -> CommandButton.ICON_REPEAT_OFF
}

internal fun repeatButtonLabel(repeatMode: Int): String = when (repeatMode) {
    Player.REPEAT_MODE_ONE -> "Repeat one"
    Player.REPEAT_MODE_ALL -> "Repeat all"
    else -> "Repeat off"
}
