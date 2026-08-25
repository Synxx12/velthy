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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velthy.client.data.settings.AppSettings
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
 * Apple Music / Cider-Style Dual-Island Floating Bottom Bar.
 * - Left/Center Island: Primary navigation capsule with fluid sliding pill indicator and horizontal swipe gestures
 * - Right Island: Dedicated Search circular floating button
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
    val container = MaterialTheme.colorScheme.surface
    val reduceDynamicBlur by AppSettings.reduceDynamicBlur.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val mainTabs = if (tabs.size > 1) tabs.dropLast(1) else tabs
    val searchTabIndex = if (tabs.size > 1) tabs.lastIndex else -1
    val searchTab = if (tabs.size > 1) tabs.last() else null

    val barHeight = 60.dp
    val capsuleShape = RoundedCornerShape(30.dp)
    val circleShape = CircleShape

    // ─── Gesture Swiping & Fluid Sliding Pill Engine ───
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)

    var rowSize by remember { mutableStateOf(IntSize.Zero) }
    val gapPx = with(density) { 6.dp.toPx() }
    val n = mainTabs.size

    val tabWidthPx = if (rowSize.width > 0 && n > 0) {
        (rowSize.width - gapPx * (n - 1)) / n
    } else 0f
    val tabStepPx = if (rowSize.width > 0 && n > 0) {
        (rowSize.width + gapPx) / n
    } else 0f

    val isMainTabActive = selectedIndex in 0 until n
    val activeMainIndex = if (isMainTabActive) selectedIndex else 0

    val pillTargetPx = if (tabStepPx > 0f) {
        activeMainIndex * tabStepPx + dragOffset
    } else 0f

    val animatedPillOffset by animateFloatAsState(
        targetValue = pillTargetPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "pillOffset",
    )

    val pillAlpha by animateFloatAsState(
        targetValue = if (isMainTabActive) 1f else 0f,
        animationSpec = tween(220),
        label = "pillAlpha",
    )

    var lastHapticTab by remember { mutableIntStateOf(selectedIndex) }

    LaunchedEffect(selectedIndex) {
        dragOffset = 0f
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { /* Absorb any taps in gutters/gaps so they don't click items behind */ }
            }
            .navigationBarsPadding()
            .padding(horizontal = PAGE_GUTTER)
            .padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ─── 1. Main Navigation Capsule (Left Island with Swipe & Sliding Pill) ───
        Box(
            modifier = Modifier
                .weight(1f)
                .height(barHeight)
                .clip(capsuleShape)
                .then(
                    if (reduceDynamicBlur) {
                        Modifier.background(container)
                    } else {
                        Modifier.hazeEffect(
                            state = hazeState,
                            style = HazeMaterials.regular(container),
                        )
                    },
                )
                .border(0.5.dp, Color.White.copy(alpha = 0.12f), capsuleShape)
                .padding(horizontal = 6.dp, vertical = 5.dp),
        ) {
            // Fluid Sliding Pill Background Indicator
            if (tabWidthPx > 0f && pillAlpha > 0.01f) {
                Box(
                    modifier = Modifier
                        .width(with(density) { tabWidthPx.toDp() })
                        .height(with(density) { rowSize.height.toDp() })
                        .graphicsLayer {
                            translationX = animatedPillOffset
                            alpha = pillAlpha
                        }
                        .clip(capsuleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                )
            }

            // Interactive Tab Row with Horizontal Drag Detection
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { rowSize = it }
                    .pointerInput(Unit) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onDragCancel = { dragOffset = 0f },
                            onDragEnd = {
                                if (tabStepPx > 0f) {
                                    val ratio = totalDrag / tabStepPx
                                    val shift = when {
                                        ratio > 0.35f -> kotlin.math.max(1, ratio.roundToInt())
                                        ratio < -0.35f -> kotlin.math.min(-1, ratio.roundToInt())
                                        else -> 0
                                    }
                                    val currentBase = if (currentSelectedIndex in 0 until n) currentSelectedIndex else 0
                                    val newIndex = (currentBase + shift).coerceIn(0, mainTabs.lastIndex)
                                    if (newIndex != currentSelectedIndex) {
                                        onTabSelected(newIndex)
                                    }
                                }
                                dragOffset = 0f
                            },
                            onHorizontalDrag = { _, delta ->
                                totalDrag += delta
                                val currentBase = if (currentSelectedIndex in 0 until n) currentSelectedIndex else 0
                                val rawPx = when {
                                    totalDrag > 0 && currentBase == mainTabs.lastIndex -> totalDrag * 0.25f
                                    totalDrag < 0 && currentBase == 0 -> totalDrag * 0.25f
                                    else -> totalDrag
                                }
                                dragOffset = rawPx

                                val approxTab =
                                    (currentBase + dragOffset / tabStepPx)
                                        .coerceIn(0f, mainTabs.lastIndex.toFloat())
                                        .roundToInt()
                                tag@ if (approxTab != lastHapticTab) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTabSelected(index)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ─── 2. Dedicated Search Button (Right Island) ───
        if (searchTab != null && searchTabIndex >= 0) {
            val isSearchSelected = selectedIndex == searchTabIndex
            val searchScale by animateFloatAsState(
                targetValue = if (isSearchSelected) 1.08f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "searchTabScale",
            )
            val searchTint by animateColorAsState(
                targetValue = if (isSearchSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                animationSpec = tween(200),
                label = "searchTabTint",
            )

            Box(
                modifier = Modifier
                    .size(barHeight)
                    .clip(circleShape)
                    .then(
                        if (reduceDynamicBlur) {
                            Modifier.background(container)
                        } else {
                            Modifier.hazeEffect(
                                state = hazeState,
                                style = HazeMaterials.regular(container),
                            )
                        },
                    )
                    .border(0.5.dp, Color.White.copy(alpha = 0.12f), circleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTabSelected(searchTabIndex)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                // Subtle circular indicator when search is active
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSearchSelected) {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            } else {
                                Color.Transparent
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
        targetValue = if (selected) 1.08f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "tabScale",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "tabTint",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
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
