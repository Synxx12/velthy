package com.velthy.client.ui.replay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.velthy.client.R
import com.velthy.client.data.model.CARD_ART_PX
import com.velthy.client.data.model.artworkAt
import com.velthy.client.data.stats.ReplaySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale

suspend fun renderReplayPoster(
    context: Context,
    summary: ReplaySummary,
    holder: String,
    memberSince: String?,
    page: ReplayStoryPage? = null,
): Bitmap = coroutineScope {
    val songs = summary.songRows(POSTER_ROWS)
    val artists = summary.artistRows(POSTER_ROWS)
    val albums = summary.albumRows(POSTER_ALBUMS)
    val covers = (songs + artists + albums)
        .mapNotNull { it.artworkUrl }
        .distinct()
        .associateWith { url -> async(Dispatchers.IO) { loadBitmap(context, url) } }
        .mapValues { it.value.await() }

    withContext(Dispatchers.Default) {
        val lit = page?.let { summary.storyArtwork(it) }
            ?: songs.firstOrNull()?.artworkUrl
        val bitmap = Bitmap.createBitmap(POSTER_W, POSTER_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val type = Fonts(context)

        drawBackdrop(canvas, covers[lit], page?.let(::storyHue) ?: 0f)
        var y = drawHeader(canvas, context, type, summary, holder, memberSince)
        if (page == null) {
            y = drawTotals(canvas, type, summary, y)
            y = drawColumns(canvas, type, songs, artists, covers, y)
            y = drawAlbums(canvas, type, albums, covers, y)
            drawGenres(canvas, type, summary, y)
        } else {
            y = drawRuns(canvas, type, summary.storyHeadline(page), MARGIN, y, CONTENT_W)
            drawCard(canvas, type, summary, page, covers, y + 56f)
        }
        drawFooter(canvas, type)
        bitmap
    }
}

private fun drawCard(
    canvas: Canvas,
    type: Fonts,
    summary: ReplaySummary,
    page: ReplayStoryPage,
    covers: Map<String, Bitmap?>,
    top: Float,
) {
    when (page) {
        ReplayStoryPage.INTRO, ReplayStoryPage.MINUTES ->
            drawCollage(canvas, summary, covers, top)
        ReplayStoryPage.SONGS ->
            drawLeaderboard(canvas, type, summary.songRows(CARD_ROWS), covers, top, false)
        ReplayStoryPage.ARTISTS ->
            drawLeaderboard(canvas, type, summary.artistRows(CARD_ROWS), covers, top, true)
        ReplayStoryPage.ALBUMS ->
            drawLeaderboard(canvas, type, summary.albumRows(CARD_ROWS), covers, top, false)
        ReplayStoryPage.GENRES -> drawBigList(canvas, type, summary.genreRows(CARD_ROWS), top)
        ReplayStoryPage.HABITS -> drawHabits(canvas, type, summary, top)
        ReplayStoryPage.SUMMARY -> drawRecap(canvas, type, summary, top)
    }
}

private fun drawLeaderboard(
    canvas: Canvas,
    type: Fonts,
    rows: List<ReplayRow>,
    covers: Map<String, Bitmap?>,
    top: Float,
    circular: Boolean,
) {
    val lead = rows.firstOrNull() ?: return
    val hero = 348f
    drawArtwork(canvas, lead.artworkUrl?.let { covers[it] }, lead.title, MARGIN, top, hero, circular)

    val textX = MARGIN + hero + 48f
    val textWidth = POSTER_W - MARGIN - textX
    val title = type.heading(78f, Color.WHITE)
    canvas.drawText(ellipsised(lead.title, title, textWidth), textX, top + 86f, title)
    var y = top + 86f
    lead.subtitle?.let {
        val sub = type.body(50f, 0xB3FFFFFF.toInt())
        y += 62f
        canvas.drawText(ellipsised(it, sub, textWidth), textX, y, sub)
    }
    val stats = type.body(44f, 0x8CFFFFFF.toInt())
    canvas.drawText(
        "${formatListening(lead.ms)} · ${countOf(lead.plays, "play")}",
        textX,
        y + 62f,
        stats,
    )

    var rowY = top + hero + 90f
    rows.drop(1).forEach { row ->
        canvas.drawText(
            row.rank.toString(),
            MARGIN,
            rowY + 72f,
            type.heading(44f, 0x73FFFFFF),
        )
        val artX = MARGIN + 62f
        drawArtwork(canvas, row.artworkUrl?.let { covers[it] }, row.title, artX, rowY, 108f, circular)
        val name = type.body(48f, Color.WHITE, bold = true)
        val nameX = artX + 108f + 28f
        val stat = type.body(38f, 0x80FFFFFF.toInt())
        val statWidth = stat.measureText(formatListening(row.ms))
        canvas.drawText(
            ellipsised(row.title, name, POSTER_W - MARGIN - nameX - statWidth - 32f),
            nameX,
            rowY + 72f,
            name,
        )
        stat.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatListening(row.ms), POSTER_W - MARGIN, rowY + 72f, stat)
        rowY += 148f
    }
}

