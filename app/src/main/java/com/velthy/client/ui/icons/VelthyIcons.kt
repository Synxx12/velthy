package com.velthy.client.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Thick-stroke, round-capped icons in the spirit of Telegram's modern icon set.
 * Drawn as strokes (no fills) so the 2.2px weight + round joins read as a
 * single polished family. Tint is applied by [androidx.compose.material3.Icon].
 */
object VelthyIcons {

    private const val STROKE = 2.2f
    private val stroke = SolidColor(Color.Black)

    val Play: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_play",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                fill = stroke,
            ) {
                moveTo(6.8f, 4.8f)
                lineTo(19.2f, 12f)
                lineTo(6.8f, 19.2f)
                close()
            }
        }.build()
    }

    val Search: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_search",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Lens (full circle from two arcs)
                moveTo(4.6f, 11f)
                arcToRelative(6.4f, 6.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, 12.8f, 0f)
                arcToRelative(6.4f, 6.4f, 0f, isMoreThanHalf = true, isPositiveArc = true, -12.8f, 0f)
                // Handle
                moveTo(15.9f, 15.9f)
                lineTo(20.4f, 20.4f)
            }
        }.build()
    }

    val Explore: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_explore",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Compass dial
                moveTo(3.4f, 12f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.2f, 0f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, -17.2f, 0f)
                // Needle
                moveTo(15.4f, 8.6f)
                lineTo(13.6f, 13.6f)
                lineTo(8.6f, 15.4f)
                lineTo(10.4f, 10.4f)
                close()
            }
        }.build()
    }

    /**
     * Clock face with two hands — a download asked for but not yet on disk.
     */
    val Clock: ImageVector by lazy {
        ImageVector.Builder(
            name = "velthy_clock",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Dial (full circle from two arcs)
                moveTo(3.4f, 12f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, 17.2f, 0f)
                arcToRelative(8.6f, 8.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, -17.2f, 0f)
                // Minute hand up, hour hand down to the right
                moveTo(12f, 7.4f); lineTo(12f, 12f); lineTo(15.4f, 13.8f)
            }
        }.build()
    }

    val Shuffle: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_shuffle",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Strand that crosses downwards, with its arrow head
                moveTo(3.4f, 7.4f); lineTo(7f, 7.4f); lineTo(16.6f, 16.6f); lineTo(20.6f, 16.6f)
                moveTo(18.1f, 14.1f); lineTo(20.6f, 16.6f); lineTo(18.1f, 19.1f)
                // Strand that crosses upwards, broken around the intersection
                moveTo(3.4f, 16.6f); lineTo(7f, 16.6f); lineTo(9.8f, 13.9f)
                moveTo(13.9f, 10.1f); lineTo(16.6f, 7.4f); lineTo(20.6f, 7.4f)
                moveTo(18.1f, 4.9f); lineTo(20.6f, 7.4f); lineTo(18.1f, 9.9f)
            }
        }.build()
    }

    val Repeat: ImageVector by lazy { repeatLoop("bc_repeat", withOne = false) }

    val RepeatOne: ImageVector by lazy { repeatLoop("bc_repeat_one", withOne = true) }

    /**
     * Two straight runs joined by semicircles, with the arrow heads lying flat
     * at the ends of the straights. Putting them on the curves instead — as a
     * first pass did — makes the glyph read as a refresh/sync symbol.
     */
    private fun repeatLoop(name: String, withOne: Boolean): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.6f, 7.6f)
                lineTo(15.4f, 7.6f)
                arcToRelative(4.4f, 4.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 8.8f)
                lineTo(8.6f, 16.4f)
                arcToRelative(4.4f, 4.4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, -8.8f)
                close()
                // Direction of travel: right along the top, left along the bottom.
                moveTo(13.5f, 5.7f); lineTo(15.4f, 7.6f); lineTo(13.5f, 9.5f)
                moveTo(10.5f, 14.5f); lineTo(8.6f, 16.4f); lineTo(10.5f, 18.3f)

                if (withOne) {
                    // Slim "1" inside the loop
                    moveTo(10.9f, 10.9f); lineTo(12.2f, 10f); lineTo(12.2f, 14f)
                }
            }
        }.build()

    /** AutoPlay's lemniscate. */
    val Infinity: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_infinity",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 12f)
                curveTo(10.1f, 9.1f, 8.7f, 8f, 7.1f, 8f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0f, 8f)
                curveTo(8.7f, 16f, 10.1f, 14.9f, 12f, 12f)
                curveTo(13.9f, 9.1f, 15.3f, 8f, 16.9f, 8f)
                arcToRelative(4f, 4f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0f, 8f)
                curveTo(15.3f, 16f, 13.9f, 14.9f, 12f, 12f)
            }
        }.build()
    }

    /** Beamed pair of notes, for instrumental stretches in the lyrics. */
    val MusicNote: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_music_note",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            // Heads are solid; stems and beam keep the family's stroke weight.
            path(fill = stroke) {
                moveTo(4.2f, 17.7f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 5.8f, 0f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -5.8f, 0f)
                close()
                moveTo(14.2f, 15.9f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 5.8f, 0f)
                arcToRelative(2.9f, 2.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, -5.8f, 0f)
                close()
            }
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10f, 17.7f); lineTo(10f, 6.7f)
                moveTo(20f, 15.9f); lineTo(20f, 4.9f)
                moveTo(10f, 6.7f); lineTo(20f, 4.9f)
            }
        }.build()
    }

    /** Speech bubble with two lines of words. */
    val Lyrics: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_lyrics",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.2f, 4.6f)
                lineTo(17.8f, 4.6f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.8f, 2.8f)
                lineTo(20.6f, 13.6f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.8f, 2.8f)
                lineTo(10.6f, 16.4f)
                lineTo(6.8f, 19.6f)
                lineTo(6.8f, 16.4f)
                lineTo(6.2f, 16.4f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.8f, -2.8f)
                lineTo(3.4f, 7.4f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.8f, -2.8f)
                close()
                moveTo(7.6f, 9f); lineTo(16.4f, 9f)
                moveTo(7.6f, 12.1f); lineTo(13.2f, 12.1f)
            }
        }.build()
    }

    /** Speech bubble with quotation marks inside — Apple Music style lyrics icon. */
    val LyricsQuote: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_lyrics_quote",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.5f, 4.5f)
                lineTo(17.5f, 4.5f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.8f, 2.8f)
                lineTo(20.3f, 13.5f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.8f, 2.8f)
                lineTo(10.5f, 16.3f)
                lineTo(6.5f, 19.5f)
                lineTo(6.5f, 16.3f)
                lineTo(6.5f, 16.3f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.8f, -2.8f)
                lineTo(3.7f, 7.3f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.8f, -2.8f)
                close()
            }
            path(fill = stroke) {
                moveTo(9f, 9.2f)
                arcToRelative(1.3f, 1.3f, 0f, isMoreThanHalf = true, isPositiveArc = true, -2.6f, 0f)
                arcToRelative(1.3f, 1.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.6f, 0f)
                close()
                moveTo(9f, 9.2f)
                lineTo(7.8f, 12.8f)
                lineTo(6.6f, 12.8f)
                lineTo(7.8f, 9.2f)
                close()
                moveTo(14.5f, 9.2f)
                arcToRelative(1.3f, 1.3f, 0f, isMoreThanHalf = true, isPositiveArc = true, -2.6f, 0f)
                arcToRelative(1.3f, 1.3f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2.6f, 0f)
                close()
                moveTo(14.5f, 9.2f)
                lineTo(13.3f, 12.8f)
                lineTo(12.1f, 12.8f)
                lineTo(13.3f, 9.2f)
                close()
            }
        }.build()
    }

    /** Plain chevron — a disclosure hint, not a directional arrow. */
    val ChevronRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_chevron_right",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9.5f, 6.2f)
                lineTo(15.3f, 12f)
                lineTo(9.5f, 17.8f)
            }
        }.build()
    }

    /**
     * The player's like control, in two weights.
     *
     * Filled rather than merely tinted when set: the player draws every glyph
     * white on artwork, where a colour change alone is the one signal the
     * backdrop can swallow. A shape change survives any album cover.
     */
    val Heart: ImageVector by lazy { heart("bc_heart", filled = false) }

    val HeartFilled: ImageVector by lazy { heart("bc_heart_filled", filled = true) }

    private fun heart(name: String, filled: Boolean): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                fill = if (filled) stroke else null,
            ) {
                // Two lobes meeting at the top notch, falling to a single point.
                moveTo(12f, 20f)
                curveTo(12f, 20f, 3.2f, 14.6f, 3.2f, 8.9f)
                arcToRelative(4.5f, 4.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.8f, -1.5f)
                arcToRelative(4.5f, 4.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 8.8f, 1.5f)
                curveTo(20.8f, 14.6f, 12f, 20f, 12f, 20f)
                close()
            }
        }.build()

    /** Adding something — a new playlist, on the library shelf. */
    val Plus: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_plus",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 5f); lineTo(12f, 19f)
                moveTo(5f, 12f); lineTo(19f, 12f)
            }
        }.build()
    }

    /** Arrow pointing down into a tray — offline download. */
    val Download: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_download",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Vertical stem
                moveTo(12f, 4.5f); lineTo(12f, 15.5f)
                // Arrow head
                moveTo(7.5f, 11f); lineTo(12f, 15.5f); lineTo(16.5f, 11f)
                // Tray base
                moveTo(4.5f, 18f); lineTo(19.5f, 18f)
            }
        }.build()
    }

    val Library: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_library",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                // Three upright spines + one leaning — Apple Music's library glyph, thickened
                moveTo(4.6f, 4.8f); lineTo(4.6f, 19.2f)
                moveTo(9.2f, 4.8f); lineTo(9.2f, 19.2f)
                moveTo(13.8f, 4.8f); lineTo(13.8f, 19.2f)
                moveTo(17.2f, 5.6f); lineTo(20.6f, 18.9f)
            }
        }.build()
    }

    val NorthWest: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_north_west",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(6.5f, 6.5f)
                lineTo(15.5f, 6.5f)
                moveTo(6.5f, 6.5f)
                lineTo(6.5f, 15.5f)
                moveTo(6.5f, 6.5f)
                lineTo(17.5f, 17.5f)
            }
        }.build()
    }

    val TrendingUp: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_trending_up",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(3.5f, 17.5f)
                lineTo(9.5f, 11.5f)
                lineTo(13.5f, 15.5f)
                lineTo(20.5f, 7.5f)
                moveTo(14.5f, 7.5f)
                lineTo(20.5f, 7.5f)
                lineTo(20.5f, 13.5f)
            }
        }.build()
    }

    val Podcasts: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_podcasts",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 11f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, 4f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, -4f)
                moveTo(8.5f, 8.5f)
                arcToRelative(5f, 5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 7f, 0f)
                moveTo(6f, 6f)
                arcToRelative(8.5f, 8.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 12f, 0f)
                moveTo(12f, 15f)
                lineTo(12f, 21f)
            }
        }.build()
    }

    val Moon: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_moon",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3f)
                arcToRelative(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 9f, 9f)
                arcToRelative(9f, 9f, 0f, isMoreThanHalf = false, isPositiveArc = true, -9f, -9f)
                close()
            }
        }.build()
    }

    val Headphones: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_headphones",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 12f)
                arcToRelative(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, 16f, 0f)
                moveTo(4f, 12f); lineTo(4f, 17f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2f, 2f)
                lineTo(6.5f, 19f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, 1.5f, -1.5f)
                lineTo(8f, 13.5f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = false, -1.5f, -1.5f)
                lineTo(4f, 12f)
                moveTo(20f, 12f); lineTo(20f, 17f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(17.5f, 19f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.5f, -1.5f)
                lineTo(16f, 13.5f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = false, isPositiveArc = true, 1.5f, -1.5f)
                lineTo(20f, 12f)
            }
        }.build()
    }

    val AirPlay: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_airplay",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(5f, 17f)
                lineTo(4f, 17f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(2f, 6f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                lineTo(20f, 4f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(22f, 15f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(19f, 17f)
                moveTo(12f, 15f)
                lineTo(7f, 20f)
                lineTo(17f, 20f)
                close()
            }
        }.build()
    }

    val Earbuds: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_earbuds",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7.5f, 7f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = true, isPositiveArc = false, -3f, 2.8f)
                lineTo(6f, 18.5f)
                arcToRelative(1.2f, 1.2f, 0f, isMoreThanHalf = false, isPositiveArc = false, 2.4f, 0f)
                lineTo(7.5f, 9.5f)
                moveTo(16.5f, 7f)
                arcToRelative(2.8f, 2.8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 3f, 2.8f)
                lineTo(18f, 18.5f)
                arcToRelative(1.2f, 1.2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2.4f, 0f)
                lineTo(16.5f, 9.5f)
            }
        }.build()
    }

    val Speaker: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_speaker",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 4f)
                lineTo(17f, 4f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, 2f)
                lineTo(19f, 18f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(7f, 20f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                lineTo(5f, 6f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, 2f, -2f)
                moveTo(12f, 8f)
                arcToRelative(1.5f, 1.5f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, 0.01f)
                moveTo(12f, 14f)
                arcToRelative(3f, 3f, 0f, isMoreThanHalf = true, isPositiveArc = true, 0f, 0.01f)
            }
        }.build()
    }

    val UsbDac: ImageVector by lazy {
        ImageVector.Builder(
            name = "mq_usbdac",
            defaultWidth = 24.dp, defaultHeight = 24.dp,
            viewportWidth = 24f, viewportHeight = 24f,
        ).apply {
            path(
                stroke = stroke,
                strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8f, 7f)
                lineTo(16f, 7f)
                lineTo(16f, 17f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, 2f)
                lineTo(10f, 19f)
                arcToRelative(2f, 2f, 0f, isMoreThanHalf = false, isPositiveArc = true, -2f, -2f)
                close()
                moveTo(10f, 7f); lineTo(10f, 4f); lineTo(14f, 4f); lineTo(14f, 7f)
                moveTo(12f, 11f); lineTo(12f, 15f)
            }
        }.build()
    }
}
