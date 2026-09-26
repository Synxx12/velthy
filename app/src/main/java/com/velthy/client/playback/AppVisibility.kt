package com.velthy.client.playback

/**
 * Whether any part of this app is actually on screen.
 *
 * A plain flag rather than a flow because the only reader is a loop that wants
 * the current answer, not a history of it: the flag is written from the
 * activity's lifecycle on the main thread and read from the playback service's
 * own sampling loop.
 *
 * This is deliberately *not* the service's own lifecycle. A foreground service
 * is expected to run with the screen off — that is the entire reason it exists —
 * so nothing the service can observe about itself answers the question. The
 * activity is the only component that can see a window, and it says.
 *
 * Defaults to true: a cold start, where the service is up before the activity's
 * first resume, should behave exactly as it always did rather than briefly
 * dropping to the idle rate.
 */
object AppVisibility {

    /** The current answer, for a polling loop. */
    @Volatile
    var isVisible: Boolean = true
        private set

    /** Called by the activity as it is resumed and stopped. */
    fun set(visible: Boolean) {
        isVisible = visible
    }
}
