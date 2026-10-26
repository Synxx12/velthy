package com.velthy.client.ui.player

internal const val LYRICS_CONTROLS_IDLE_MS = 3_000L

internal fun autoHideLyricsControls(
    lyricsOpen: Boolean,
    queueOpen: Boolean,
    hasLyrics: Boolean,
    overlayOpen: Boolean,
    controlsHidden: Boolean,
    controlsTouching: Boolean,
): Boolean = lyricsOpen && !queueOpen && hasLyrics && !overlayOpen && !controlsHidden &&
    !controlsTouching
