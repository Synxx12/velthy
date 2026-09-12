package com.velthy.client.ui.components

import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect

/**
 * Keeps Haze's visual style while letting it sample at a lower resolution.
 *
 * Haze 1.x otherwise processes every effect at full resolution, even when a
 * large blur makes those extra source pixels invisible. A third rather than
 * [HazeInputScale.Auto]: Auto only steps the resolution down once the blur
 * radius is large enough for it to be confident, which leaves the mini player,
 * the two bars and the fade strips above and below the feed sampling at full
 * resolution on every frame of every scroll — the one moment in this app where
 * the cost is being paid continuously. A third is what the liquid glass
 * surfaces already sample at, and the blur is what hides the upscale, so the
 * pixels being given up could not be seen either way.
 *
 * [block] still runs against the effect scope, so a call site keeps its
 * progressive gradient, its noise factor and anything else it was setting.
 */
@OptIn(ExperimentalHazeApi::class)
fun Modifier.optimizedHazeEffect(
    state: HazeState,
    style: HazeStyle = HazeStyle.Unspecified,
    block: (HazeEffectScope.() -> Unit)? = null,
): Modifier = hazeEffect(state, style) {
    inputScale = HazeInputScale.Fixed(0.33f)
    block?.invoke(this)
}
