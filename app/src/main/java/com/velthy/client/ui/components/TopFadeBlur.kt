package com.velthy.client.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velthy.client.data.settings.AppSettings
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * The run the fade needs below the bar to get from full blur to none without
 * the eye finding where it got there.
 */
private val FADE_RUN = 88.dp

/** The bar's own height, above whatever inset it is sitting under. */
val TopBarContentHeight = 52.dp

/**
 * How far down the window the bar actually ends: the status bar inset it is
 * pinned under, plus its own height.
 */
@Composable
fun topBarHeight(): Dp =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TopBarContentHeight

/**
 * How much blur the fade reaches at its outer edge — short of all of it.
 */
private const val PEAK = 0.75f

/**
 * How dark the readability scrim starts, at the very top of the strip.
 */
private const val SCRIM_PEAK = 0.42f

/** Enough stops that the ramp does not band across a near-flat colour. */
private const val SCRIM_STOPS = 12

/**
 * The glass behind every top bar: full blur along the top edge, ramping to
 * nothing on the way down.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TopFadeBlur(
    hazeState: HazeState,
    pageColor: Color,
    modifier: Modifier = Modifier,
    scrimColor: Color = MaterialTheme.colorScheme.background,
) {
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    if (reduceDynamicBlur) return

    val height = topBarHeight() + FADE_RUN

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.ultraThin(pageColor),
            ) {
                progressive = HazeProgressive.verticalGradient(
                    easing = EaseOutCubic,
                    startIntensity = PEAK,
                    endIntensity = 0f,
                )
                noiseFactor = 0f
            },
    )

    val scrim = remember(scrimColor) {
        Brush.verticalGradient(
            colorStops = Array(SCRIM_STOPS) { i ->
                val t = i / (SCRIM_STOPS - 1f)
                t to scrimColor.copy(alpha = SCRIM_PEAK * (1f - EaseOutCubic.transform(t)))
            },
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(scrim),
    )
}

