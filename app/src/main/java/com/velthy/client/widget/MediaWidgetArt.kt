package com.velthy.client.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.util.LruCache
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.velthy.client.data.model.CARD_ART_PX
import com.velthy.client.data.model.HEADER_ART_PX
import com.velthy.client.data.model.NOTIFICATION_ART_PX
import com.velthy.client.data.model.ROW_ART_PX
import com.velthy.client.data.model.artworkAt
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The single image a widget draws: the album cover, filling it edge to edge,
 * with its bottom dissolving into a blur for the transport to sit on.
 *
 * All of it is baked into one bitmap because a widget cannot blur anything at
 * runtime — [android.widget.RemoteViews] has no RenderEffect, no Haze, no
 * shaders, and no way to reach a view's render node. So the effect the app gets
 * live from
 * [BottomFadeBlur][com.velthy.client.ui.components.BottomFadeBlur] has to be
 * drawn here instead, once per track, on the CPU.
 *
 * The ramp is four progressively blurrier copies of the bottom of the cover,
 * drawn back over it softest-first, each masked by a vertical alpha gradient
 * starting lower than the last — which adds up to a blur that accelerates
 * downwards. Each copy is a separable box blur ([blurInPlace]) run on a
 * quarter-ish-scale working image and sampled back up.
 */
internal object MediaWidgetArt {

    /**
     * Draws the widget's artwork at exactly [widthPx] × [heightPx].
     *
     * [bandPx] is the height of the transport strip the layout will lay over the
     * result — the blur is sized from it, so the two stay locked together. See
     * `@dimen/widget_band_compact`.
     *
     * [key] identifies the track this is for, and is what the composite is
     * remembered under. Pass null only when there is nothing to remember (no
     * track at all), so the placeholder isn't cached under a shared name.
     */
    suspend fun render(
        context: Context,
        artworkUrl: String?,
        widthPx: Int,
        heightPx: Int,
        bandPx: Int,
        key: String?,
        cornerRadiusPx: Float,
    ): Bitmap {
        peek(key, widthPx, heightPx, bandPx)?.let { return it }
        val cacheKey = key?.let { cacheKey(it, widthPx, heightPx, bandPx) }

        val cover = loadArtwork(context, artworkUrl, maxOf(widthPx, heightPx))
        val composed = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composed)
        if (cover != null) canvas.fillCentreCropped(cover) else canvas.fillPlaceholder()

        // Taller than the band, so the ramp has room to start invisibly above
        // it — but never taller than the widget. On two cells the band is more
        // than half the height and this clamp binds, which is fine: the ramp's
        // first stop is a quarter of the way down and the artwork above it is
        // untouched.
        val blurRegion = (bandPx * BLUR_REGION_SCALE).coerceIn(1, heightPx)
        canvas.blurBottom(composed, blurRegion, bandPx)
        canvas.scrimBottom(bandPx)

