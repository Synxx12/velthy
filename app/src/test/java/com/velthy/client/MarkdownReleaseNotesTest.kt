package com.velthy.client

import com.velthy.client.ui.components.sanitizeReleaseNotes
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownReleaseNotesTest {

    @Test
    fun strips_html_comments_from_markdown() {
        val raw = "<!-- Velthy-Severity: critical -->\n\n## 🎵 Velthy v1.4.6.5\n\n- Fix bug"
        assertEquals("## 🎵 Velthy v1.4.6.5\n\n- Fix bug", sanitizeReleaseNotes(raw))
    }

    @Test
    fun strips_multiline_html_comments() {
        val raw = "<!--\n multi-line\n comment\n -->\n# Title"
        assertEquals("# Title", sanitizeReleaseNotes(raw))
    }

    @Test
    fun trims_blank_input() {
        assertEquals("", sanitizeReleaseNotes(""))
        assertEquals("", sanitizeReleaseNotes("   \n\n  "))
    }
}