private fun drawBigList(canvas: Canvas, type: Fonts, rows: List<ReplayRow>, top: Float) {
    val lead = rows.firstOrNull() ?: return
    val word = type.heading(150f, Color.WHITE)
    canvas.drawText(ellipsised(lead.title, word, CONTENT_W), MARGIN, top + 120f, word)
    canvas.drawText(formatListening(lead.ms), MARGIN, top + 186f, type.body(46f, 0x99FFFFFF.toInt()))

    var y = top + 300f
    rows.drop(1).forEach { row ->
        canvas.drawText(row.rank.toString(), MARGIN, y, type.heading(48f, 0x73FFFFFF))
        val name = type.body(52f, Color.WHITE, bold = true)
        canvas.drawText(row.title, MARGIN + 80f, y, name)
        val stat = type.body(38f, 0x80FFFFFF.toInt()).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(formatListening(row.ms), POSTER_W - MARGIN, y, stat)
        y += 106f
    }
}

private fun drawHabits(canvas: Canvas, type: Fonts, summary: ReplaySummary, top: Float) {
    var y = top + 40f
    fun stat(value: String, label: String) {
        canvas.drawText(value, MARGIN, y, type.heading(84f, Color.WHITE))
        canvas.drawText(label, MARGIN, y + 56f, type.body(42f, 0x99FFFFFF.toInt()))
        y += 176f
    }
    if (summary.distinctAlbums > 0) {
        stat(grouped(summary.distinctAlbums.toLong()), "different albums")
    }
    summary.busiestDay?.let {
        stat(formatDay(it), "your biggest day — ${formatListening(summary.busiestDayMs)}")
    }
    summary.peakHour?.let { stat(formatHour(it), "when you listen most") }
}

private fun drawRecap(canvas: Canvas, type: Fonts, summary: ReplaySummary, top: Float) {
    var y = top + 40f
    fun line(label: String, value: String) {
        canvas.drawText(label, MARGIN, y, type.body(42f, 0x80FFFFFF.toInt()))
        val v = type.body(52f, Color.WHITE, bold = true)
        canvas.drawText(ellipsised(value, v, CONTENT_W - 320f), MARGIN + 320f, y, v)
        y += 96f
    }
    line("Minutes", formatMinutes(summary.totalMs))
    summary.songs.firstOrNull()?.let { line("Top song", it.song.title) }
    summary.artists.firstOrNull()?.let { line("Top artist", it.title) }
    summary.albums.firstOrNull()?.let { line("Top album", it.title) }
    summary.genres.firstOrNull()?.let { line("Top genre", it.title) }
}

private fun drawCollage(
    canvas: Canvas,
    summary: ReplaySummary,
    covers: Map<String, Bitmap?>,
    top: Float,
) {
    val squares = summary.songs.mapNotNull { it.song.thumbnailUrl }.distinct().take(3)
    val faces = summary.artists.mapNotNull { it.artworkUrl }.distinct()
        .filterNot { it in squares }.take(3)
    val squareAt = listOf(Triple(0.22f, 0.30f, 500f), Triple(0.02f, 0.06f, 260f), Triple(0.66f, 0.00f, 215f))
    val faceAt = listOf(Triple(0.00f, 0.62f, 185f), Triple(0.72f, 0.32f, 225f), Triple(0.46f, 0.76f, 200f))
    val height = 900f
    squares.forEachIndexed { index, url ->
        val (fx, fy, size) = squareAt[index]
        drawArtwork(canvas, covers[url], "", MARGIN + CONTENT_W * fx, top + height * fy, size, false)
    }
    faces.forEachIndexed { index, url ->
        val (fx, fy, size) = faceAt[index]
        drawArtwork(canvas, covers[url], "", MARGIN + CONTENT_W * fx, top + height * fy, size, true)
    }
}

