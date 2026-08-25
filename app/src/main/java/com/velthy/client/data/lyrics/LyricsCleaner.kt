package com.velthy.client.data.lyrics

/**
 * Normalizes and cleans track titles and artist names for high-accuracy lyrics resolution.
 * Removes YouTube metadata noise (e.g. "(Official Music Video)", "[4K]", "(feat. ...)").
 */
object LyricsCleaner {

    private val NOISE_PATTERNS = listOf(
        Regex("""\((?:official|music|lyric|lyrics|audio|video|visualizer|remastered|deluxe|bonus|acoustic|live|extended|radio edit)[^)]*\)""", RegexOption.IGNORE_CASE),
        Regex("""\[(?:official|music|lyric|lyrics|audio|video|visualizer|remastered|deluxe|bonus|acoustic|live|extended|radio edit|hd|4k|hq)[^]]*]""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:official (?:music )?(?:video|audio)|lyric video|lyrics|audio|full song|visualizer|4k video|hd video)\b""", RegexOption.IGNORE_CASE),
        Regex("""\((?:feat\.?|ft\.?)[^)]*\)""", RegexOption.IGNORE_CASE),
        Regex("""\[(?:feat\.?|ft\.?)[^]]*]""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:feat\.?|ft\.?)\s+[^\s,]+""", RegexOption.IGNORE_CASE),
    )

    data class CleanResult(
        val rawTitle: String,
        val rawArtist: String,
        val cleanTitle: String,
        val cleanArtist: String,
        val primaryArtist: String,
    )

    fun clean(title: String, artist: String): CleanResult {
        var t = title
        // Remove text after " | " or " // "
        if (t.contains(" | ")) t = t.substringBefore(" | ")
        if (t.contains(" // ")) t = t.substringBefore(" // ")

        for (pattern in NOISE_PATTERNS) {
            t = t.replace(pattern, " ")
        }
        t = t.replace(Regex("""\s+"""), " ").trim()
        if (t.isBlank()) t = title.trim()

        var a = artist
        if (a.endsWith(" - Topic", ignoreCase = true)) {
            a = a.removeSuffix(" - Topic").trim()
        }
        if (a.contains(" // ")) a = a.substringBefore(" // ")
        a = a.replace(Regex("""\s+"""), " ").trim()
        if (a.isBlank()) a = artist.trim()

        // Extract primary artist (before comma, &, x, feat.)
        val primary = a.split(Regex("""[,&/]|(?:\s+(?:feat\.?|ft\.?|x|with)\s+)""", RegexOption.IGNORE_CASE))
            .firstOrNull()?.trim() ?: a

        return CleanResult(
            rawTitle = title.trim(),
            rawArtist = artist.trim(),
            cleanTitle = t,
            cleanArtist = a,
            primaryArtist = primary,
        )
    }
}
