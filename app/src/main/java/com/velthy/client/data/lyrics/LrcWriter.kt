package com.velthy.client.data.lyrics

/**
 * Turns parsed lines back into the text of an LRC file.
 */
internal fun List<LyricLine>.toLrc(): String {
    if (isEmpty()) return ""
    return sortedBy { it.timeMs }.joinToString("\n") { line -> stamp(line.timeMs) + line.text }
}

private fun stamp(timeMs: Long): String {
    val total = timeMs.coerceAtLeast(0L)
    val minutes = (total / 60_000).toString().padStart(2, '0')
    val seconds = (total % 60_000 / 1_000).toString().padStart(2, '0')
    val centiseconds = (total % 1_000 / 10).toString().padStart(2, '0')
    return "[$minutes:$seconds.$centiseconds]"
}
