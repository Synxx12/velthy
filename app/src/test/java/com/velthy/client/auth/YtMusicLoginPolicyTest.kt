package com.velthy.client.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtMusicLoginPolicyTest {

    @Test
    fun `accepts the exact music origin`() {
        assertTrue(isMusicSessionUrl("https://music.youtube.com"))
    }

    @Test
    fun `accepts music origin with path and query`() {
        assertTrue(isMusicSessionUrl("https://music.youtube.com/watch?v=abc"))
    }

    @Test
    fun `rejects a lookalike host`() {
        assertFalse(isMusicSessionUrl("https://music.youtube.com.evil.example/"))
    }

    @Test
    fun `rejects other youtube hosts`() {
        assertFalse(isMusicSessionUrl("https://www.youtube.com/"))
    }

    @Test
    fun `rejects a missing url`() {
        assertFalse(isMusicSessionUrl(null))
    }
}
