package com.velthy.client.playback

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What an intent from outside the app turned out to be asking for. */
sealed interface LinkRequest {
    /** A song, by video id — `watch?v=`, a `youtu.be` short link, a Short. */
    data class Track(val videoId: String) : LinkRequest

    /** An album, playlist or artist page, by browse id. */
    data class Page(val browseId: String) : LinkRequest

    /**
     * Words rather than an id: "play Blinding Lights", or a shared search URL.
     *
     * [play] separates the two. A spoken request is an instruction — it should
     * start the best match, not leave a list on screen for someone whose phone
     * is in their pocket — while a search *link* is a page somebody meant to
     * show you.
     */
    data class Search(val query: String, val play: Boolean) : LinkRequest

    /** "Play music", with nothing said about what. */
    data object Resume : LinkRequest
}

/**
 * Links and voice requests handed to Velthy from elsewhere on the device.
 *
 * The same relay [PlayerDeepLink] is, and for the same reason: what has to
 * happen — start a queue, push a page, run a search — is all inside
 * `VelthyApp`'s composition, which [MainActivity][com.velthy.client.MainActivity]
 * has no handle on. The activity reads the intent, this holds the answer, and
 * the composition serves it once its controller and view model exist.
 */
object MusicLink {

    /**
     * Marks an intent as already read.
     */
    private const val EXTRA_CONSUMED = "velthy.linkConsumed"

    private val _pending = MutableStateFlow<LinkRequest?>(null)

    /** The outstanding request, or null. Cleared by [handled]. */
    val pending: StateFlow<LinkRequest?> = _pending.asStateFlow()

    /** Reads an incoming intent, and reports whether it carried a request. */
    fun consume(intent: Intent?): Boolean {
        if (intent == null || intent.getBooleanExtra(EXTRA_CONSUMED, false)) return false
        val request = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let(::parse)
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let(::firstUrl)
                ?.let { parse(Uri.parse(it)) }
            // The assistant's "play <something>". An empty query is the whole
            // point of the Resume case: "play music" names nothing, and the
            // useful answer is to carry on with what was already on.
            MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {
                val query = intent.getStringExtra(SearchManager.QUERY).orEmpty().trim()
                if (query.isEmpty()) LinkRequest.Resume else LinkRequest.Search(query, play = true)
            }
            else -> null
        } ?: return false
        intent.putExtra(EXTRA_CONSUMED, true)
        _pending.value = request
        return true
    }

    /**
     * Called once the request has actually been acted on.
     */
    fun handled() {
        _pending.value = null
    }

    /**
     * What a YouTube or YouTube Music URL points at, or null for one this app
     * has nothing to show for.
     */
    fun parse(uri: Uri): LinkRequest? {
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return null
        val segments = uri.pathSegments.orEmpty()
        if (host == "youtu.be") {
            return segments.firstOrNull()?.let(::track)
        }
        if (host != "youtube.com" && host != "music.youtube.com" && host != "m.youtube.com") {
            return null
        }
        val list = uri.getQueryParameter("list")?.trim().orEmpty()
        return when (segments.firstOrNull()) {
            "watch" -> uri.getQueryParameter("v")?.let(::track) ?: playlist(list)
            "playlist" -> playlist(list)
            "shorts", "embed", "v" -> segments.getOrNull(1)?.let(::track)
            "channel", "browse" -> segments.getOrNull(1)?.takeIf { it.isNotBlank() }
                ?.let(LinkRequest::Page)
            "search" -> uri.getQueryParameter("q")?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { LinkRequest.Search(it, play = false) }
            else -> list.takeIf { it.isNotEmpty() }?.let { playlist(it) }
        }
    }

    private fun track(videoId: String): LinkRequest.Track? =
        videoId.trim().takeIf { it.isNotEmpty() }?.let(LinkRequest::Track)

    private fun playlist(listId: String): LinkRequest.Page? {
        if (listId.isEmpty()) return null
        return LinkRequest.Page(if (listId.startsWith("VL")) listId else "VL$listId")
    }

    private fun firstUrl(text: String): String? =
        URL_IN_TEXT.find(text)?.value

    private val URL_IN_TEXT = Regex("""https?://\S+""")
}
