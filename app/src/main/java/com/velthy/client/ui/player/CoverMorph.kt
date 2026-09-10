package com.velthy.client.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
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
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import com.velthy.client.data.model.Song
import com.velthy.client.data.model.artworkAt
import kotlin.math.abs
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
fun coverChromeReveal(morph: Float): Float = smooth((morph - 0.20f) / 0.45f)

/**
 * 0 → 1 as the real sleeve/banner may be revealed. Held at 0 for most of the
 * morph so the moving cover is the only artwork on screen, then released over
 * the last few percent where it already overlaps the real artwork exactly.
 */
fun coverArtReveal(morph: Float): Float = smooth((morph - 0.955f) / 0.045f)

/**
 * 0 → 1 over the first sliver of the morph, by which point the travelling
 * cover has clearly left the mini player's artwork behind.
 *
 * The mini player fades its own cover on this curve rather than on
 * [coverArtReveal]: that one is written for the *player's* artwork, which must
 * stay hidden until the cover lands on it. For the mini player it is the other
 * way round — its cover is where the journey starts, so the moment the
 * travelling copy moves off it the original has to go, or two copies of the
 * same square are on screen at once.
 */
fun coverDeparture(morph: Float): Float = smooth(morph / 0.05f)

/**
 * 0 → 1 as the tab bar and mini player may be seen, the exact complement of
 * [coverChromeReveal].
 *
 * Complementary on purpose, and the reason the transition no longer has a
 * hole in it. These two are the only things standing between the page and the
 * cover while the player is in flight, and when each was given its own timing
 * there was a stretch in the middle where the player had already faded out and
 * the mini player had not yet faded in — which is exactly the frame the cover
 * was left floating over the app on its own. Summed, the pair covers the
 * page at every point of the morph: whatever the player gives up, the bars
 * underneath take over in the same frame.
 */
fun bottomChromeReveal(morph: Float): Float = 1f - coverChromeReveal(morph)

/**
 * 0 → 1 as the mini player's own contents settle into place.
 *
 * Runs behind [bottomChromeReveal] rather than with it: the pill arrives
 * first, and the title, artist and transport slide the last few dp up into it.
 * One surface fading up as a whole reads as a panel appearing; a surface that
 * is already there with its content still arriving reads as the bar being
 * *put together* — which is what makes it belong to the same gesture as the
 * cover coming down onto it.
 */
fun miniContentSettle(morph: Float): Float = smooth((0.40f - morph) / 0.25f)

/**
 * 0 → 1 as the title and artist may rise into their row, and the sweep the
 * transport block follows them up on.
 *
 * Staggered behind [coverChromeReveal] rather than sharing it, and staggered
 * against each other, because a player whose every part faded on one curve
 * reads as a single flat sheet appearing. Three runs of different lengths —
 * backdrop, credits, controls — is what makes the screen look like it is being
 * assembled as the cover comes to rest on it, and it costs nothing: each is
 * read in a draw layer, so a frame of it is a repaint rather than a rebuild.
 */
fun playerCreditsReveal(morph: Float): Float = smooth((morph - 0.34f) / 0.40f)

/**
 * 0 → 1 as the transport block may rise. Started before the credits and run
 * longer, so the controls are still settling when the title has landed.
 */
