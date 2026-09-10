package com.velthy.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.velthy.client.data.model.ROW_ART_PX
import com.velthy.client.data.model.Song
import com.velthy.client.data.model.artworkAt
import com.velthy.client.ui.components.thumbnailBorder
import com.velthy.client.ui.haptics.Haptic
import com.velthy.client.ui.haptics.rememberHaptics
import com.velthy.client.ui.player.coverDeparture
import com.velthy.client.ui.player.miniContentSettle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * The transport buttons' touch target. Material's default 48dp is what a bar
 * this slim is really made of, so it sets the height on its own.
 */
private val GLYPH_SLOT = 40.dp

/**
 * The play and skip glyphs themselves.
 */
private val GLYPH_SIZE = 32.dp

/** The spinner that stands in for the play glyph, kept in proportion to it. */
private val SPINNER_SIZE = 22.dp

/**
 * The gap between the two transport controls.
 */
private val TRANSPORT_GAP = 8.dp

/**
 * Vertical padding, which with the 40dp artwork sets the bar's height at 56dp
 * and so its pill radius at 28.
 */
private val ROW_PADDING_VERTICAL = 8.dp

/**
 * Horizontal padding, deliberately larger than the vertical.
 */
private val ROW_PADDING_HORIZONTAL = 12.dp

/**
 * The artwork's corner, on the 8dp every other thumbnail in the app carries.
 */
private val ART_CORNER = 8.dp

/** Frosted mini player that rides just above the floating tab bar. */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    isLoading: Boolean,
    hazeState: HazeState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    /**
     * Reports the artwork's bounds in window pixels, used as the origin of the
     * mini → full morph. Optional so a preview that has no player never pays
     * for the measurement.
     */
    onArtBounds: ((Rect) -> Unit)? = null,
    /**
     * The host's open/close progress (1 = the player is fully open). The bar
     * reads it only inside draw layers, so the morph costs it no recomposition:
     * its cover is handed to the travelling one, and its contents slide the
     * last few dp into place behind them. The bar's own fade belongs to the
     * host, which reveals it together with the tab bar underneath it.
     */
    morph: State<Float>? = null,
    /**
     * How far the bar has been dragged up, in pixels, while the swipe-to-open
     * gesture is in flight. Set by the host so the bar travels with the finger
     * instead of sitting still until it snaps open.
     */
    lift: Animatable<Float, AnimationVector1D>? = null,
    modifier: Modifier = Modifier,
) {
    val canBlur = rememberCanBlur()
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = modifier
            .padding(horizontal = PAGE_GUTTER)
            .graphicsLayer {
                // Read in the draw phase so a frame of the swipe costs the bar a
                // repaint rather than a recomposition.
                translationY = -(lift?.value ?: 0f)
            }
            .clip(shape)
            .then(
                if (!canBlur) {
                    Modifier.background(MaterialTheme.colorScheme.surface)
                } else {
                    Modifier.hazeEffect(state = hazeState, style = HazeMaterials.thin(MaterialTheme.colorScheme.surface))
                },
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), shape)
            .clickable(onClick = onExpand),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = ROW_PADDING_HORIZONTAL,
                    vertical = ROW_PADDING_VERTICAL,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = song.artworkAt(ROW_ART_PX),
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    // Hands its cover over to the travelling one the instant
                    // that copy has cleared it. Without this the bar keeps
                    // painting the same square the morph is flying away with,
                    // and the two diverge across the screen.
                    .graphicsLayer { alpha = 1f - coverDeparture(morph?.value ?: 0f) }
                    .clip(RoundedCornerShape(ART_CORNER))
                    .thumbnailBorder(RoundedCornerShape(ART_CORNER))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .onGloballyPositioned { coords ->
                        onArtBounds?.invoke(
                            Rect(
                                coords.positionInRoot().x,
                                coords.positionInRoot().y,
                                coords.positionInRoot().x + coords.size.width,
                                coords.positionInRoot().y + coords.size.height,
                            ),
                        )
                    },
            )
            Spacer(Modifier.width(10.dp))
            // Everything but the artwork, so the cover stays the fixed point
            // the stretch slides in behind. Weighted as one block, which is
            // where the credits column's own weight used to sit.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .miniContentStagger(morph),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isLoading) {
                    Box(Modifier.size(GLYPH_SLOT), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onBackground,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(SPINNER_SIZE),
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                            onPlayPause()
                        },
                        modifier = Modifier.size(GLYPH_SLOT),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(GLYPH_SIZE),
                        )
                    }
                }
                Spacer(Modifier.width(TRANSPORT_GAP))
                IconButton(
                    onClick = {
                        haptics.play(Haptic.SkipNext)
                        onNext()
                    },
                    modifier = Modifier.size(GLYPH_SLOT),
                ) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(GLYPH_SIZE),
                    )
                }
            }
        }
    }
}

/**
 * Slides the bar's contents up into their row as [miniContentSettle] runs.
 *
 * Read inside the layer block, never in composition: this moves on every frame
 * of the morph, and a value read in the body would recompose the title, the
 * artist and both transport buttons sixty times a second to move them a few dp.
 */
private fun Modifier.miniContentStagger(morph: State<Float>?): Modifier {
    if (morph == null) return this
    return this.graphicsLayer {
        translationY = (1f - miniContentSettle(morph.value)) * 10.dp.toPx()
    }
}

