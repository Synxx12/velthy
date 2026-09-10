package com.velthy.client.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velthy.client.ui.haptics.Haptic
import com.velthy.client.ui.haptics.rememberHaptics
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.roundToInt

data class BottomTab(
    val label: String,
    val icon: ImageVector,
)

/**
 * The pill's two gradients, hoisted out of the draw lambda.
 *
 * They used to be built inline for every frame of every slide — five
 * `Color.copy` calls each, twice over, sixty times a second. Only the
 * gradient's start and end travel with the capsule, so its colours are
 * constants and there is nothing per-frame left to build.
 */
private val PILL_GLASS_COLORS = listOf(
    Color.White.copy(alpha = 0.16f),
    Color(0xFF8CE8FF).copy(alpha = 0.14f), // Soft prismatic cyan
    Color(0xFFE8B5FF).copy(alpha = 0.14f), // Soft prismatic violet
    Color(0xFFFFC085).copy(alpha = 0.12f), // Soft prismatic peach
    Color.White.copy(alpha = 0.10f),
)

private val PILL_SHEEN_COLORS = listOf(
    Color.White.copy(alpha = 0.38f),
    Color(0xFF8CE8FF).copy(alpha = 0.30f), // Prismatic cyan sheen
    Color(0xFFE8B5FF).copy(alpha = 0.30f), // Prismatic violet sheen
    Color(0xFFFFD59E).copy(alpha = 0.25f), // Prismatic gold sheen
    Color.White.copy(alpha = 0.35f),
)

/** The hairline the pill is ringed with. */
private val PILL_HAIRLINE = 0.75.dp

/**
 * The pill's slide, one spec for everything that moves on it.
 *
 * It used to be two: the position was on a `dampingRatio` of 0.78 and the width
 * on 0.80, so the capsule's left edge and its right edge settled at slightly
 * different moments. That lag between the two ends of one shape is what read as
 * wobble — the pill looked like it was still deciding where it wanted to be.
 * One spec means the whole capsule arrives together.
 *
 * The numbers themselves are a compromise between the two things that were
 * wrong with the old pair: `StiffnessMediumLow` (400) took long enough that a
 * tab change felt like waiting, and the 0.78 damping left it ringing after it
 * got there. 800 is roughly twice as quick and arrives in a little over a
 * third of a second; 0.85 keeps just enough overshoot to feel liquid rather
 * than motorised, and settles in one movement instead of two.
 */
private val PILL_SLIDE = spring<Float>(dampingRatio = 0.85f, stiffness = 800f)

