package com.velthy.client.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velthy.client.data.settings.AppSettings
import com.velthy.client.data.settings.AudioQuality
import com.velthy.client.data.sources.SourceConfig
import com.velthy.client.data.sources.SourceHealth
import com.velthy.client.data.sources.SourceKind
import com.velthy.client.data.sources.SourceRegistry
import com.velthy.client.ui.haptics.Haptic
import com.velthy.client.ui.haptics.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the app is allowed to get audio from.
 *
 * The list leads, because it is what the screen is for: **Sources** is the order
 * they are tried, and only the sources the user added can be dragged, because a
 * built-in kind's rank is fixed and a gesture should not be able to argue with
 * it. The arrangement is written when the finger is lifted rather than per
 * frame — persisting and rebuilding every source's instance is not work to do
 * mid-gesture. **Lossless** closes the page as its footnote: a preference over
 * the connection's own quality ceiling, the master switch that can only take
 * lossless away, never grant it above the ceiling.
 *
 * Nothing here downloads code, and nothing on it can teach the app a new way to
 * behave after it has shipped — a module supplies audio, not instructions.
 */
@Composable
fun SourcesScreen(
    contentPadding: PaddingValues,
    /**
     * Asks the activity to open the editor for a source — or for a new one, when
     * handed a config the registry does not have yet.
     *
     * Raised rather than drawn here so its scrim covers the tab bar and the mini
     * player, the same way every other alert in the app is hosted.
     */
    onEditSource: (SourceConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configs by SourceRegistry.configs.collectAsStateWithLifecycle()
    val lossless by AppSettings.losslessAudio.collectAsStateWithLifecycle()
    val wifiQuality by AppSettings.audioQualityWifi.collectAsStateWithLifecycle()
    val cellularQuality by AppSettings.audioQualityCellular.collectAsStateWithLifecycle()
    val metered by AppSettings.meteredConnection.collectAsStateWithLifecycle()

    /** Last known reachability per source, filled in as the probes come back. */
    val health = remember { mutableStateMapOf<String, SourceHealth>() }

    // Every source that has a server to reach is probed, not just the built-in
    // module, so a custom index gets the same reachability line — which is the
    // only feedback that a URL just typed in was any good.
    val probeKey = configs.filter { it.kind.needsServer }.joinToString { "${it.id}@${it.baseUrl}" }
    LaunchedEffect(probeKey) {
        configs.filter { it.kind.needsServer && it.isComplete }.forEach { config ->
            val source = SourceRegistry.instance(config.id) ?: return@forEach
            health[config.id] = withContext(Dispatchers.IO) {
                runCatching { source.health() }
                    .getOrElse { SourceHealth.Unreachable(it.message ?: "Failed") }
            }
        }
    }

    /** The ceiling in force right now, and whether it is below lossless. */
    val connectionQuality = if (metered == true) cellularQuality else wifiQuality
    val losslessCapped = connectionQuality != AudioQuality.LOSSLESS
    // Asked of all enabled sources, not just the built-in module.
    val anyLosslessSource = configs.any { it.enabled && it.isComplete && it.kind.canServeLossless }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Text(
            text = "Sources",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        )

        // The list leads the page and the preference below is the footnote, which
        // is how the reference screen reads: what you came for first, the one
        // switch that governs it after.
        SettingsGroup(
            header = "Sources — tried in this order",
            footer = "The first source that has the track plays it; the rest are " +
                "stepped over rather than failing playback. With two or more custom " +
                "sources, drag them to set which is asked before the others.",
        ) {
            // The addons are split out from the rest because they are the only
            // rows whose order is the *user's*. Everything else ranks by kind,
            // which is fixed in [SourceKind] and not something a drag should be
            // able to argue with — a gesture that let JioSaavn be dragged above
            // an addon would be offering a choice the resolver does not
            // actually honour.
            //
            // They are still one continuously numbered list. The numbers say
            // what order the sources are tried in, and restarting the count
            // under a second heading would break the one thing this screen is
            // for.
            val addons = configs.filter { it.kind.isUserAdded }
            val fixed = configs.filterNot { it.kind.isUserAdded }.sortedBy { it.kind.rank }

            val row: @Composable (Int, SourceConfig, (@Composable () -> Unit)?) -> Unit =
                { position, config, handle ->
                    SourceRow(
                        position = position,
                        config = config,
                        health = health[config.id],
                        onClick = { onEditSource(config) },
                        onToggle = { SourceRegistry.setEnabled(config.id, it) },
                        handle = handle,
                    )
                }

            ReorderableAddons(
                addons = addons,
                onReorder = { SourceRegistry.reorderAddons(it.map(SourceConfig::id)) },
                row = row,
            )

            // No leading divider when addons came first: each of those wrappers
            // ends with one, which is what makes them all the same height for
            // the drag to measure against.
            fixed.forEachIndexed { index, config ->
                if (index > 0) RowDivider()
                SourceRow(
                    position = addons.size + index + 1,
                    config = config,
                    health = health[config.id],
                    // The built-in module has an address worth editing; JioSaavn
                    // and YouTube have nothing to point at.
                    onClick = if (config.kind.needsServer) ({ onEditSource(config) }) else null,
                    // YouTube gets no switch at all — see [SourceRegistry.setEnabled].
                    onToggle = if (config.kind == SourceKind.YOUTUBE) {
                        null
                    } else {
                        ({ SourceRegistry.setEnabled(config.id, it) })
                    },
                )
            }

            RowDivider()
            SettingsRow(
                icon = Icons.Rounded.Add,
                title = "Add custom module",
                subtitle = SourceKind.CUSTOM_MODULE.detail,
                onClick = { onEditSource(SourceConfig(kind = SourceKind.CUSTOM_MODULE)) },
            )
        }

        SettingsGroup(
            header = "Lossless audio",
            // Kept to a line each: a footnote here explains one switch, and the
            // only three states worth spelling out are the ones where the switch
            // on its own would read as doing nothing.
            footer = when {
                !anyLosslessSource -> "Nothing enabled above can serve lossless yet."
                losslessCapped -> "Overridden — this connection's ceiling sits below Lossless."
                else -> "Plays the file the source holds instead of a transcode. Uses much more data."
            },
        ) {
            SettingsRow(
                icon = Icons.Rounded.GraphicEq,
                title = "Prefer lossless",
                subtitle = "FLAC and ALAC straight from the source",
                badge = "Overridden".takeIf { lossless && losslessCapped },
                trailing = {
                    Switch(
                        checked = lossless,
                        onCheckedChange = AppSettings::setLosslessAudio,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = { AppSettings.setLosslessAudio(!lossless) },
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * The addon rows, draggable by their handles to set which is asked first.
 *
 * The gesture keeps only how far the finger has travelled and which slot it
 * started on, and both where the row is drawn and which slot it occupies are
 * derived from those two numbers, so they cannot drift apart however many swaps
 * happen along the way.
 *
 * It should read as one movement rather than a list of jumps. The row under the
 * finger tracks it exactly; when a swap is decided, the row it displaced
 * springs into the slot it vacated and the dragged row lifts a hair with a
 * shadow over it, and one haptic tick marks the crossing. All of that is drawn
 * from state read in the draw phase, so the gesture costs a repaint per frame
 * and never a recomposition of the list.
 *
 * The drag lives on the handle alone rather than on the whole row: the handle's
 * [detectDragGestures] consumes the vertical drag before the scroll container
 * sees it, while a drag anywhere on the row would make the page impossible to
 * scroll past. And the order is written back only when the gesture ends —
 * [SourceRegistry.reorderAddons] persists to encrypted prefs and rebuilds the
 * source instances, which is not work to do on every frame of a drag.
 *
 * No handle is drawn for a single addon. There is nothing to reorder, and a
 * grip that cannot move anything is a control that lies.
 */
@Composable
private fun ReorderableAddons(
    addons: List<SourceConfig>,
    onReorder: (List<SourceConfig>) -> Unit,
    row: @Composable (Int, SourceConfig, (@Composable () -> Unit)?) -> Unit,
) {
    if (addons.isEmpty()) return
    if (addons.size == 1) {
        row(1, addons.first(), null)
        // The same trailing rule the reorderable rows below emit, so the row
        // that follows this block is separated whichever branch drew it.
        RowDivider()
        return
    }

    val haptics = rememberHaptics()
    val cardShape = remember { RoundedCornerShape(12.dp) }

    var liveOrder by remember(addons) { mutableStateOf(addons) }
    var dragged by remember { mutableStateOf<String?>(null) }

    /** Distance the finger has covered since this gesture began, in pixels. */
    var totalDrag by remember { mutableFloatStateOf(0f) }

    /** Which slot of [liveOrder] it began on. */
    var startIndex by remember { mutableIntStateOf(0) }

    // The distance from one row's top to the next — the row plus the hairline
    // after it, measured off the wrapper holding both. Frozen for the duration
    // of a gesture so a relayout mid-drag cannot move the boundaries the drag
    // is being measured against underneath it.
    var pitchPx by remember { mutableFloatStateOf(0f) }
    var lockedPitchPx by remember { mutableFloatStateOf(0f) }

    liveOrder.forEachIndexed { index, config ->
        // Keyed on the config's id so this composable — gesture and all —
        // follows that addon from slot to slot. Matched by position instead,
        // the first swap would change the key under the finger, restart the
        // `pointerInput` coroutine mid-gesture, and stall the drag one swap
        // after it started.
        key(config.id) {
            val isDragging = config.id == dragged

            // The row under the finger is the only one with a position of its
            // own; when a swap hands its neighbour a new slot, that neighbour's
            // jump is played back as motion instead — the offset snaps to where
            // it was drawn a moment ago and springs to zero, so the list closes
            // up behind the drag rather than blinking into place. Read in the
            // draw phase, so a row on its way repaints without recomposing the
            // list around it.
            val slotShift = remember { Animatable(0f) }
            var settledIndex by remember { mutableIntStateOf(index) }
            LaunchedEffect(index) {
                val moved = index - settledIndex
                settledIndex = index
                if (moved != 0 && config.id != dragged) {
                    slotShift.snapTo(-moved * pitchPx)
                    slotShift.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 900f))
                }
            }

            // The lifted card, kept in step with the queue's own reorder so the
            // two gestures read alike: the row under the finger rises a hair and
            // carries a shadow, and nothing else on the screen moves.
            val lift by animateFloatAsState(
                targetValue = if (isDragging) 1.02f else 1f,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 1200f),
                label = "sourceRowLift",
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .onSizeChanged { pitchPx = it.height.toFloat() }
                    .graphicsLayer {
                        // Read in the draw phase, so a drag moves the row
                        // without recomposing the list at all. The row sits
                        // wherever the finger has carried it from where it was
                        // picked up, less whatever the swaps have already moved
                        // its slot — so a swap relocates the slot and shortens
                        // this by exactly as much, and the row does not budge.
                        translationY = if (isDragging) {
                            totalDrag -
                                (liveOrder.indexOfFirst { it.id == config.id } - startIndex) * lockedPitchPx
                        } else {
                            slotShift.value
                        }
                        scaleX = lift
                        scaleY = lift
                        shape = cardShape
                        clip = isDragging
                        shadowElevation = if (isDragging) 8.dp.toPx() else 0f
                    }
                    .background(
                        color = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        shape = cardShape,
                    ),
            ) {
                row(index + 1, config) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .size(20.dp)
                            // A constant key on purpose: the row is pinned by
                            // [key] above, so nothing about a reorder should
                            // restart this coroutine.
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragged = config.id
                                        totalDrag = 0f
                                        startIndex = liveOrder.indexOfFirst { it.id == config.id }
                                        lockedPitchPx = pitchPx
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        val pitch = lockedPitchPx
                                        if (pitch <= 0f) return@detectDragGestures
                                        var at = liveOrder.indexOfFirst { it.id == config.id }
                                        if (at < 0) return@detectDragGestures

                                        // Held past either end the row stops
                                        // there under the finger, rather than
                                        // running off the list and having to be
                                        // dragged all the way back.
                                        totalDrag = (totalDrag + delta.y).coerceIn(
                                            -startIndex * pitch,
                                            (liveOrder.lastIndex - startIndex) * pitch,
                                        )

                                        // A loop, not an `if`: one pointer event
                                        // can cover several rows when the finger
                                        // is quick, and settling one row per
                                        // event would leave the list trailing.
                                        var swaps = 0
                                        while (true) {
                                            val travelled = totalDrag / pitch
                                            val moved = (at - startIndex).toFloat()
                                            if (travelled > moved + SWAP_THRESHOLD && at < liveOrder.lastIndex) {
                                                liveOrder = liveOrder.toMutableList()
                                                    .apply { add(at + 1, removeAt(at)) }
                                                at++
                                                swaps++
                                            } else if (travelled < moved - SWAP_THRESHOLD && at > 0) {
                                                liveOrder = liveOrder.toMutableList()
                                                    .apply { add(at - 1, removeAt(at)) }
                                                at--
                                                swaps++
                                            } else {
                                                break
                                            }
                                        }
                                        // One tick per crossing, not per row
                                        // covered — a quick flick that clears
                                        // three rows is still one movement of
                                        // the finger, and should feel like one.
                                        if (swaps > 0) haptics.play(Haptic.Tick)
                                    },
                                    onDragEnd = {
                                        dragged = null
                                        totalDrag = 0f
                                        onReorder(liveOrder)
                                    },
                                    onDragCancel = {
                                        dragged = null
                                        totalDrag = 0f
                                        liveOrder = addons
                                    },
                                )
                            },
                    )
                }
                // After the row, not before it, so every wrapper is exactly one
                // row plus one hairline — the pitch the drag measures against.
                RowDivider()
            }
        }
    }
}

