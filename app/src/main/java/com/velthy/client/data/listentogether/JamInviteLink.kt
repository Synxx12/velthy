package com.velthy.client.data.listentogether

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * An invite as it was read off a link: the code, and the server it points at
 * when the link names one.
 *
 * The server half is what makes a self-hosted party shareable: somebody
 * running their own server can hand out a link that carries it, and the
 * listener's app goes there rather than to the built-in one. Absent means
 * "whichever server this install is pointed at".
 */
data class ParsedJamInvite(
    val code: String,
    val serverUrl: String? = null,
)

/** Relays a Listen Together invite from [com.velthy.client.MainActivity] to Compose. */
object JamInviteLink {

    const val ORIGIN = "https://velthy.my.id"

    private const val EXTRA_CONSUMED = "velthy.jamInviteConsumed"
    private const val HOST = "velthy.my.id"
    private const val CUSTOM_SCHEME = "velthy"
    private const val CUSTOM_HOST = "join"

    private val _pending = MutableStateFlow<ParsedJamInvite?>(null)
    val pending: StateFlow<ParsedJamInvite?> = _pending.asStateFlow()

    /** Reads a web invite from a cold launch or a new intent on the existing task. */
    fun consume(intent: Intent?): Boolean {
        if (
            intent == null ||
            intent.action != Intent.ACTION_VIEW ||
            intent.getBooleanExtra(EXTRA_CONSUMED, false)
        ) return false

        val invite = parseInvite(intent.dataString) ?: return false
        intent.putExtra(EXTRA_CONSUMED, true)
        _pending.value = invite
        return true
    }

    fun handled() {
        _pending.value = null
    }

    /** Returns the normalized party code only for the public invite URL shape. */
    fun parse(value: String?): String? = parseInvite(value)?.code

    /**
     * Parses an incoming invite.
     *
     * Two forms carry it, and both have to work: `velthy://join/ABC123` from a
     * device that has the app, and `https://velthy.my.id/join/ABC123` for
     * anywhere a custom scheme would not be clickable. Either may carry a
     * `?server=` naming the party server the code lives on.
     */
    fun parseInvite(value: String?): ParsedJamInvite? {
        val uri = runCatching { URI(value ?: return null) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val query = uri.rawQuery
        val server = extractQueryParam(query, "server")?.let { sanitizeServerUrl(it) }

        // 1. Custom scheme: velthy://join/<CODE> or velthy://join?code=<CODE>
        if (scheme == CUSTOM_SCHEME && host == CUSTOM_HOST) {
            val pathPart = uri.path.orEmpty().trim('/').takeIf { it.isNotBlank() }
            val candidate = pathPart ?: extractQueryParam(query, "code") ?: return null
            val code = cleanCode(candidate) ?: return null
            return ParsedJamInvite(code = code, serverUrl = server)
        }

        // 2. Official web domain: https://velthy.my.id/join/<CODE>
        if (scheme == "https" && host == HOST) {
            val match = INVITE_PATH.matchEntire(uri.path.orEmpty()) ?: return null
            val code = match.groupValues[1].uppercase()
            return ParsedJamInvite(code = code, serverUrl = server)
        }

        return null
    }

    /**
     * The link to hand somebody else.
     *
     * The `velthy://` form comes first because it is the one that opens the app
     * directly, on a device that has it installed. The web address is the
     * fallback a chat client will still make clickable, and it carries the same
     * code in the same place.
     *
     * [customServer] rides along only when it is the server the party is
     * actually on: an invite that names the built-in address is the ordinary
     * case, and putting it in every link would leak it into every chat.
     */
    fun url(code: String, customServer: String? = null): String {
        val base = customServer?.trim()?.trimEnd('/')
        return if (!base.isNullOrBlank() && !base.equals(ORIGIN, ignoreCase = true)) {
            "$base/join/${code.uppercase()}"
        } else {
            "$ORIGIN/join/${code.uppercase()}"
        }
    }

    /** The same invite in the app's own scheme, for a device that has Velthy. */
    fun schemeUrl(code: String, customServer: String? = null): String {
        val normalizedCode = code.uppercase()
        val base = customServer?.trim()?.trimEnd('/')
        return if (!base.isNullOrBlank()) {
            val encoded = runCatching { URLEncoder.encode(base, "UTF-8") }.getOrDefault(base)
            "velthy://join/$normalizedCode?server=$encoded"
        } else {
            "velthy://join/$normalizedCode"
        }
    }

    /** The same invite as a plain web address, for anywhere a custom scheme is not clickable. */
    fun webUrl(code: String, customServer: String? = null): String {
        val base = customServer?.trim()?.trimEnd('/')
        return if (!base.isNullOrBlank() && !base.equals(ORIGIN, ignoreCase = true)) {
            "$base/join/${code.uppercase()}"
        } else {
            "$ORIGIN/join/${code.uppercase()}"
        }
    }

    private fun cleanCode(raw: String): String? {
        val cleaned = raw.filter { it.isLetterOrDigit() }.uppercase()
        return if (cleaned.length == ListenTogether.CODE_LENGTH) cleaned else null
    }

    private fun extractQueryParam(query: String?, paramName: String): String? {
        if (query.isNullOrBlank()) return null
        return query.split('&').asSequence()
            .map { it.split('=', limit = 2) }
            .firstOrNull { it.isNotEmpty() && it[0].equals(paramName, ignoreCase = true) }
            ?.getOrNull(1)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
    }

    /**
     * A `?server=` value, or null when it is not usable.
     *
     * Deliberately loose: this is a value that arrived in a link somebody else
     * wrote, so it is *validated* before use rather than trusted — the address
     * is normalised and probed when the invite is acted on. What it must not do
     * is crash or be carried along when it is obviously not an address.
     */
    private fun sanitizeServerUrl(raw: String?): String? {
        val trimmed = raw?.trim()?.trimEnd('/') ?: return null
        if (trimmed.isBlank()) return null
        if (trimmed.any { it.isWhitespace() }) return null
        val withScheme = if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed
        } else {
            "https://$trimmed"
        }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank()) return null
        return withScheme
    }

    private val INVITE_PATH = Regex("""/join/([A-Za-z0-9]{${ListenTogether.CODE_LENGTH}})""")
}