private fun drawBackdrop(canvas: Canvas, lead: Bitmap?, hue: Float) {
    val colors = paletteOf(lead).map { rotated(it, hue) }
    canvas.drawColor(dimmed(colors.first()))

    val anchors = listOf(
        0.20f to 0.16f,
        0.84f to 0.22f,
        0.76f to 0.66f,
        0.18f to 0.78f,
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    colors.forEachIndexed { index, color ->
        val (fx, fy) = anchors[index]
        val cx = POSTER_W * fx
        val cy = POSTER_H * fy
        val radius = POSTER_W * 0.95f
        paint.shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(
                ColorUtils.setAlphaComponent(color, 210),
                ColorUtils.setAlphaComponent(color, 0),
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, paint)
    }
    paint.shader = null

    paint.shader = android.graphics.LinearGradient(
        0f,
        0f,
        0f,
        POSTER_H.toFloat(),
        intArrayOf(0x8C000000.toInt(), 0x59000000, 0xCC000000.toInt()),
        floatArrayOf(0f, 0.42f, 1f),
        Shader.TileMode.CLAMP,
    )
    canvas.drawRect(0f, 0f, POSTER_W.toFloat(), POSTER_H.toFloat(), paint)
    paint.shader = null
}

private fun paletteOf(bitmap: Bitmap?): List<Int> {
    val fallback = listOf(0xFF3A1C71.toInt(), 0xFFD76D77.toInt(), 0xFF2B5876.toInt(), 0xFFFFAF7B.toInt())
    val source = bitmap ?: return fallback
    val swatches = runCatching {
        Palette.from(source).maximumColorCount(24).generate().swatches
            .sortedByDescending { it.population }
            .map { it.rgb }
    }.getOrNull().orEmpty()
    if (swatches.isEmpty()) return fallback
    return (swatches + fallback).take(4).map(::tuned)
}

private fun rotated(color: Int, degrees: Float): Int {
    if (degrees == 0f) return color
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[0] = (hsl[0] + degrees) % 360f
    return ColorUtils.HSLToColor(hsl)
}

private fun tuned(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
    hsl[2] = hsl[2].coerceIn(0.28f, 0.58f)
    return ColorUtils.HSLToColor(hsl)
}

private fun dimmed(color: Int): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[2] = 0.10f
    return ColorUtils.HSLToColor(hsl)
}

private fun drawHeader(
    canvas: Canvas,
    context: Context,
    type: Fonts,
    summary: ReplaySummary,
    holder: String,
    memberSince: String?,
): Float {
    val label = summary.label
    val title = if (label.length == 4 && label.all { it.isDigit() }) {
        "Replay'${label.takeLast(2)}"
    } else {
        "Replay · $label"
    }
    canvas.drawText(title, MARGIN, 132f, type.heading(46f, 0xFFFFFFFF.toInt()))
    val brand = type.heading(46f, 0xE6FFFFFF.toInt()).apply {
        textAlign = Paint.Align.RIGHT
    }
    canvas.drawText("velthy", POSTER_W - MARGIN, 132f, brand)
    val wordWidth = brand.measureText("velthy")
    drawLogo(canvas, context, POSTER_W - MARGIN - wordWidth - LOGO_GAP, 132f)

    val credit = listOfNotNull(
        holder.ifBlank { DEFAULT_HOLDER },
        memberSince?.let { "member since $it" },
    ).joinToString(" · ")
    canvas.drawText(credit.uppercase(Locale.ROOT), MARGIN, 182f, type.label(24f, 0x8CFFFFFF.toInt()))
    return 300f
}

private fun drawTotals(canvas: Canvas, type: Fonts, summary: ReplaySummary, top: Float): Float {
    canvas.drawText(
        formatMinutes(summary.totalMs),
        MARGIN,
        top + 120f,
        type.heading(148f, Color.WHITE),
    )
    canvas.drawText(
        "MINUTES LISTENED",
        MARGIN,
        top + 176f,
        type.label(30f, 0xB3FFFFFF.toInt(), tracking = 0.14f),
    )
    canvas.drawText(
        "${countOf(summary.totalPlays, "play")} · " +
            "${countOf(summary.distinctSongs, "song")} · " +
            countOf(summary.distinctArtists, "artist"),
        MARGIN,
        top + 232f,
        type.body(30f, 0x99FFFFFF.toInt()),
    )
    return top + 320f
}

