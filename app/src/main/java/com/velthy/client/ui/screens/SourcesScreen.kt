package com.velthy.client.ui.screens

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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Where the app is allowed to get audio from.
 *
 * Two questions live here. **Lossless** is a preference over the connection's
 * own quality ceiling — the master switch that can only take lossless away,
 * never grant it above the ceiling. **Sources** is the list, and its order is
 * the order they are tried: only the sources the user added can be dragged,
 * because a built-in kind's rank is fixed and a gesture should not be able to
 * argue with it. The arrangement is written when the finger is lifted rather
 * than per frame — persisting and rebuilding every source's instance is not
 * work to do mid-gesture.
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

    /** The sources the user added — the only rows a drag may move. */
    val movable = configs.filter { it.kind.isUserAdded }

    /** The arrangement on screen while a drag is in progress; committed on release. */
    val reorder = remember { SourcesReorderState(movable) }
    reorder.onCommit = { SourceRegistry.reorderAddons(it) }
    LaunchedEffect(configs) { reorder.sync(configs.filter { it.kind.isUserAdded }) }

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

        SettingsGroup(
            header = "Lossless audio",
            // Said plainly because the alternative is a switch that appears to
            // do something and doesn't. YouTube publishes no lossless rendition
            // of anything, so on a stock install this preference is inert until
            // a source that can serve lossless is added below.
            footer = when {
                !anyLosslessSource ->
                    "Nothing enabled below can serve lossless yet. Add a module source below, " +
                        "and tracks it holds a lossless rendition of will play as the file itself " +
                        "rather than as a transcode."
                losslessCapped ->
                    "Currently overridden: the quality ceiling for this connection is set below " +
                        "Lossless, and that budget wins. Set On Wi-Fi (or On mobile data) to " +
                        "Lossless to hear the files."
                else ->
                    "Asks the source for the file it holds instead of a transcode of it. " +
                        "Costs considerably more data than High, and does nothing when it has no " +
                        "lossless rendition to give."
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

        SettingsGroup(
            header = "Sources — tried in this order",
            footer = "A source that doesn't have the track, or can't be reached, is " +
                "stepped over rather than failing playback — the next one down plays it " +
                "instead. The sources you add come first, and you can drag them to set " +
                "which is asked before the others.",
        ) {
            // One continuously numbered list: the numbers say what order the
            // sources are tried in, and restarting the count under a second
            // heading would break the one thing this screen is for. Only the
            // user's own rows carry a handle — a built-in kind's rank is fixed
            // in [SourceKind] and a drag could not change what is asked first.
            val fixed = configs.filterNot { it.kind.isUserAdded }.sortedBy { it.kind.ordinal }
            val draggable = reorder.order.size > 1

            reorder.order.forEachIndexed { index, config ->
                if (index > 0) RowDivider()
                // Keyed by config id, not by position: a row that keys on its
                // index is torn down and rebuilt the moment it is swapped, and
                // the drag gesture dies with it.
                key(config.id) {
                    val dragging = reorder.draggedId == config.id
                    SourceRow(
                        position = index + 1,
                        config = config,
                        health = health[config.id],
                        onClick = { onEditSource(config) },
                        onToggle = { SourceRegistry.setEnabled(config.id, it) },
                        draggable = draggable,
                        dragging = dragging,
                        onDragStart = { reorder.onDragStart(config.id) },
                        onDrag = reorder::onDrag,
                        onDragEnd = reorder::onDragEnd,
                        onHeight = { reorder.heights[config.id] = it },
                        modifier = Modifier
                            .zIndex(if (dragging) 1f else 0f)
                            .graphicsLayer { translationY = if (dragging) reorder.dragOffset else 0f },
                    )
                }
            }

            fixed.forEachIndexed { index, config ->
                if (index > 0 || reorder.order.isNotEmpty()) RowDivider()
                SourceRow(
                    position = reorder.order.size + index + 1,
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

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Drag-to-reorder for the user's own sources.
 *
 * The order lives here rather than in [SourceRegistry] while a drag is in
 * progress — the registry is written once, on release, because persisting and
 * rebuilding every source's instance is not work to do per frame. Rows move one
 * slot at a time as the finger clears half of a neighbour, which is what makes
 * each swap unambiguous; [dragOffset] is corrected by that neighbour's height
 * every time, so the dragged row stays put under the finger while its slot
 * moves. Row heights come from layout rather than a constant, because a row
 * whose name wraps to two lines is taller and the threshold has to follow it.
 */
private class SourcesReorderState(initial: List<SourceConfig>) {
    val order = mutableStateListOf<SourceConfig>().apply { addAll(initial) }

    /** Row heights by config id, measured from layout. */
    val heights = mutableStateMapOf<String, Int>()

    var draggedId by mutableStateOf<String?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    /** Writes the final arrangement; called once the finger is lifted. */
    var onCommit: (List<String>) -> Unit = {}

    /** Adopts the registry's list whenever the user is not mid-drag. */
    fun sync(configs: List<SourceConfig>) {
        if (draggedId != null) return
        if (order.map { it.id } == configs.map { it.id }) return
        order.clear()
        order.addAll(configs)
    }

    fun onDragStart(id: String) {
        draggedId = id
        dragOffset = 0f
    }

    fun onDrag(deltaY: Float) {
        val id = draggedId ?: return
        dragOffset += deltaY
        var index = order.indexOfFirst { it.id == id }
        if (index < 0) return
        while (index < order.lastIndex) {
            val below = order[index + 1]
            val height = heights[below.id] ?: break
            if (dragOffset <= height / 2f) break
            dragOffset -= height
            order.add(index + 1, order.removeAt(index))
            index++
        }
        while (index > 0) {
            val above = order[index - 1]
            val height = heights[above.id] ?: break
            if (dragOffset >= -height / 2f) break
            dragOffset += height
            order.add(index - 1, order.removeAt(index))
            index--
        }
    }

    fun onDragEnd() {
        if (draggedId == null) return
        draggedId = null
        dragOffset = 0f
        onCommit(order.map { it.id })
    }
}

@Composable
private fun SourceRow(
    position: Int,
    config: SourceConfig,
    health: SourceHealth?,
    onClick: (() -> Unit)?,
    /** Null for a source that cannot be switched off, which gets a label instead. */
    onToggle: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    /** Only the user's own sources carry a handle — see [SourcesScreen]. */
    draggable: Boolean = false,
    dragging: Boolean = false,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onHeight: (Int) -> Unit = {},
) {
    Row(
        modifier = modifier
            .onSizeChanged { onHeight(it.height) }
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .heightIn(min = 60.dp)
            .padding(horizontal = ROW_INSET, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The handle is the only thing that starts a drag — the row itself has
        // to stay free for a tap and for the page to scroll under a finger.
        // With one source there is nothing to reorder, so it is not drawn.
        if (draggable) {
            Icon(
                imageVector = Icons.Rounded.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .alpha(if (config.enabled) 1f else 0.4f)
                    .pointerInput(config.id) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, amount ->
                                change.consume()
                                onDrag(amount.y)
                            },
                        )
                    },
            )
            Spacer(Modifier.width(4.dp))
        }
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
