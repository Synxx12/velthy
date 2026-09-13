package com.velthy.client.playback

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Sleep timer. Holds a deadline; [PlaybackService] watches it and pauses
 * playback once it passes.
 *
 * A deadline rather than a countdown: nothing has to tick for the timer to
 * stay accurate, so it survives the player UI being dismissed, and any
 * observer can work out how long is left for itself. elapsedRealtime, not
 * wall clock, so changing the system time can't cut a timer short.
 */
object SleepTimer {

    /** Deadline on [SystemClock.elapsedRealtime], or null when no timer is set. */
    val deadline = MutableStateFlow<Long?>(null)

    /**
     * The preset that was chosen, so the picker can tick it.
     *
     * Null when off — and null once [extend] has added time, because no rung of
     * the picker describes that wait any more. Which is why "is a timer armed"
     * is asked of [deadline] and never of this.
     */
    val minutes = MutableStateFlow<Int?>(null)

    /**
     * Pause when the current track ends instead of after a fixed wait.
     *
     * Deliberately not expressed as a deadline of "duration minus position":
     * seeking, crossfade and a queue that reorders itself would all leave that
     * number wrong, whereas the track ending is an event the player reports.
     */
    val afterTrack = MutableStateFlow(false)

    /** Durations offered in the picker. */
    val PRESETS = listOf(15, 30, 45, 60)

    /** Whether any kind of timer is currently armed. */
    val isRunning: Boolean get() = deadline.value != null || afterTrack.value

    fun start(minutes: Int) {
        afterTrack.value = false
        this.minutes.value = minutes
        deadline.value = SystemClock.elapsedRealtime() + minutes * 60_000L
    }

    /**
     * Pushes the deadline [minutes] further out.
     *
     * Counted from the deadline rather than from now, so adding five minutes to
     * a timer with twelve left leaves seventeen — and a timer that has already
     * reached zero starts again from this moment rather than landing in the
     * past.
     *
     * [PRESETS] is deliberately left alone and [minutes] is cleared: what the
     * picker offers and what the clock says are different questions, and after
     * this neither rung describes the wait. An armed timer is a set [deadline],
     * so everything that reads one keeps working.
     */
    fun extend(minutes: Int) {
        val now = SystemClock.elapsedRealtime()
        val base = deadline.value?.takeIf { it > now } ?: now
        this.minutes.value = null
        deadline.value = base + minutes * 60_000L
    }

    /** Pause once the track playing right now finishes. */
    fun startAfterTrack() {
        minutes.value = null
        deadline.value = null
        afterTrack.value = true
    }

    fun cancel() {
        minutes.value = null
        deadline.value = null
        afterTrack.value = false
    }

    /** How long is left, or null when no timer is running. */
    fun remainingMs(): Long? =
        deadline.value?.let { (it - SystemClock.elapsedRealtime()).coerceAtLeast(0L) }

    /**
     * "m:ss" for a stretch of millis — the shape every countdown readout on this
     * timer speaks, so the sheet, the picker and anything added later agree on
     * what is left rather than each rounding it their own way.
     */
    fun clock(ms: Long): String {
        val seconds = (ms / 1000L).coerceAtLeast(0L)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }
}
