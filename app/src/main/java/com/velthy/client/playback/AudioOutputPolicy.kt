package com.velthy.client.playback

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.velthy.client.data.settings.OutputPcmMode

/**
 * Decides whether Media3 may open a PCM-float AudioTrack.
 *
 * Float output is not safe on every Android route. Some OEM speaker paths
 * accept the AudioTrack and then convert it to PCM 16 in AudioFlinger, and on
 * affected Samsung FLAC decoders that combination also produces timestamp
 * discontinuities and severely distorted output. So it is allowed only when all
 * three hold: the listener asked for float, the route is an external one this
 * app actually claimed, and that route advertises float support.
 */
internal object AudioOutputPolicy {

    fun shouldUseFloatOutput(
        requestedMode: OutputPcmMode,
        isPreferredUsbRoute: Boolean,
        advertisesPcmFloat: Boolean,
    ): Boolean = requestedMode == OutputPcmMode.FLOAT_32 &&
        isPreferredUsbRoute &&
        advertisesPcmFloat

    /**
     * Whether this device's FLAC decoder is the vendor one that mangles PCM
     * float.
     *
     * Asked of the codec list rather than of a track: the sink is built before
     * any track is known, so the only thing that can be checked in time is what
     * the device ships.
     */
    @UnstableApi
    fun hasUnsafeFloatFlacDecoder(): Boolean = runCatching {
        MediaCodecUtil.getDecoderInfos("audio/flac", false, false)
            .any { isUnsafeFloatFlacDecoder(it.name) }
    }.getOrDefault(false)

    /** Samsung's vendor FLAC decoder emits invalid timestamps with PCM float. */
    fun isUnsafeFloatFlacDecoder(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized == "c2.sec.flac.decoder" ||
            (normalized.startsWith("omx.sec.") && normalized.contains("flac"))
    }
}