/**
 * Apple Music / Cider-Style Dual-Island Floating Bottom Bar.
 * - Left Island: Primary navigation capsule (Home, New, Library)
 * - Right Island: Dedicated Search circular floating button
 * - Fluid Liquid Glass Sliding Pill: Emerges unclipped out of the 3-item capsule,
 *   glides across the gap, and docks directly into the Search button with spring physics.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun FloatingBottomBar(
    tabs: List<BottomTab>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val container = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
    val fallbackContainer = MaterialTheme.colorScheme.surface
    val canBlur = rememberCanBlur()
    val haptics = rememberHaptics()
    val density = LocalDensity.current

    val mainTabs = if (tabs.size > 1) tabs.dropLast(1) else tabs
    val searchTabIndex = if (tabs.size > 1) tabs.lastIndex else -1
    val searchTab = if (tabs.size > 1) tabs.last() else null

    val barHeight = 60.dp
    val capsuleShape = RoundedCornerShape(30.dp)
    val circleShape = CircleShape
    val islandGapDp = 8.dp
    val innerHorizontalPadDp = 6.dp
    val innerVerticalPadDp = 5.dp

    // ─── Gesture Swiping & Fluid Sliding Pill Coordinates Engine ───
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)

    var leftIslandSize by remember { mutableStateOf(IntSize.Zero) }
    val n = mainTabs.size

    val innerPadPx = with(density) { innerHorizontalPadDp.toPx() }
    val gapMainPx = with(density) { 6.dp.toPx() }
    val islandGapPx = with(density) { islandGapDp.toPx() }
    val barHeightPx = with(density) { barHeight.toPx() }

    val leftInnerWidthPx = (leftIslandSize.width - innerPadPx * 2).coerceAtLeast(0f)
    val tabWidthPx = if (leftInnerWidthPx > 0f && n > 0) {
        (leftInnerWidthPx - gapMainPx * (n - 1)) / n
    } else 0f
    val tabStepPx = if (leftInnerWidthPx > 0f && n > 0) {
        tabWidthPx + gapMainPx
    } else 0f

    val tab0X = innerPadPx
    val tab1X = tab0X + tabStepPx
    val tab2X = tab0X + 2 * tabStepPx

    val searchPillSizeDp = 48.dp
    val searchPillWidthPx = with(density) { searchPillSizeDp.toPx() }
    val searchPillXPx = if (leftIslandSize.width > 0) {
        leftIslandSize.width + islandGapPx + (barHeightPx - searchPillWidthPx) / 2f
    } else 0f

    val safeIndex = selectedIndex.coerceIn(0, tabs.lastIndex)

    val targetBaseXPx = when (safeIndex) {
        0 -> tab0X
        1 -> tab1X
        2 -> tab2X
        else -> searchPillXPx
    }

    val pillTargetXPx = if (leftIslandSize.width > 0) {
        val rawX = targetBaseXPx + dragOffset
        val minX = tab0X
        val maxX = searchPillXPx
        rawX.coerceIn(minX, maxX)
    } else 0f

    val isSearchOrNearSearch = safeIndex == 3 || (safeIndex == 2 && dragOffset > (searchPillXPx - tab2X) * 0.45f)

    val targetWidthPx = if (isSearchOrNearSearch && searchPillWidthPx > 0f) {
        searchPillWidthPx
    } else {
        tabWidthPx
    }

    // Deliberately State objects rather than unwrapped with `by`.
    //
    // Both of them are read inside the pill's draw lambda and nowhere else, so
    // a frame of the slide repaints the capsule instead of recomposing the bar.
    // Unwrapped with `by`, the same read happens in composition and every frame
    // of every tab change rebuilds the islands, the four tab items and the
    // search button around it.
    val animatedPillX = animateFloatAsState(
        targetValue = pillTargetXPx,
        animationSpec = PILL_SLIDE,
        label = "liquidPillX",
    )

    val animatedPillWidth = animateFloatAsState(
        targetValue = targetWidthPx,
        animationSpec = PILL_SLIDE,
        label = "liquidPillWidth",
    )

    // There is no corner animation any more, and nothing was lost by dropping
    // it: both of the values it interpolated between were already half the
    // pill's height — 25dp on a 50dp pill, 24dp on the 48dp search circle — so
    // it was animating a capsule to a capsule. Deriving the corner from the
    // height in the draw lambda gives the identical shape and removes a third
    // animation from the frame.

    var lastHapticTab by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) {
        dragOffset = 0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { /* Absorb empty space taps around navbar */ }
            }
            .navigationBarsPadding()
            .padding(horizontal = PAGE_GUTTER)
            .padding(bottom = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        // ─── 1. Dual-Island Background Layer (Left Capsule + Right Circle) ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
            horizontalArrangement = Arrangement.spacedBy(islandGapDp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Island Background Capsule (Home, New, Library)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(capsuleShape)
                    .then(
                        if (!canBlur) {
                            Modifier.background(fallbackContainer)
                        } else {
                            Modifier.hazeEffect(
                                state = hazeState,
                                style = HazeMaterials.thin(container),
                            )
                        },
                    )
                    .border(0.5.dp, Color.White.copy(alpha = 0.14f), capsuleShape)
                    .onSizeChanged { leftIslandSize = it },
            )

            // Right Island Background Circle (Search)
            if (searchTab != null && searchTabIndex >= 0) {
                Box(
                    modifier = Modifier
                        .size(barHeight)
                        .clip(circleShape)
                        .then(
                            if (!canBlur) {
                                Modifier.background(fallbackContainer)
                            } else {
                                Modifier.hazeEffect(
                                    state = hazeState,
                                    style = HazeMaterials.thin(container),
                                )
                            },
                        )
                        .border(0.5.dp, Color.White.copy(alpha = 0.14f), circleShape),
                )
            }
        }

        // ─── 2. Liquid glass pill, painted rather than laid out ───
        //
        // Everything about this capsule moves on every frame of a slide: where
        // it is, how wide it is, and (once) its corner. Driving that through
        // Modifier.width/clip/background/border meant a fresh measure pass and a
        // fresh render layer per frame, which is what made the bar feel heavy —
        // the whole subtree around it was re-placed to move one shape. Painting
        // it in a draw lambda costs a repaint and nothing else, the corner falls
        // out of the pill's own height, and both animation values are read here
        // rather than in composition.
        if (tabWidthPx > 0f && leftIslandSize.width > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .drawBehind {
                        val width = animatedPillWidth.value
                        if (width <= 0f) return@drawBehind
                        val left = animatedPillX.value
                        val top = innerVerticalPadDp.toPx()
                        val height = size.height - top * 2
                        if (height <= 0f) return@drawBehind

                        val topLeft = Offset(left, top)
                        val pillSize = Size(width, height)
                        // A capsule, always: the corner is half the pill's own
                        // height, which is exactly what the 25dp/24dp pair the
                        // animation used to interpolate between resolved to.
                        val corner = CornerRadius(height / 2f)
                        val end = Offset(left + width, top + height)

                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = PILL_GLASS_COLORS,
                                start = topLeft,
                                end = end,
                            ),
                            topLeft = topLeft,
                            size = pillSize,
                            cornerRadius = corner,
                        )
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                colors = PILL_SHEEN_COLORS,
                                start = topLeft,
                                end = end,
                            ),
                            topLeft = topLeft,
                            size = pillSize,
                            cornerRadius = corner,
                            style = Stroke(width = PILL_HAIRLINE.toPx()),
                        )
                    },
            )
        }

        // ─── 3. Interactive Content & Gesture Layer (Icons, Text, Drag Gestures) ───
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight),
            horizontalArrangement = Arrangement.spacedBy(islandGapDp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Island Content (Home, New, Library with Horizontal Drag)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = innerHorizontalPadDp, vertical = innerVerticalPadDp)
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragCancel = { dragOffset = 0f },
                            onDragEnd = {
                                if (tabStepPx > 0f) {
                                    val distToSearch = (searchPillXPx - tab2X).coerceAtLeast(tabStepPx)
                                    val ratio = if (currentSelectedIndex == 2 && totalDrag > 0) {
                                        totalDrag / distToSearch
                                    } else {
                                        totalDrag / tabStepPx
                                    }
                                    val shift = when {
                                        ratio > 0.32f -> kotlin.math.max(1, ratio.roundToInt())
                                        ratio < -0.32f -> kotlin.math.min(-1, ratio.roundToInt())
                                        else -> 0
                                    }
                                    val currentBase = if (currentSelectedIndex in 0 until n) currentSelectedIndex else 0
                                    val maxIndex = if (searchTabIndex >= 0) tabs.lastIndex else mainTabs.lastIndex
                                    val newIndex = (currentBase + shift).coerceIn(0, maxIndex)
                                    if (newIndex != currentSelectedIndex) {
                                        haptics.play(Haptic.Select)
                                        onTabSelected(newIndex)
                                    }
                                }
                                dragOffset = 0f
                            },
                            onHorizontalDrag = { _, delta ->
                                totalDrag += delta
                                val currentBase = if (currentSelectedIndex in 0 until n) currentSelectedIndex else 0
                                val maxRightPx = if (currentBase == 2 && searchTabIndex >= 0) {
                                    searchPillXPx - tab2X
                                } else {
                                    (mainTabs.lastIndex - currentBase) * tabStepPx
                                }

                                val rawPx = when {
                                    totalDrag > maxRightPx -> maxRightPx + (totalDrag - maxRightPx) * 0.22f
                                    totalDrag < 0 && currentBase == 0 -> totalDrag * 0.22f
                                    else -> totalDrag
                                }
                                dragOffset = rawPx

                                val approxTab = if (currentBase == 2 && dragOffset > (searchPillXPx - tab2X) * 0.5f) {
                                    3
                                } else {
                                    (currentBase + dragOffset / tabStepPx)
                                        .coerceIn(0f, mainTabs.lastIndex.toFloat())
                                        .roundToInt()
                                }
                                if (approxTab != lastHapticTab) {
                                    haptics.play(Haptic.Tick)
                                    lastHapticTab = approxTab
                                }
                            },
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                mainTabs.forEachIndexed { index, tab ->
                    AppleMusicTabItem(
                        tab = tab,
                        selected = index == selectedIndex,
                        onClick = {
                            haptics.play(Haptic.Select)
                            onTabSelected(index)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Right Island Content (Search Button with Left Drag to Library)
            if (searchTab != null && searchTabIndex >= 0) {
                val isSearchSelected = selectedIndex == searchTabIndex
                val searchScale by animateFloatAsState(
                    targetValue = if (isSearchSelected) 1.06f else 1f,
                    // The pill's own spec, so the glyph swells on the same beat
                    // the capsule docks under it rather than lagging behind.
                    animationSpec = PILL_SLIDE,
                    label = "searchTabScale",
                )
                val searchTint by animateColorAsState(
                    targetValue = if (isSearchSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    },
                    animationSpec = tween(180),
                    label = "searchTabTint",
                )

                Box(
                    modifier = Modifier
                        .size(barHeight)
                        .clip(circleShape)
                        .pointerInput(Unit) {
                            var searchDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { searchDrag = 0f },
                                onDragCancel = {
                                    dragOffset = 0f
                                    searchDrag = 0f
                                },
                                onDragEnd = {
                                    if (searchDrag < -25f) {
                                        haptics.play(Haptic.Select)
                                        onTabSelected(mainTabs.lastIndex)
                                    }
                                    dragOffset = 0f
                                    searchDrag = 0f
                                },
                                onHorizontalDrag = { _, delta ->
                                    searchDrag += delta
                                    dragOffset = searchDrag
                                },
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                haptics.play(Haptic.Select)
                                onTabSelected(searchTabIndex)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = searchTab.icon,
                        contentDescription = searchTab.label,
                        tint = searchTint,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer {
                                scaleX = searchScale
                                scaleY = searchScale
                            },
                    )
                }
            }
        }
    }
}

/**
 * Apple Music Tab item with icon + text label.
 */
@Composable
private fun AppleMusicTabItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        // Matches the pill, so the selected icon grows as the capsule arrives
        // instead of drifting up to size after it has already stopped.
        animationSpec = PILL_SLIDE,
        label = "tabScale",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
        },
        animationSpec = tween(180),
        label = "tabTint",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .padding(vertical = 4.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
