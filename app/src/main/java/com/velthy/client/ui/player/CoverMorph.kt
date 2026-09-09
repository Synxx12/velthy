package com.velthy.client.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp as lerpFloat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.velthy.client.data.model.Song
import com.velthy.client.data.model.artworkAt
import kotlin.math.roundToInt

/**
 * Where the artwork the full player will finally show lives, described for the
 * open/close morph. [isHero] tells the morph which corner radius to end on —
 * a full-bleed banner has none, the square sleeve keeps its 10dp.
 */
data class CoverTarget(val isHero: Boolean, val rect: Rect)

/** Smoothstep used by every reveal curve so nothing ever starts or stops hard. */
private fun smooth(x: Float): Float {
    val v = x.coerceIn(0f, 1f)
    return v * v * (3f - 2f * v)
}

/**
 * 0 → 1 as the player's chrome (mesh backdrop, title, controls) may fade in.
 * Deliberately earlier than the artwork hand-off: the chrome settles behind
 * the still-travelling cover instead of popping in with it.
 */
fun coverChromeReveal(morph: Float): Float = smooth((morph - 0.25f) / 0.5f)

/**
 * 0 → 1 as the real sleeve/banner may be revealed. Held at 0 for most of the
 * morph so the moving cover is the only artwork on screen, then released over
 * the last few percent where it already overlaps the real artwork exactly.
 */
fun coverArtReveal(morph: Float): Float = smooth((morph - 0.955f) / 0.045f)

/**
 * Cover art decoded once, at the same generous size the player asks for, so
 * this layer shares a cache entry and a bitmap with the sleeve and banner.
 */
private const val COVER_MORPH_PX = 1200

/**
 * A single moving artwork layer for the mini → full morph.
 *
 * It draws one square of the current song's cover whose bounds travel from
 * the mini player's artwork to wherever the full player's own artwork will
 * be. The real sleeve/banner is held invisible underneath (see
 * [coverArtReveal]) and takes over exactly when this layer reaches it, so the
 * cover appears to grow out of the mini player and never "arrives" twice.
 *
 * [mini] and [target.rect] are in window coordinates; [overlayOrigin] is this
 * overlay's own window position so the drawing can be placed locally.
 */
@Composable
fun CoverMorphOverlay(
    song: Song?,
    mini: Rect?,
    target: CoverTarget?,
    morph: State<Float>,
    overlayOrigin: Offset,
    modifier: Modifier = Modifier,
) {
    val m = morph.value.coerceIn(0f, 1f)
    val alpha = 1f - coverArtReveal(m)
    if (m <= 0f || alpha <= 0.004f || mini == null || target == null) return

    val from = mini
    val to = target.rect
    val left = lerpFloat(from.left, to.left, m)
    val top = lerpFloat(from.top, to.top, m)
    val right = lerpFloat(from.right, to.right, m)
    val bottom = lerpFloat(from.bottom, to.bottom, m)

    // The mini artwork and the sleeve both carry rounded corners; the hero
    // banner is edge to edge. Fade the corner away (or back in) to match.
    val radius = lerp(if (target.isHero) 0.dp else 10.dp, 8.dp, 1f - m)

    val offsetX = (left - overlayOrigin.x).roundToInt()
    val offsetY = (top - overlayOrigin.y).roundToInt()
    val boxW = (right - left).roundToInt().coerceAtLeast(1)
    val boxH = (bottom - top).roundToInt().coerceAtLeast(1)

    val context = LocalContext.current
    val url = song?.artworkAt(COVER_MORPH_PX)

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(offsetX, offsetY) }
                .layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(boxW, boxH))
                    layout(boxW, boxH) { placeable.place(0, 0) }
                }
                .graphicsLayer { this.alpha = alpha }
                .clip(RoundedCornerShape(radius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (url != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .size(COVER_MORPH_PX)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
