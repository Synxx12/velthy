package com.velthy.client.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velthy.client.BuildConfig
import com.velthy.client.R
import com.velthy.client.data.settings.AppSettings
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Telegram-style frosted glass top bar.
 *
 * The content behind must be tagged with `Modifier.hazeSource(hazeState)`;
 * this bar then samples and blurs whatever scrolls beneath it in real time
 * (RenderEffect on API 31+, translucent scrim fallback below).
 *
 * Apple Music behaviour: the big in-list header owns the title at rest;
 * once the list scrolls, the small centered title + hairline divider fade in.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun FrostedTopBar(
    title: String,
    hazeState: HazeState,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    /**
     * Whether the bar carries its own pane of glass.
     *
     * False where something behind it is already providing one — [TopFadeBlur]
     * on a page whose artwork runs up under the status bar. Two panes over the
     * same content is one too many, and this bar's is the one with the hard
     * bottom edge.
     */
    ownBackdrop: Boolean = true,
    onBack: (() -> Unit)? = null,
    refreshing: Boolean = false,
    // A lambda, not a value: the drag changes every frame, and reading it in
    // the caller would recompose the whole app on each one.
    pullFraction: () -> Float = { 0f },
    searchBar: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val titleAlpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(220),
        label = "topBarTitleAlpha",
    )
    val dividerColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.outline.copy(alpha = if (scrolled && ownBackdrop) 0.4f else 0f),
        animationSpec = tween(220),
        label = "topBarDivider",
    )
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { /* Absorb any taps in top bar empty space so they never trigger items underneath */ }
            }
            .then(
                when {
                    !ownBackdrop -> Modifier
                    reduceDynamicBlur -> Modifier.background(MaterialTheme.colorScheme.surface)
                    else -> Modifier.hazeEffect(
                        state = hazeState,
                        style = HazeMaterials.ultraThin(MaterialTheme.colorScheme.surface),
                    )
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp),
        ) {
            if (searchBar != null) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = if (onBack != null) 4.dp else 16.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        searchBar()
                    }
                    actions()
                }
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 96.dp)
                        .fillMaxWidth()
                        .graphicsLayer { alpha = titleAlpha },
                )
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = "Velthy Logo",
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Velthy",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (BuildConfig.FLAVOR == "dev") {
                            Text(
                                text = "Dev",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    actions()
                }
            }
        }
        // The divider and the loader line share the bar's bottom edge; the box
        // only grows to the line's height while a refresh is actually showing.
        Box(Modifier.fillMaxWidth()) {
            HorizontalDivider(thickness = 0.5.dp, color = dividerColor)
            RefreshLine(
                refreshing = refreshing,
                pullFraction = pullFraction,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * The refresh indicator: a line along the bottom of the bar, directly under the
 * status bar. It tracks the drag on the way down — filling left to right as the
 * pull approaches the threshold — then sweeps indefinitely once the refresh is
 * away, so the two phases read as one continuous gesture.
 */
@Composable
private fun RefreshLine(refreshing: Boolean, pullFraction: () -> Float, modifier: Modifier = Modifier) {
    val fraction = pullFraction()
    val isPulling = fraction > 0.005f
    AnimatedVisibility(
        visible = refreshing || isPulling,
        enter = fadeIn(tween(120)),
        exit = fadeOut(tween(220)),
        modifier = modifier,
    ) {
        val lineModifier = Modifier
            .fillMaxWidth()
            .height(LINE_HEIGHT)
        if (refreshing) {
            LinearProgressIndicator(
                modifier = lineModifier,
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
            )
        } else {
            LinearProgressIndicator(
                progress = { pullFraction().coerceIn(0f, 1f) },
                modifier = lineModifier,
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

private val LINE_HEIGHT = 2.5.dp
