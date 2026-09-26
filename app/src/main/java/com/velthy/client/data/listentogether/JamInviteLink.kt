package com.velthy.client.data.listentogether

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI

/** Relays a Listen Together invite from [com.velthy.client.MainActivity] to Compose. */
object JamInviteLink {

    const val ORIGIN = "https://velthy.my.id"

    private const val EXTRA_CONSUMED = "velthy.jamInviteConsumed"
    private const val HOST = "velthy.my.id"

    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    /** Reads a web invite from a cold launch or a new intent on the existing task. */
    fun consume(intent: Intent?): Boolean {
        if (
            intent == null ||
            intent.action != Intent.ACTION_VIEW ||
            intent.getBooleanExtra(EXTRA_CONSUMED, false)
        ) return false

        val code = parse(intent.dataString) ?: return false
        intent.putExtra(EXTRA_CONSUMED, true)
        _pending.value = code
        return true
    }

    fun handled() {
        _pending.value = null
    }

    /**
     * Returns the normalized party code for either invite shape.
     *
     * Two forms carry it, and both have to work: `velthy://join/ABC123` from a
     * device that has the app, and `https://velthy.my.id/join/ABC123` for
     * anywhere a custom scheme would not be clickable. The path is the same in
     * both, so the code is read the same way once the scheme check is out of
     * the way.
     */
    fun parse(value: String?): String? {
        val uri = runCatching { URI(value ?: return null) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        val isAppLink = scheme == "velthy" && host == "join"
        val isWebLink = scheme == "https" && host == HOST
        if (!isAppLink && !isWebLink) return null

        // The app form puts the code where a web form puts its first path
        // segment, so the two are read from the same place.
        val path = if (isAppLink) "/join${uri.path.orEmpty()}" else uri.path.orEmpty()
        val match = INVITE_PATH.matchEntire(path) ?: return null
        return match.groupValues[1].uppercase()
    }

    /**
     * The link to hand somebody else.
     *
     * The `velthy://` form comes first because it is the one that opens the app
     * directly, on a device that has it installed. The web address is the
     * fallback a chat client will still make clickable, and it carries the same
     * code in the same place.
     */
    fun url(code: String): String = "velthy://join/${code.uppercase()}"

    /** The same invite as a plain web address, for anywhere a custom scheme is not clickable. */
    fun webUrl(code: String): String = "$ORIGIN/join/${code.uppercase()}"

    private val INVITE_PATH = Regex("""/join/([A-Za-z0-9]{${ListenTogether.CODE_LENGTH}})""")
}