/**
 * How far past a neighbour the finger has to carry a row before the two trade
 * places, as a share of one row's pitch.
 *
 * Deliberately more than half: at exactly half, a row that has just swapped
 * lands precisely on the boundary of swapping back, so the shake in any real
 * finger flips it back and forth for as long as it is held near a crossing.
 */
private const val SWAP_THRESHOLD = 0.6f

@Composable
private fun SourceRow(
    position: Int,
    config: SourceConfig,
    health: SourceHealth?,
    onClick: (() -> Unit)?,
    /** Null for a source that cannot be switched off, which gets a label instead. */
    onToggle: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    /**
     * The drag grip, for a row whose position is the user's to set. Passed in
     * rather than drawn here because the gesture belongs to the list that knows
     * the other rows — see [ReorderableAddons] — and null for every row whose
     * rank is fixed by its kind.
     */
    handle: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .heightIn(min = 60.dp)
            .padding(horizontal = ROW_INSET, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$position",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .width(18.dp)
                .alpha(if (config.enabled) 1f else 0.4f),
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = when (config.kind) {
                SourceKind.CUSTOM_MODULE -> Icons.Rounded.Extension
                SourceKind.JIOSAAVN -> Icons.Rounded.GraphicEq
                SourceKind.MODULE -> Icons.Rounded.Extension
                SourceKind.YOUTUBE -> Icons.Rounded.PlayCircle
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .size(ICON_SIZE)
                .alpha(if (config.enabled) 1f else 0.4f),
        )
        Spacer(Modifier.width(ICON_GAP))
        Column(
            Modifier
                .weight(1f)
                .alpha(if (config.enabled) 1f else 0.4f),
        ) {
            Text(
                text = config.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = config.statusLine(health),
                style = MaterialTheme.typography.bodyMedium,
                color = when (health) {
                    // Only a rejection is coloured. A server that is merely
                    // down will be up again without anyone doing anything,
                    // and painting that red trains people to ignore the
                    // colour by the time it means something.
                    is SourceHealth.Rejected -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (onToggle == null) {
            Text(
                text = "Always on",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Switch(
                checked = config.enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
        // Outside the dimming above, and last in the row: the grip is the
        // surface that starts the drag, so it has to sit clear of the switch —
        // a press meant for it must never land on the switch instead.
        if (handle != null) {
            Spacer(Modifier.width(4.dp))
            handle()
        }
    }
}

/** The second line of a row: what this source is, or what is wrong with it. */
private fun SourceConfig.statusLine(health: SourceHealth?): String = when {
    !isComplete -> "Tap to finish setting up"
    health is SourceHealth.Ok -> listOfNotNull(
        health.detail,
        kind.labels.take(3).joinToString(" · "),
    ).joinToString(" — ")
    health is SourceHealth.Rejected -> health.reason
    health is SourceHealth.Unreachable -> "Can't reach it right now — ${health.reason}"
    kind.needsServer -> "Checking…"
    else -> kind.labels.take(3).joinToString(" · ")
}