        val rounded = composed.withRoundedCorners(cornerRadiusPx)
        composed.recycle()
        cacheKey?.let { composites.put(it, rounded) }
        return rounded
    }

    /**
     * The composite for these arguments if it has already been drawn, without
     * drawing it if it hasn't.
     */
    fun peek(key: String?, widthPx: Int, heightPx: Int, bandPx: Int): Bitmap? =
        key?.let { composites[cacheKey(it, widthPx, heightPx, bandPx)] }?.takeIf { !it.isRecycled }

    /** Drops every remembered composite — the last widget has just been removed. */
    fun clear() = composites.evictAll()

    private fun cacheKey(key: String, widthPx: Int, heightPx: Int, bandPx: Int) =
        "$key|$widthPx|$heightPx|$bandPx"

    // ---- artwork ----

    private suspend fun loadArtwork(context: Context, url: String?, longestSidePx: Int): Bitmap? {
        if (url.isNullOrBlank()) return null
        val px = artPxFor(longestSidePx)
        val request = ImageRequest.Builder(context)
            // Through the app's own size ladder, so this shares a disk-cache
            // entry with the rows, cards and headers already drawing the same
            // cover instead of pulling a widget-sized copy of its own over the
            // wire. Local artwork (content://…/albumart/…) carries no size hint
            // and passes through untouched.
            .data(url.artworkAt(px) ?: url)
            .size(px)
            .allowHardware(false) // the blur below reads pixels
            .build()
        val result = runCatching { SingletonImageLoader.get(context).execute(request) }.getOrNull()
        return (result as? SuccessResult)?.image?.toBitmap()
    }

    /**
     * The smallest size in the app's existing artwork ladder that still covers a
     * widget this big.
     */
    private fun artPxFor(longestSidePx: Int): Int = when {
        longestSidePx <= ROW_ART_PX -> ROW_ART_PX
        longestSidePx <= CARD_ART_PX -> CARD_ART_PX
        longestSidePx <= NOTIFICATION_ART_PX -> NOTIFICATION_ART_PX
        else -> HEADER_ART_PX
    }

    /** Fills the canvas with [src], cropped from its centre rather than squashed. */
    private fun Canvas.fillCentreCropped(src: Bitmap) {
        val scale = maxOf(width.toFloat() / src.width, height.toFloat() / src.height)
        val sampleW = (width / scale).coerceAtMost(src.width.toFloat())
        val sampleH = (height / scale).coerceAtMost(src.height.toFloat())
        val left = (src.width - sampleW) / 2f
        val top = (src.height - sampleH) / 2f
        drawBitmap(
            src,
            Rect(
                left.toInt(),
                top.toInt(),
                (left + sampleW).toInt(),
                (top + sampleH).toInt(),
            ),
            Rect(0, 0, width, height),
            Paint().apply { isFilterBitmap = true },
        )
    }

    /**
     * What stands in for a cover there isn't one of: a track with no artwork, a
     * fetch that failed, or nothing ever played.
     */
    private fun Canvas.fillPlaceholder() {
        drawRect(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    intArrayOf(0xFF2E3446.toInt(), 0xFF1B2130.toInt(), 0xFF07090E.toInt()),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
    }

    // ---- the blur ----

    /**
     * Blurs the bottom [regionPx] of [source], accelerating downwards.
     */
    private fun Canvas.blurBottom(source: Bitmap, regionPx: Int, bandPx: Int) {
        val top = source.height - regionPx
        val region = runCatching {
            Bitmap.createBitmap(source, 0, top, source.width, regionPx)
        }.getOrNull() ?: return

        val floorPx = regionPx * MIN_WORKING_SIGMA / (BLUR_SIGMAS.first() * bandPx)
        val small = region.halvedTo(floorPx)
        val w = small.width
        val h = small.height
        val pixels = IntArray(w * h)
        if (w >= 2 && h >= 2) small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== region) small.recycle()
        if (region !== source) region.recycle()
        if (w < 2 || h < 2) return

        val toWorking = h.toFloat() / regionPx

        val scratch = IntArray(pixels.size)
        val level = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { isFilterBitmap = true }

        var applied = 0f
        for (index in BLUR_SIGMAS.indices) {
            val target = BLUR_SIGMAS[index] * bandPx * toWorking
            val radius = boxRadiusFor(sqrt((target * target - applied * applied).coerceAtLeast(0f)))
            if (radius >= 1) {
                blurInPlace(pixels, scratch, w, h, radius)
                val step = sigmaOf(radius)
                applied = sqrt(applied * applied + step * step)
            }
            level.setPixels(pixels, 0, w, 0, 0, w, h)

            val soft = BitmapShader(level, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                setLocalMatrix(
                    Matrix().apply {
                        setScale(
                            source.width.toFloat() / w,
                            regionPx.toFloat() / h,
                        )
                        postTranslate(0f, top.toFloat())
                    },
                )
            }
            val start = top + STOPS[index] * regionPx
            val end = top + (STOPS[index] + STOP_FEATHER).coerceAtMost(1f) * regionPx
            val mask = LinearGradient(
                0f, start, 0f, maxOf(end, start + 1f),
                Color.TRANSPARENT, Color.WHITE, Shader.TileMode.CLAMP,
            )
            paint.shader = ComposeShader(soft, mask, PorterDuff.Mode.DST_IN)
            drawRect(0f, top.toFloat(), source.width.toFloat(), source.height.toFloat(), paint)
        }
        level.recycle()
    }

    private fun Bitmap.halvedTo(target: Float): Bitmap {
        var current = this
        while (current.height / 2 >= target && current.width / 2 >= 2) {
            val next = Bitmap.createScaledBitmap(
                current,
                current.width / 2,
                current.height / 2,
                true,
            )
            if (current !== this) current.recycle()
            current = next
        }
        return current
    }

    private fun blurInPlace(pixels: IntArray, scratch: IntArray, w: Int, h: Int, radius: Int) {
        val r = radius.coerceAtMost(maxOf(1, minOf(w, h) - 1))
        repeat(BLUR_PASSES) {
            boxPass(pixels, scratch, lines = h, lineStride = w, span = w, step = 1, radius = r)
            boxPass(scratch, pixels, lines = w, lineStride = 1, span = h, step = w, radius = r)
        }
    }

    private fun boxPass(
        src: IntArray,
        dst: IntArray,
        lines: Int,
        lineStride: Int,
        span: Int,
        step: Int,
        radius: Int,
    ) {
        val window = radius * 2 + 1
        for (line in 0 until lines) {
            val base = line * lineStride
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val c = src[base + i.coerceIn(0, span - 1) * step]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            for (i in 0 until span) {
                dst[base + i * step] =
                    OPAQUE or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                val gone = src[base + (i - radius).coerceIn(0, span - 1) * step]
                val come = src[base + (i + radius + 1).coerceIn(0, span - 1) * step]
                r += ((come shr 16) and 0xFF) - ((gone shr 16) and 0xFF)
                g += ((come shr 8) and 0xFF) - ((gone shr 8) and 0xFF)
                b += (come and 0xFF) - (gone and 0xFF)
            }
        }
    }

    private fun sigmaOf(radius: Int): Float =
        sqrt(BLUR_PASSES * (radius.toFloat() * radius + radius) / 3f)

    private fun boxRadiusFor(sigma: Float): Int =
        ((-1f + sqrt(1f + 12f * sigma * sigma / BLUR_PASSES)) / 2f).roundToInt()

    // ---- scrim and corners ----

    private fun Canvas.scrimBottom(bandPx: Int) {
        val top = (height - bandPx * SCRIM_SCALE).coerceAtLeast(0f)
        drawRect(
            0f,
            top,
            width.toFloat(),
            height.toFloat(),
            Paint().apply {
                shader = LinearGradient(
                    0f, top, 0f, height.toFloat(),
                    intArrayOf(Color.TRANSPARENT, 0x40000000, 0xB8000000.toInt()),
                    floatArrayOf(0f, 0.45f, 1f),
                    Shader.TileMode.CLAMP,
                )
            },
        )
    }

    private fun Bitmap.withRoundedCorners(radiusPx: Float): Bitmap {
        if (radiusPx <= 0f) return this
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            radiusPx,
            radiusPx,
            Paint().apply {
                isAntiAlias = true
                shader = BitmapShader(this@withRoundedCorners, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            },
        )
        return out
    }

    // ---- tuning ----

    private const val BLUR_REGION_SCALE = 2
    private val BLUR_SIGMAS = floatArrayOf(0.035f, 0.070f, 0.110f, 0.155f)
    private const val MIN_WORKING_SIGMA = 1.6f
    private const val BLUR_PASSES = 3
    private const val OPAQUE = 0xFF shl 24
    private val STOPS = floatArrayOf(0.28f, 0.50f, 0.70f, 0.86f)
    private const val STOP_FEATHER = 0.26f
    private const val SCRIM_SCALE = 1.2f

    private val composites = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
}
