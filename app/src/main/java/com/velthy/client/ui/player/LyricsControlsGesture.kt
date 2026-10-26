package com.velthy.client.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * How far a finger may wander inside a lower-half tap and still be a tap, as a
 * multiple of the platform's own slop.
 *
 * The list underneath starts scrolling at exactly one slop, and it consumes the
 * gesture when it does - so a tap that shifted by a hair more than that was not
 * merely ignored, it was taken by the list and became a browse. Every shortfall
 * here is a tap somebody meant and did not get.
 */
private const val TAP_SLOP_FACTOR = 2.5f

/** How far a finger must travel down before the scroll counts as a reveal. */
private val REVEAL_DRAG_THRESHOLD = 32.dp

internal enum class LyricsControlsReveal {
    NONE,
    SWIPE,
    TAP,
}

/**
 * Decide what a finger is doing without waiting for the gesture to finish.
 *
 * A downward drag reveals as soon as it crosses the threshold, so the controls
 * do not have to wait for the finger to lift. That direction is deliberate:
 * dragging down means going back toward earlier lyrics, while dragging up is
 * the natural motion for reading ahead, which should leave the controls hidden.
 * A tap only counts once it is released inside the lower half, which is where
 * the old tap fallback lived.
 */
internal fun lyricsControlsReveal(
    downY: Float,
    currentY: Float,
    height: Float,
    tapSlopPx: Float,
    swipeThresholdPx: Float,
    released: Boolean,
    pointerCount: Int,
): LyricsControlsReveal {
    if (pointerCount > 1) return LyricsControlsReveal.NONE
    if (currentY - downY >= swipeThresholdPx) return LyricsControlsReveal.SWIPE
    if (!released) return LyricsControlsReveal.NONE
    val travel = abs(currentY - downY)
    return if (travel <= tapSlopPx && downY >= height / 2f) {
        LyricsControlsReveal.TAP
    } else {
        LyricsControlsReveal.NONE
    }
}

/**
 * Intercept a lower-half tap or a backward lyric scroll before a lyric row can
 * seek; leave forward scrolling and real drags to the list.
 *
 * Runs on [PointerEventPass.Initial] only to see the gesture before the list
 * does; it never consumes movement. Consuming even the first slop used to make
 * the list's own drag detector see a consumed change and abandon the gesture,
 * so a drag in fullscreen was left to the sheet underneath and felt stiff.
 * Movement is left entirely to the list; only the release of a tap is consumed,
 * which is what stops the lyric row underneath from seeking as well. A reveal
 * by drag does not consume anything and simply steps aside once it has fired.
 *
 * "Backward" means the finger moving down the screen: the list is being pulled
 * back toward earlier lines. Reading ahead moves the finger up, and that is
 * exactly the gesture that must not bring the transport back.
 *
 * The detector stays installed while the lyrics are open, because a backwards
 * drag that starts with the controls still visible must also be able to keep
 * them there. It only consumes anything when [controlsHidden]: a tap in the
 * visible state belongs to the lyric row underneath and must seek it as usual.
 * Movement is never consumed in either state, so the list keeps its own scroll.
 */
@Composable
internal fun Modifier.revealLyricsControlsOnGesture(
    controlsHidden: Boolean,
    onReveal: () -> Unit,
): Modifier {
    val currentOnReveal = rememberUpdatedState(onReveal)
    val currentControlsHidden = rememberUpdatedState(controlsHidden)
    return pointerInput(Unit) {
        val tapSlop = viewConfiguration.touchSlop * TAP_SLOP_FACTOR
        val revealThreshold = REVEAL_DRAG_THRESHOLD.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var sawMultiplePointers = false
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.size > 1) sawMultiplePointers = true
                when (
                    lyricsControlsReveal(
                        downY = down.position.y,
                        currentY = change.position.y,
                        height = size.height.toFloat(),
                        tapSlopPx = tapSlop,
                        swipeThresholdPx = revealThreshold,
                        released = !change.pressed,
                        pointerCount = if (sawMultiplePointers) 2 else 1,
                    )
                ) {
                    LyricsControlsReveal.SWIPE -> {
                        currentOnReveal.value()
                        break
                    }

                    LyricsControlsReveal.TAP -> {
                        if (currentControlsHidden.value) {
                            change.consume()
                            currentOnReveal.value()
                        }
                        break
                    }

                    LyricsControlsReveal.NONE -> Unit
                }
                if (!change.pressed) break
            } while (true)
        }
    }
}
