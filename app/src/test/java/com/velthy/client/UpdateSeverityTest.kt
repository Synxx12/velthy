package com.velthy.client

import com.velthy.client.data.UpdateSeverity
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateSeverityTest {

    @Test
    fun parses_explicit_critical_marker() {
        val notes = "<!-- Velthy-Severity: critical -->\n\n## Fix crash"
        assertEquals(UpdateSeverity.CRITICAL, UpdateSeverity.fromReleaseNotes(notes))
    }

    @Test
    fun parses_explicit_important_marker() {
        val notes = "<!-- Velthy-Severity: important -->\n\n## Important security fix"
        assertEquals(UpdateSeverity.IMPORTANT, UpdateSeverity.fromReleaseNotes(notes))
    }

    @Test
    fun parses_explicit_normal_marker() {
        val notes = "<!-- Velthy-Severity: normal -->\n\n## Minor UI tweak"
        assertEquals(UpdateSeverity.NORMAL, UpdateSeverity.fromReleaseNotes(notes))
    }

    @Test
    fun falls_back_to_normal_when_no_marker() {
        val notes = "## Just some regular release notes"
        assertEquals(UpdateSeverity.NORMAL, UpdateSeverity.fromReleaseNotes(notes))
    }

    @Test
    fun handles_null_and_blank_notes() {
        assertEquals(UpdateSeverity.NORMAL, UpdateSeverity.fromReleaseNotes(null))
        assertEquals(UpdateSeverity.NORMAL, UpdateSeverity.fromReleaseNotes(""))
        assertEquals(UpdateSeverity.NORMAL, UpdateSeverity.fromReleaseNotes("   "))
    }
}