private fun drawColumns(
    canvas: Canvas,
    type: Fonts,
    songs: List<ReplayRow>,
    artists: List<ReplayRow>,
    covers: Map<String, Bitmap?>,
    top: Float,
): Float {
    val columnWidth = (POSTER_W - MARGIN * 2 - COLUMN_GAP) / 2f
    val right = MARGIN + columnWidth + COLUMN_GAP

    canvas.drawText("TOP SONGS", MARGIN, top, type.label(28f, 0xB3FFFFFF.toInt(), tracking = 0.16f))
    canvas.drawText("TOP ARTISTS", right, top, type.label(28f, 0xB3FFFFFF.toInt(), tracking = 0.16f))

    val rows = maxOf(songs.size, artists.size)
    var y = top + 54f
    repeat(rows) { index ->
        songs.getOrNull(index)?.let { drawRow(canvas, type, it, covers, MARGIN, y, columnWidth, false) }
        artists.getOrNull(index)?.let { drawRow(canvas, type, it, covers, right, y, columnWidth, true) }
        y += ROW_HEIGHT
    }
    return y + 40f
}

private fun drawRow(
    canvas: Canvas,
    type: Fonts,
    row: ReplayRow,
    covers: Map<String, Bitmap?>,
    x: Float,
    y: Float,
    width: Float,
    circular: Boolean,
) {
    canvas.drawText(
        row.rank.toString(),
        x,
        y + ART * 0.68f,
        type.heading(34f, if (row.rank == 1) ACCENT else 0x73FFFFFF),
    )
    val artX = x + 44f
    val art = row.artworkUrl?.let { covers[it] }
    drawArtwork(canvas, art, row.title, artX, y, ART, circular)

    val textX = artX + ART + 20f
    val textWidth = width - (textX - x)
    val title = type.body(30f, Color.WHITE, bold = true)
    canvas.drawText(ellipsised(row.title, title, textWidth), textX, y + 36f, title)
    val sub = type.body(25f, 0x99FFFFFF.toInt())
    val detail = listOfNotNull(row.subtitle, formatListening(row.ms)).joinToString(" · ")
    canvas.drawText(ellipsised(detail, sub, textWidth), textX, y + 72f, sub)
}

private fun drawAlbums(
    canvas: Canvas,
    type: Fonts,
    albums: List<ReplayRow>,
    covers: Map<String, Bitmap?>,
    top: Float,
): Float {
    if (albums.isEmpty()) return top
    canvas.drawText("TOP ALBUMS", MARGIN, top, type.label(28f, 0xB3FFFFFF.toInt(), tracking = 0.16f))
    val size = 200f
    val gap = (POSTER_W - MARGIN * 2 - size * POSTER_ALBUMS) / (POSTER_ALBUMS - 1)
    val y = top + 40f
    albums.take(POSTER_ALBUMS).forEachIndexed { index, album ->
        val x = MARGIN + index * (size + gap)
        drawArtwork(canvas, album.artworkUrl?.let { covers[it] }, album.title, x, y, size, false)
        val name = type.body(25f, Color.WHITE, bold = true)
        canvas.drawText(ellipsised(album.title, name, size), x, y + size + 38f, name)
        val sub = type.body(22f, 0x8CFFFFFF.toInt())
        canvas.drawText(
            ellipsised(album.subtitle ?: formatListening(album.ms), sub, size),
            x,
            y + size + 70f,
            sub,
        )
    }
    return y + size + 130f
}

private fun drawGenres(canvas: Canvas, type: Fonts, summary: ReplaySummary, top: Float) {
    val genres = summary.genreRows(POSTER_GENRES)
    if (genres.isEmpty()) return
    canvas.drawText("TOP GENRES", MARGIN, top, type.label(28f, 0xB3FFFFFF.toInt(), tracking = 0.16f))
    val paint = type.body(32f, Color.WHITE, bold = true)
    val chip = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x26FFFFFF }
    var x = MARGIN
    val y = top + 44f
    genres.forEach { genre ->
        val width = paint.measureText(genre.title) + 44f
        if (x + width > POSTER_W - MARGIN) return
        canvas.drawRoundRect(RectF(x, y, x + width, y + 62f), 31f, 31f, chip)
        canvas.drawText(genre.title, x + 22f, y + 42f, paint)
        x += width + 14f
    }
}

private fun drawFooter(canvas: Canvas, type: Fonts) {
    canvas.drawText(
        "Counted on device with velthy",
        MARGIN,
        POSTER_H - 64f,
        type.label(24f, 0x73FFFFFF, tracking = 0.08f),
    )
}

