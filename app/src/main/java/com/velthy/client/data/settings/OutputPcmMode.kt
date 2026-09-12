package com.velthy.client.data.settings

/**
 * What the app asks Android to open its output as.
 *
 * A *request*, not a promise: the framework owns the final mixer and DAC
 * decision, and a route that cannot carry 32-bit float gets PCM 16 back
 * whatever this says. See [com.velthy.client.playback.AudioOutputPolicy] for
 * when the request is even allowed to be made.
 */
enum class OutputPcmMode(val label: String, val detail: String) {
    PCM_16(
        "16-bit PCM",
        "Safe on every route, including the phone's own speaker",
    ),
    FLOAT_32(
        "32-bit float",
        "Only used on an external route that reports float support",
    ),
    ;

    companion object {
        /** The stored value, or the default when nothing valid is stored. */
        fun from(name: String?): OutputPcmMode =
            entries.firstOrNull { it.name == name } ?: PCM_16
    }
}
