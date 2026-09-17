package com.velthy.client.data

/**
 * How loudly a release should be announced.
 *
 * GitHub Releases has no priority field, so the signal travels in the release
 * body itself: the release workflow writes a `Velthy-Severity:` line just under
 * the heading, derived from the changelog's own section headings. The app reads
 * that line back out. When it is absent — an old release, or notes written by
 * hand — [NORMAL] is assumed, which is the safe default: the worst a missing
 * line can do is under-promise, never wrongly badge a routine build as urgent.
 *
 * Ordering is deliberate and is what [rank] compares, so a check can tell
 * "this release is more urgent than the one I already notified about" without
 * hard-coding a list in two places.
 */
enum class UpdateSeverity(
    /** Short label shown on the badge. */
    val label: String,
    /** One line explaining the badge, shown under the title. */
    val blurb: String,
    /**
     * Whether this release should interrupt — a heads-up notification that
     * peeks over whatever is on screen, rather than only landing in the shade.
     */
    val interrupts: Boolean,
) {
    NORMAL(
        label = "Update",
        blurb = "A new version of Velthy is available.",
        interrupts = false,
    ),
    IMPORTANT(
        label = "Important",
        blurb = "Recommended update with notable fixes and improvements.",
        interrupts = true,
    ),
    CRITICAL(
        label = "Critical",
        blurb = "Fixes a serious problem. Update as soon as possible.",
        interrupts = true,
    ),
    ;

    /** Higher is more urgent; used to keep the loudest unread release. */
    val rank: Int get() = ordinal

    companion object {
        /**
         * The marker line the release workflow writes, and the one place its
         * spelling lives. Kept in one constant so the workflow and the parser
         * cannot drift apart.
         */
        const val MARKER = "Velthy-Severity"

        /**
         * The severity named by [notes], or [NORMAL] when none is stated.
         *
         * Matches `Velthy-Severity: critical` case-insensitively and tolerates
         * surrounding whitespace and markdown emphasis, because the line is
         * written by a workflow and read by a parser that must not be brittle
         * about a stray `*` or `_`.
         */
        fun fromReleaseNotes(notes: String?): UpdateSeverity {
            if (notes.isNullOrBlank()) return NORMAL
            val match = Regex(
                """$MARKER\s*:\s*[*_`\s]*([A-Za-z]+)""",
                RegexOption.IGNORE_CASE,
            ).find(notes) ?: return NORMAL
            return when (match.groupValues[1].lowercase()) {
                "critical" -> CRITICAL
                "important" -> IMPORTANT
                else -> NORMAL
            }
        }
    }
}