fun playerControlsReveal(morph: Float): Float = smooth((morph - 0.26f) / 0.46f)

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
 * The layer is laid out once, at its destination, and the whole journey is a
 * draw-phase transform ([graphicsLayer]) of scale and translation measured
 * from there. Animating bounds through layout instead would re-place the
 * subtree on every frame of the morph, which is exactly the kind of per-frame
 * work that shows up as a stutter on a mid-range phone.
 *
 * A shadow that swells in the middle of the flight and settles onto the
 * sleeve's own at the end is what keeps it reading as a piece of material
 * moving through the screen rather than a picture pasted over it.
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
    /**
     * Fired once the cover's own decode has landed. The host holds the flight
     * until then, so the animation never begins on a frame whose artwork isn't
     * there to be seen.
     */
    onArtReady: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (mini == null || target == null) return
    val from = mini
    val to = target.rect
    if (to.width <= 0f || to.height <= 0f || from.width <= 0f || from.height <= 0f) return

    // Everything below is fixed for the whole flight — only the morph, read
    // inside the layer block, moves. Kept out of composition on purpose: a
    // value read here would recompose (and re-lay-out) this subtree per frame.
    val baseOffsetX = (to.left - overlayOrigin.x).roundToInt()
    val baseOffsetY = (to.top - overlayOrigin.y).roundToInt()
    val baseWidth = to.width.roundToInt().coerceAtLeast(1)
    val baseHeight = to.height.roundToInt().coerceAtLeast(1)
    val startCorner = if (target.isHero) 0.dp else 8.dp
    val endCorner = if (target.isHero) 0.dp else 10.dp

    val context = LocalContext.current
    val url = song?.artworkAt(COVER_MORPH_PX)
    // The overlay's own copy of the cover is decoded at the player's size, not
    // the mini player's 160px one, so it shares nothing with the thumbnail
    // already on screen and has to be fetched and decoded on the way in. Until
    // it lands this layer paints nothing at all rather than its placeholder
    // colour: the mini player's own artwork is directly underneath it, pixel for
    // pixel, and a flat grey square dropped over it for the first frames is
    // both the wrong picture and the most visible thing on the screen.
    var artLoaded by remember(url) { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset { IntOffset(baseOffsetX, baseOffsetY) }
                .layout { measurable, _ ->
                    val placeable = measurable.measure(Constraints.fixed(baseWidth, baseHeight))
                    layout(baseWidth, baseHeight) { placeable.place(0, 0) }
                }
                .graphicsLayer {
                    val m = morph.value.coerceIn(0f, 1f)
                    alpha = (1f - coverArtReveal(m)) * if (artLoaded) 1f else 0f
                    transformOrigin = TransformOrigin(0f, 0f)
                    // Laid out at the destination, so the journey is a scale
                    // about the top-left plus however far that corner still has
                    // to travel.
                    val sx = lerpFloat(from.width, to.width, m) / to.width
                    val sy = lerpFloat(from.height, to.height, m) / to.height
                    scaleX = sx
                    scaleY = sy
                    translationX = (from.left - to.left) * (1f - m)
                    translationY = (from.top - to.top) * (1f - m)

                    // The corner is the one thing here that must not simply
                    // scale with the layer, and getting that wrong is what left
                    // the cover arriving at the mini player as a hard square.
                    //
                    // A shape set in this block is measured in the layer's own
                    // coordinates and only scaled down afterwards with
                    // everything else. The mini player's artwork is a 40dp tile
                    // with an 8dp corner — a fifth of its width — but the layer
                    // is a sleeve-sized box, so "8dp" was being read as 8dp of a
                    // >300dp square and landing under one dp once shrunk: sharp.
                    //
                    // Dividing by the scale converts the radius we want *seen*
                    // into the one this layer has to be told, so the cover comes
                    // home on the mini player's own corner instead of its own.
                    val shrink = ((sx + sy) / 2f).coerceAtLeast(0.0001f)
                    shape = RoundedCornerShape(lerp(startCorner, endCorner, m) / shrink)
                    clip = true

                    // Deliberately left to scale with the layer, unlike the
                    // corner: the mini player's artwork is flat and casts
                    // nothing, while the sleeve carries a real shadow, so an
                    // elevation that grows as the cover does is the honest
                    // reading. 0 at both ends, 1 mid-flight, so the cover lifts
                    // off the bar and sets back down instead of sliding flat.
                    val lift = 1f - abs(2f * m - 1f)
                    shadowElevation = (lerpFloat(3f, 14f, m) + 15f * lift).dp.toPx()
                }
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
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Success && !artLoaded) {
                            artLoaded = true
                            onArtReady?.invoke()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