private fun drawArtwork(
    canvas: Canvas,
    bitmap: Bitmap?,
    fallback: String,
    x: Float,
    y: Float,
    size: Float,
    circular: Boolean,
) {
    val bounds = RectF(x, y, x + size, y + size)
    val radius = if (circular) size / 2f else size * 0.10f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (bitmap != null) {
        val scale = size / minOf(bitmap.width, bitmap.height).toFloat()
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(
                x - (bitmap.width * scale - size) / 2f,
                y - (bitmap.height * scale - size) / 2f,
            )
        }
        paint.shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            .apply { setLocalMatrix(matrix) }
        canvas.drawRoundRect(bounds, radius, radius, paint)
        paint.shader = null
    } else {
        val hue = (fallback.hashCode().toFloat() % 360f + 360f) % 360f
        paint.color = ColorUtils.HSLToColor(floatArrayOf(hue, 0.55f, 0.45f))
        canvas.drawRoundRect(bounds, radius, radius, paint)
    }
}

private fun drawRuns(
    canvas: Canvas,
    type: Fonts,
    runs: List<HeadlineRun>,
    x: Float,
    top: Float,
    maxWidth: Float,
): Float {
    val bold = type.heading(HEADLINE_SIZE, Color.WHITE)
    val plain = type.body(HEADLINE_SIZE, 0x9EFFFFFF.toInt(), bold = true)

    val text = StringBuilder()
    val weights = ArrayList<Boolean>()
    runs.forEach { run ->
        text.append(run.text)
        repeat(run.text.length) { weights += run.bold }
    }

    var lineX = x
    var y = top + HEADLINE_SIZE
    var index = 0
    while (index < text.length) {
        if (text[index].isWhitespace()) {
            index++
            continue
        }
        var end = index
        while (end < text.length && !text[end].isWhitespace()) end++
        val word = text.substring(index, end)
        val paint = if (weights[index]) bold else plain
        val width = paint.measureText("$word ")
        if (lineX + width - x > maxWidth && lineX > x) {
            lineX = x
            y += HEADLINE_LEADING
        }
        canvas.drawText(word, lineX, y, paint)
        lineX += width
        index = end
    }
    return y + 24f
}

private fun drawLogo(canvas: Canvas, context: Context, right: Float, baseline: Float) {
    val logo = runCatching {
        ResourcesCompat.getDrawable(context.resources, R.drawable.ic_logo, null)
    }.getOrNull() ?: return
    val left = right - LOGO_W
    val top = baseline - LOGO_H
    logo.setTint(0xE6FFFFFF.toInt())
    logo.setBounds(left.toInt(), top.toInt(), (left + LOGO_W).toInt(), (top + LOGO_H).toInt())
    logo.draw(canvas)
}

private fun ellipsised(text: String, paint: Paint, width: Float): String {
    if (paint.measureText(text) <= width) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.take(end) + "…") > width) end--
    return text.take(end).trimEnd() + "…"
}

private class Fonts(context: Context) {
    private val heavy = font(context, R.font.sf_pro_display_heavy) ?: Typeface.DEFAULT_BOLD
    private val semibold = font(context, R.font.sf_pro_display_semibold) ?: Typeface.DEFAULT_BOLD
    private val regular = font(context, R.font.sf_pro_display_regular) ?: Typeface.DEFAULT

    fun heading(size: Float, color: Int) = paint(heavy, size, color)

    fun body(size: Float, color: Int, bold: Boolean = false) =
        paint(if (bold) semibold else regular, size, color)

    fun label(size: Float, color: Int, tracking: Float = 0.10f) =
        paint(semibold, size, color).apply { letterSpacing = tracking }

    private fun paint(face: Typeface, size: Float, color: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color
        }

    private fun font(context: Context, id: Int): Typeface? =
        runCatching { ResourcesCompat.getFont(context, id) }.getOrNull()
}

private suspend fun loadBitmap(context: Context, url: String): Bitmap? = runCatching {
    val request = ImageRequest.Builder(context)
        .data(url.artworkAt(CARD_ART_PX))
        .allowHardware(false)
        .build()
    (SingletonImageLoader.get(context).execute(request) as? SuccessResult)?.image?.toBitmap()
}.getOrNull()

private const val POSTER_W = 1080
private const val POSTER_H = 1920

private const val MARGIN = 72f

private const val LOGO_W = 66f
private const val LOGO_H = 44f
private const val LOGO_GAP = 20f

private const val CONTENT_W = POSTER_W - MARGIN * 2

private const val HEADLINE_SIZE = 88f
private const val HEADLINE_LEADING = 108f

private const val CARD_ROWS = 5
private const val COLUMN_GAP = 36f
private const val ROW_HEIGHT = 116f
private const val ART = 84f
private const val ACCENT = 0xFFFA2D48.toInt()

private const val POSTER_ROWS = 5
private const val POSTER_ALBUMS = 3
private const val POSTER_GENRES = 4
