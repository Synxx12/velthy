package com.velthy.client.data.settings

/**
 * CPU budget for Automix's background analysis, not for its audible mix.
 *
 * The two on-device models that prepare a transition — the beat tracker and the
 * vocal tracker — are the only part of playback that will happily eat every
 * core a phone has, and they run while a track is playing. This is the dial for
 * that: how many threads each may take, and therefore how much of the device is
 * left for the decoder, the interface, and anything else the listener is doing.
 *
 * A budget rather than a speed setting because it is spent either way: fewer
 * threads means the analysis of the *next* track may still be finishing when
 * this one ends, which costs a less considered transition, not a broken one.
 *
 * The numbers are BitChord's, including the default of [BALANCED] — see
 * [com.velthy.client.data.settings.AppSettings.automixPerformanceMode].
 */
enum class AutomixPerformanceMode(val inferenceThreads: Int) {
    EFFICIENT(1),
    BALANCED(2),
    PERFORMANCE(4),
    ;

    /** What the settings row shows as the current choice. */
    val label: String
        get() = when (this) {
            EFFICIENT -> "Efficient"
            BALANCED -> "Balanced"
            PERFORMANCE -> "Performance"
        }

    /** The line under it, saying what the choice costs. */
    val detail: String
        get() = when (this) {
            EFFICIENT -> "1 thread · lowest heat and battery use · analysis may take longer"
            BALANCED -> "2 threads · recommended balance of speed, heat and battery"
            PERFORMANCE -> "4 threads · fastest analysis · higher heat and battery use"
        }
}
