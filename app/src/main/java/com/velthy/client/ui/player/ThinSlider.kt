package com.velthy.client.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Apple Music's scrubber: a hairline capsule with no thumb knob, which
 * thickens under your finger and settles back when you let go.
 * Features an Apple Music luminous loading shimmer during audio buffering.
 */
@Composable
fun ThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    /**
     * Sends a sheen travelling along the played portion for as long as it is
     * true. Reserved for a transition that genuinely mixed — see
     * [com.velthy.client.data.settings.AppSettings.smartMixInProgress].
     */
    mixing: Boolean = false,
    /**
     * Span of the track, as fractions of its duration, that the next Smart Fade
     * transition is planned to occupy. Drawn as a brighter stretch of the
     * unplayed bar so the mix is visible before it arrives.
     */
    transitionWindow: ClosedFloatingPointRange<Float>? = null,
    idleHeight: Dp = 6.dp,
    activeHeight: Dp = 11.dp,
    activeColor: Color = Color.White.copy(alpha = 0.92f),
    inactiveColor: Color = Color.White.copy(alpha = 0.26f),
    /** Halfway between the two track colours: visible against unplayed, invisible under played. */
    markerColor: Color = Color.White.copy(alpha = 0.5f),
    /**
     * True while audio stream is buffering/loading in ExoPlayer.
     */
    isLoading: Boolean = false,
) {
    var dragging by remember { mutableStateOf(false) }
    val height by animateDpAsState(
        targetValue = if (dragging) activeHeight else idleHeight,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "sliderHeight",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "thinSliderLoading")
    val loadingPhase by if (isLoading) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_250, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "loadingPhase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val streamPhase by if (isLoading) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1_350, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "streamPhase",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val loadingAlpha by animateFloatAsState(
        targetValue = if (isLoading && !dragging) 1f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "loadingAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            // Generous invisible touch target — the visible bar is ~6dp.
            .height(activeHeight + 22.dp)
            // One gesture loop for both taps and drags.
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    onValueChange((down.position.x / size.width).coerceIn(0f, 1f))

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        if (pointer.positionChanged()) {
                            onValueChange((pointer.position.x / size.width).coerceIn(0f, 1f))
                            pointer.consume()
                        }
                    }

                    dragging = false
                    onValueChangeFinished?.invoke()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = inactiveColor, cornerRadius = radius)

            // Between the two track colours, and drawn *under* the played fill:
            transitionWindow?.let { window ->
                val from = size.width * window.start.coerceIn(0f, 1f)
                val to = size.width * window.endInclusive.coerceIn(0f, 1f)
                if (to > from) {
                    drawRoundRect(
                        color = markerColor,
                        topLeft = Offset(from, 0f),
                        size = Size(to - from, size.height),
                        cornerRadius = radius,
                    )
                }
            }

            val filled = size.width * value.coerceIn(0f, 1f)

            // Smart Interactive Loading / Streaming Visualizer
            if (loadingAlpha > 0.005f) {
                if (filled <= size.height) {
                    // Initial track loading: Apple Music full-track gliding luminous capsule
                    val pillWidth = (size.width * 0.28f).coerceAtLeast(size.height * 2f)
                    val travelDistance = (size.width - pillWidth).coerceAtLeast(0f)
                    val startX = travelDistance * loadingPhase
                    drawRoundRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                activeColor.copy(alpha = 0.25f * loadingAlpha),
                                activeColor.copy(alpha = 0.95f * loadingAlpha),
                                activeColor.copy(alpha = 0.25f * loadingAlpha),
                            ),
                            startX = startX,
                            endX = startX + pillWidth,
                        ),
                        topLeft = Offset(startX, 0f),
                        size = Size(pillWidth, size.height),
                        cornerRadius = radius,
                    )
                } else {
                    // Mid-track buffering: Smart forward data-stream wave on the unplayed track ahead
                    val remainingWidth = (size.width - filled).coerceAtLeast(0f)
                    if (remainingWidth > size.height) {
                        val waveWidth = (remainingWidth * 0.5f).coerceAtLeast(size.height * 2f)
                        val travel = remainingWidth + waveWidth
                        val waveStart = filled - waveWidth + (travel * streamPhase)
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.55f * loadingAlpha),
                                    Color.Transparent,
                                ),
                                startX = waveStart,
                                endX = waveStart + waveWidth,
                            ),
                            topLeft = Offset(filled, 0f),
                            size = Size(remainingWidth, size.height),
                            cornerRadius = radius,
                        )
                    }
                }
            }

            // Normal filled progress bar (Solid, precise, and never jittering)
            if (filled > 0f) {
                drawRoundRect(
                    color = activeColor,
                    size = Size(filled.coerceAtLeast(size.height), size.height),
                    cornerRadius = radius,
                )
                // If loading mid-song, draw an active glowing halo at the playhead tip
                if (loadingAlpha > 0.005f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.9f * loadingAlpha),
                                Color.Transparent,
                            ),
                            center = Offset(filled, size.height / 2f),
                            radius = size.height * 1.5f,
                        ),
                        radius = size.height * 1.5f,
                        center = Offset(filled, size.height / 2f),
                    )
                }
            }
        }

        // Smart Mix Sheen
        AnimatedVisibility(
            visible = mixing,
            enter = fadeIn(tween(durationMillis = 420)),
            exit = fadeOut(tween(durationMillis = 520)),
        ) {
            MixSheen(height = height)
        }
    }
}

/**
 * A single soft highlight travelling the length of the bar, over and over,
 * while two tracks are being mixed.
 *
 * Drawn as a moving gradient rather than an opacity pulse because a pulse reads
 * as "loading" — the thing every shimmer in every app means — and this is the
 * opposite claim: not that the app is waiting, but that it is doing something.
 * Motion along the bar also points the same way the music is going.
 *
 * Sweeps the **whole** bar rather than the played portion, which the first
 * version did and which made it invisible twice over. A transition happens in
 * the opening seconds of the incoming track, so the played portion is then a
 * few percent of the width — a highlight travelling across that is a flicker at
 * the far left. And the played portion is already white at 0.92 alpha, so white
 * at 0.55 over it resolves to 0.96: the same hue, four percent brighter. The
 * unplayed track sits at 0.26, and that is where a white band actually reads.
 */
@Composable
private fun MixSheen(height: Dp) {
    val transition = rememberInfiniteTransition(label = "mixSheen")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // Long enough to read as a sweep rather than a flicker, and slow
            // enough not to compete with the music for attention.
            animation = tween(durationMillis = 1_700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "mixSheenPhase",
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val band = size.width * BAND_FRACTION
        // Travels from fully off the left edge to fully off the right, so the
        // highlight enters and leaves rather than materialising mid-bar.
        val centre = -band + (size.width + band * 2f) * phase
        drawRoundRect(
            brush = Brush.linearGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.6f),
                    1f to Color.Transparent,
                ),
                start = Offset(centre - band / 2f, 0f),
                end = Offset(centre + band / 2f, 0f),
            ),
            cornerRadius = CornerRadius(size.height / 2f),
        )
    }
}

/** Width of the travelling highlight, as a fraction of the whole bar. */
private const val BAND_FRACTION = 0.22f
