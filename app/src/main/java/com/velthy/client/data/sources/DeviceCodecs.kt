package com.velthy.client.data.sources

import android.media.MediaCodecList
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.velthy.client.data.TrackLog
import java.util.Locale

/**
 * What this particular phone can actually decode, asked once and remembered.
 *
 * One question so far, and it is the one the rest of the app is otherwise free
 * to ignore all the way to silence: **Dolby Atmos**. E-AC-3 is licensed, so its
 * decoder ships with the *vendor* image rather than with Android — present on
 * some flagships, absent on plenty of devices that look identical from up here.
 * Nothing else in the app ever asked, so an Atmos rendition was handed to the
 * player on every device alike.
 *
 * Asked here, before the URL is returned, rather than left to the player,
 * because of how badly the player fails at it. A format no renderer supports is
 * not reliably an *error*: `DefaultTrackSelector` declines to select a track
 * whose support is `FORMAT_UNSUPPORTED_SUBTYPE`, the period then ends with no
 * renderer enabled, and the queue moves on — a song that skipped itself
 * instantly, with no exception for the service to recover from and nothing on
 * screen to explain it.
 *
 * ### Getting the answer right
 *
 * The verdict has to be the *renderer's* verdict, because the renderer is who
 * acts on it. So [MediaCodecUtil] is asked first: it is precisely what
 * `MediaCodecSelector` hands `MediaCodecAudioRenderer`, including Media3's
 * per-device workarounds and its blocklists for decoders that are advertised
 * and don't work.
 *
 * Both mime types are asked for. A decoder that declares plain `audio/eac3` can
 * carry a JOC stream — the height objects are dropped and what comes out is the
 * 5.1 core downmixed, which is a lesser render but not a failure — and Media3
 * will fall back to one for exactly that reason. Asking for only the JOC mime
 * would refuse Atmos on every device that ships the ordinary E-AC-3 decoder.
 *
 * [MediaCodecList] is then a backstop rather than the primary answer, for the
 * case where the Media3 query throws. `REGULAR_CODECS` deliberately, not
 * `ALL_CODECS`: the extra entries in the full list exist only for specific
 * configurations, and counting them is how you end up saying yes to a decoder
 * the renderer will not be given.
 */
object DeviceCodecs {

    private const val TAG = "Velthy"

    /**
     * E-AC-3 JOC, and the plain E-AC-3 core it degrades to. Either decoder is
     * enough to say yes — see the note on fallback above.
     */
    private val DOLBY_MIMES = listOf("audio/eac3-joc", "audio/eac3")

    @Volatile
    private var probed: Boolean? = null

    /**
     * Whether an E-AC-3 (JOC) stream would find a decoder on this device.
     *
     * Answered once per process and cached: the codec list cannot change while
     * the app is running, and the query is not free — a `MediaCodecList` walk
     * costs tens of milliseconds, on a path that is otherwise holding up audio.
     */
    val playsDolbyAtmos: Boolean
        get() = probed ?: probe().also { probed = it }

    /**
     * Yes on anything that cannot be established.
     *
     * A probe that fails outright is exotic and an unsupported device is not,
     * but the cost of the two mistakes is not symmetric: guessing "no" on a
     * capable flagship silently drops it from the tier it is paying for and
     * greys out the switch that would restore it, while guessing "yes" only
     * restores the behaviour that shipped before any of this existed. So the
     * uncertain case keeps the old behaviour.
     */
    private fun probe(): Boolean {
        val viaMedia3 = media3Decoders()
        if (viaMedia3 != null) {
            TrackLog.d(TAG, "Dolby Atmos decoders (Media3): ${viaMedia3.ifEmpty { "none" }}")
            return viaMedia3.isNotEmpty()
        }
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                !info.isEncoder && info.supportedTypes.any {
                    it.lowercase(Locale.ROOT) in DOLBY_MIMES
                }
            }.map { it.name }
        }.onSuccess {
            TrackLog.d(TAG, "Dolby Atmos decoders (platform): ${it.ifEmpty { "none" }}")
        }.map {
            it.isNotEmpty()
        }.getOrElse {
            TrackLog.w(TAG, "could not read the codec list; assuming Dolby Atmos plays — ${it.message}")
            true
        }
    }

    /**
     * The decoders Media3 itself would offer the renderer for an Atmos stream,
     * by name, or null if the query failed and the caller should fall back to
     * the platform's own list.
     *
     * Isolated so the unstable-API surface is one function wide.
     */
    @UnstableApi
    private fun media3Decoders(): List<String>? = runCatching {
        DOLBY_MIMES.flatMap { MediaCodecUtil.getDecoderInfos(it, false, false) }
            .map { it.name }
            .distinct()
    }.getOrElse {
        TrackLog.w(TAG, "Media3 could not enumerate Dolby decoders — ${it.message}")
        null
    }
}
