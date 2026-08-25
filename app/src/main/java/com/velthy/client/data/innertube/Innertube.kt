package com.velthy.client.data.innertube

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import com.velthy.client.data.model.LikeStatus
import com.velthy.client.data.model.PlaylistPrivacy
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.security.MessageDigest

/**
 * Minimal Innertube (youtubei) client.
 *
 * Two kinds of client identity, for different reasons:
 *
 *  - **WEB_REMIX** against music.youtube.com for browse/search/library. It
 *    returns the full YT Music shelf layout and honours the signed-in session.
 *
 *  - **A device client** for the `player` endpoint, chosen per call. Which
 *    ones Google answers changes without notice, so [player] takes the
 *    identity as an argument and [StreamResolver] walks a list of them rather
 *    than betting the app on any single one. See [PlayerClient].
 *
 * Authenticated requests are signed with Google's SAPISIDHASH scheme derived
 * from the stored cookie; no long-lived token is ever minted or stored.
 */
object Innertube {

    private const val MUSIC_BASE = "https://music.youtube.com/youtubei/v1"
    private const val YT_BASE = "https://www.youtube.com/youtubei/v1"
    private const val MUSIC_ORIGIN = "https://music.youtube.com"

    private const val WEB_REMIX_VERSION = "1.20260707.12.00"
    private const val WEB_REMIX_CLIENT_ID = "67"

    private const val TAG = "Musique"

    /** Session cookie captured by the login WebView; null = browse as guest. */
    var cookie: String? = null

    /**
     * Google's per-session visitor id.
     *
     * Far more load-bearing than "an id for stats". A `player` request that
     * carries no visitor id is treated as a client with no session at all, and
     * Google answers it in one of two ways: the honest one, `LOGIN_REQUIRED` /
     * "Sign in to confirm you're not a bot", or the quiet one — a perfectly
     * ordinary-looking response whose stream URLs serve a byte to anything that
     * asks and then refuse every real read with 403. The second is what
     * "it loads and then doesn't play" is made of.
     *
     * So it is fetched deliberately by [ensureVisitorData] rather than being
     * hoped for: browse responses carry one only sometimes, and a session that
     * never happened to see one would silently never play anything.
     */
    @Volatile
    private var visitorData: String? = null

    /**
     * A visitor id for this session, minting one if there isn't one yet.
     *
     * @param refresh discard the current id and take a fresh one — worth doing
     *   exactly once when a request comes back accusing us of being a bot,
     *   since an id can be burned while the session around it is fine.
     */
    suspend fun ensureVisitorData(refresh: Boolean = false): String? {
        if (!refresh && visitorData != null) return visitorData
        runCatching { fetchVisitorData() }
            .onFailure { Log.w(TAG, "could not mint a visitor id: ${it.message}") }
            .getOrNull()
            ?.let { visitorData = it }
        return visitorData
    }

    /**
     * The service worker bootstrap the web player loads before anything else,
     * which is where a fresh visitor id comes from without needing a page.
     * It answers with an anti-hijacking prefix and then plain nested arrays,
     * the second string in the top array being the id.
     */
    private suspend fun fetchVisitorData(): String? {
        val body = client.get("https://www.youtube.com/sw.js_data") {
            header("User-Agent", WEB_USER_AGENT)
        }.bodyAsText()
        val payload = Json.parseToJsonElement(body.substringAfter("\n", body.drop(5)))
        return findVisitorData(payload)
    }

    private fun findVisitorData(element: JsonElement): String? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull { findVisitorData(it) }
        is JsonPrimitive -> element.contentOrNull?.takeIf { VISITOR_DATA.matches(it) }
        else -> null
    }

    /** Protobuf-in-base64; always this shape, and nothing else in there is. */
    private val VISITOR_DATA = Regex("""Cg[A-Za-z0-9_%-]{40,}""")

    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true }

    /** See [postPlayer] — the per-request ceiling on the walk's hot path. */
    private const val PLAYER_TIMEOUT_MS = 6_000L

    private val client = HttpClient(OkHttp) {
        // Same OkHttp instance ExoPlayer streams through — see Http.
        engine { preconfigured = com.velthy.client.data.Http.client }
        install(ContentNegotiation) { json(json) }
        // Without this the only bound is OkHttp's own read timeout, and the
        // failure it raises reads as "Socket timeout has expired […]
        // socket_timeout=unknown" — Ktor reporting a limit it was never told.
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
        }
        expectSuccess = true
    }

    /**
     * Runs [block], giving transport failures another go before letting them
     * reach the caller.
     *
     * A connection reset on mobile data is weather, not information: the
     * request was fine and asking again generally answers. That matters most
     * on a shared connection pool, where a socket torn down under one request
     * — an abandoned search, a network handover — surfaces as
     * "Software caused connection abort" on whichever request picked that
     * connection up next, which had nothing to do with it.
     *
     * Only transport failures. An HTTP error status is an answer, and
     * repeating the question won't change it. Cancellation isn't caught at
     * all: [delay] throws when the coroutine is cancelled, so a search the
     * user has typed past stops here instead of retrying on behalf of a query
     * nobody is waiting for.
     */
    private suspend fun <T> withRetry(attempts: Int = 3, block: suspend () -> T): T {
        var backoff = 500L
        repeat(attempts - 1) {
            try {
                return block()
            } catch (e: HttpRequestTimeoutException) {
                // Not weather, and not worth repeating. A timeout is this app's
                // own decision that the request had long enough — so trying it
                // again cannot learn anything the first attempt didn't, and the
                // cost is multiplied rather than shared: [HttpRequestTimeoutException]
                // is an [IOException], so before this branch existed every timed-out
                // `player` call was quietly attempted three times. That turned a
                // six-second ceiling into a nineteen-second one on a walk of
                // seven clients, which is worse than the unbounded call the
                // ceiling was added to prevent. Give up on this client and let
                // the caller move to the next one.
                Log.d(TAG, "not retrying, request timed out: ${e.message}")
                throw e
            } catch (e: IOException) {
                Log.d(TAG, "retrying: ${e.message}")
            }
            delay(backoff)
            backoff *= 2
        }
        return block()
    }

    // ---- Public API ---------------------------------------------------------

    suspend fun browse(browseId: String, params: String? = null): JsonObject =
        postMusic("browse") {
            put("browseId", browseId)
            params?.let { put("params", it) }
        }

    /**
     * The next page of a paged browse response — playlists and library feeds
     * come back roughly 100 rows at a time. YouTube Music takes the token as
     * query parameters rather than in the body, and answers with a bare
     * continuation envelope carrying the same row renderers.
     */
    suspend fun browseContinuation(token: String): JsonObject = postMusic(
        endpoint = "browse",
        // The web client passes the token in the body and the older query-string
        // form is still honoured; both are sent so either is enough.
        query = mapOf("ctoken" to token, "continuation" to token, "type" to "next"),
    ) {
        put("continuation", token)
    }

    /** Signed-in profile: display name, email/handle and avatar. */
    suspend fun accountMenu(): JsonObject = postMusic("account/account_menu") {}

    /**
     * The watch queue that YouTube Music would play after [videoId] — the
     * "RDAMVM" radio mix. Used to keep AutoPlay going past the last track.
     */
    suspend fun next(videoId: String): JsonObject = postMusic("next") {
        put("videoId", videoId)
        put("playlistId", "RDAMVM$videoId")
        put("isAudioOnly", true)
    }

    suspend fun search(query: String, params: String? = null): JsonObject =
        postMusic("search") {
            put("query", query)
            params?.let { put("params", it) }
        }

    /**
     * The `player` response for [videoId] as seen by [client] — the audio
     * formats and whatever it takes to unlock them.
     *
     * The single cheapest thing this app does to start a track: one POST,
     * answered in a few hundred milliseconds, against an endpoint that carries
     * no HTML and is not rate-shaped the way the watch page is.
     *
     * [signatureTimestamp] is required by the clients whose formats come back
     * ciphered ([PlayerClient.needsSignatureTimestamp]) and ignored by the
     * rest; it is read out of YouTube's own player JavaScript.
     *
     * @throws UnplayableException when the track is refused rather than
     *   missing — a region block, a takedown, or the client being turned away.
     *   Callers walk on to the next client on the strength of that distinction.
     */
    suspend fun player(
        videoId: String,
        client: PlayerClient,
        signatureTimestamp: Int? = null,
        authenticated: Boolean = false,
    ): JsonObject {
        val response = postPlayer(videoId, client, signatureTimestamp, authenticated)

        val status = response["playabilityStatus"]?.jsonObject
            ?.get("status")?.jsonPrimitive?.content
        if (status != null && status != "OK") {
            val reason = response["playabilityStatus"]?.jsonObject
                ?.get("reason")?.jsonPrimitive?.content
            throw UnplayableException(reason ?: status)
        }
        return response
    }

    class UnplayableException(private val reason: String) :
        IllegalStateException("Track unavailable: $reason") {

        /**
         * Whether this is Google doubting the client rather than the track
         * being unavailable. Worth a fresh visitor id and another go; a real
         * region block or takedown is not.
         */
        val looksLikeBotCheck: Boolean
            get() = reason.contains("bot", ignoreCase = true) ||
                reason.contains("unusual traffic", ignoreCase = true) ||
                reason.contains("sign in", ignoreCase = true)
    }

    /** The stats endpoints a player response nominates for one playback. */
    data class PlaybackTracking(
        val playbackUrl: String,
        val watchtimeUrl: String?,
        val client: PlayerClient = PlayerClient.ANDROID_MUSIC,
    )

    /**
     * Player response fetched *with* the session cookie, purely to read back
     * `playbackTracking` — [player] deliberately skips auth so its device
     * clients are answered at all, so it never sees this block. Null for
     * guests: there's no account history to update.
     *
     * Matches InnerTune / ArchiveTune standards: queries ANDROID_MUSIC (unciphered
     * YouTube Music Android client) first, falling back to WEB_REMIX or IOS if needed.
     */
    /**
     * Finds the playback and watchtime tracking URLs YouTube gave this track.
     *
     * In accordance with SpatialFlow & InnerTune standards: queries WEB_REMIX
     * first, and falls back to ANDROID_MUSIC, IOS, or authentic direct URL templates.
     * Rewrites s.youtube.com -> music.youtube.com for 100% reliable history attribution.
     */
    suspend fun playbackTracking(videoId: String, cpn: String): PlaybackTracking? {
        if (cookie == null) return null

        // 1. Primary: WEB_REMIX (The official YouTube Music web client where user session cookies originate)
        val webRemixResponse = runCatching {
            postPlayer(videoId, PlayerClient.WEB_REMIX, signatureTimestamp = null, authenticated = true)
        }.getOrNull()

        val webTracking = webRemixResponse?.get("playbackTracking")?.jsonObject
        if (webTracking != null) {
            val playbackUrl = webTracking.trackingUrl("videostatsPlaybackUrl")
            if (playbackUrl != null) {
                return PlaybackTracking(
                    playbackUrl = playbackUrl.rewriteToMusicDomain(),
                    watchtimeUrl = webTracking.trackingUrl("videostatsWatchtimeUrl")?.rewriteToMusicDomain(),
                    client = PlayerClient.WEB_REMIX,
                )
            }
        }

        // 2. Secondary fallback: ANDROID_MUSIC (native YouTube Music client)
        val androidMusicResponse = runCatching {
            postPlayer(videoId, PlayerClient.ANDROID_MUSIC, signatureTimestamp = null, authenticated = true)
        }.getOrNull()

        val androidTracking = androidMusicResponse?.get("playbackTracking")?.jsonObject
        if (androidTracking != null) {
            val playbackUrl = androidTracking.trackingUrl("videostatsPlaybackUrl")
            if (playbackUrl != null) {
                return PlaybackTracking(
                    playbackUrl = playbackUrl.rewriteToMusicDomain(),
                    watchtimeUrl = androidTracking.trackingUrl("videostatsWatchtimeUrl")?.rewriteToMusicDomain(),
                    client = PlayerClient.WEB_REMIX,
                )
            }
        }

        // 3. Tertiary fallback: IOS
        val iosResponse = runCatching {
            postPlayer(videoId, PlayerClient.IOS, signatureTimestamp = null, authenticated = true)
        }.getOrNull()
        val iosTracking = iosResponse?.get("playbackTracking")?.jsonObject
        if (iosTracking != null) {
            val playbackUrl = iosTracking.trackingUrl("videostatsPlaybackUrl")
            if (playbackUrl != null) {
                return PlaybackTracking(
                    playbackUrl = playbackUrl.rewriteToMusicDomain(),
                    watchtimeUrl = iosTracking.trackingUrl("videostatsWatchtimeUrl")?.rewriteToMusicDomain(),
                    client = PlayerClient.WEB_REMIX,
                )
            }
        }

        // 4. Authentic Fallback URL (SpatialFlow pattern): ensures tracking is NEVER dropped
        Log.d(TAG, "Using authentic fallback WebRemix tracking URL template for $videoId")
        return PlaybackTracking(
            playbackUrl = "https://music.youtube.com/api/stats/playback?ns=yt&el=detailpage&docid=$videoId&ver=2&c=WEB_REMIX&cver=${PlayerClient.WEB_REMIX.clientVersion}&cplayer=UNIPLAYER",
            watchtimeUrl = "https://music.youtube.com/api/stats/watchtime?ns=yt&el=detailpage&docid=$videoId&ver=2&c=WEB_REMIX&cver=${PlayerClient.WEB_REMIX.clientVersion}&cplayer=UNIPLAYER",
            client = PlayerClient.WEB_REMIX,
        )
    }

    private fun String.rewriteToMusicDomain(): String =
        this.replace("https://s.youtube.com", "https://music.youtube.com")

    private fun JsonObject.trackingUrl(key: String): String? =
        this[key]?.jsonObject?.get("baseUrl")?.jsonPrimitive?.content

    /**
     * The "playback started" ping real YouTube Music clients send once a track
     * becomes audible. This is what creates the history entry the home feed
     * feeds off. [cpn] is the client-playback-nonce identifying this one play.
     */
    suspend fun pingPlayback(
        baseUrl: String,
        cpn: String,
        rtSec: Long = 0L,
        playerClient: PlayerClient = PlayerClient.WEB_REMIX,
    ) = pingStats(baseUrl, cpn, playerClient) {
        parameter("el", "detailpage")
        parameter("ns", "yt")
        parameter("fexp", "")
        parameter("cplayer", "UNIPLAYER")
        parameter("rt", rtSec.toString())
        parameter("lact", "1")
    }

    /**
     * The follow-up ping reporting how much of the track was actually heard.
     * A history entry with no watchtime behind it reads as a skip.
     * [st] and [et] are the watched segment's bounds, in seconds.
     */
    suspend fun pingWatchtime(
        baseUrl: String,
        cpn: String,
        st: Long,
        et: Long,
        lenSec: Long = 0L,
        state: String = "playing",
        rtSec: Long = 0L,
        playerClient: PlayerClient = PlayerClient.WEB_REMIX,
    ) = pingStats(baseUrl, cpn, playerClient) {
        parameter("st", st.toString())
        parameter("et", et.toString())
        parameter("cmt", et.toString())
        if (lenSec > 0L) parameter("len", lenSec.toString())
        parameter("state", state)
        parameter("el", "detailpage")
        parameter("ns", "yt")
        parameter("volume", "100")
        parameter("muted", "0")
        parameter("afmt", "251")
        parameter("cplayer", "UNIPLAYER")
        parameter("rt", rtSec.toString())
        parameter("lact", "1")
    }

    /** Shared shape of the YouTube Music stats pings, including session auth. */
    private suspend fun pingStats(
        baseUrl: String,
        cpn: String,
        playerClient: PlayerClient,
        extras: HttpRequestBuilder.() -> Unit,
    ): Int = runCatching {
        val targetUrl = baseUrl.rewriteToMusicDomain()
        val origin = MUSIC_ORIGIN
        client.get(targetUrl) {
            parameter("ver", "2")
            parameter("c", playerClient.clientName)
            parameter("cver", playerClient.clientVersion)
            parameter("cpn", cpn)
            extras()
            header("User-Agent", playerClient.userAgent)
            header("X-Origin", origin)
            header("Origin", origin)
            header("Referer", "$origin/")
            header("X-YouTube-Client-Name", playerClient.clientId.ifEmpty { "67" })
            header("X-YouTube-Client-Version", playerClient.clientVersion)
            visitorData?.let { header("X-Goog-Visitor-Id", it) }
            cookie?.let { c ->
                header("Cookie", c)
                header("X-Goog-AuthUser", "0")
                sapisidFrom(c)?.let { header("Authorization", sapisidHash(it, origin)) }
            }
        }.status.value
    }.getOrElse { e ->
        Log.w(TAG, "pingStats failed for $baseUrl: ${e.message}")
        -1
    }

    // ---- Writes -------------------------------------------------------------
    //
    // Everything below changes something on the account, so all of it needs
    // the session cookie [postMusic] already signs with. None of it needs a
    // new credential or a different client — the same WEB_REMIX identity that
    // reads the library is the one allowed to edit it.

    /** A write attempted without a session; the caller has a sign-in prompt to show. */
    class NotSignedInException : IllegalStateException("Sign in to YouTube Music to do that")

    private fun requireSession() {
        if (cookie == null) throw NotSignedInException()
    }

    /**
     * Thumbs up / down / neither, for [videoId].
     *
     * The response is inspected rather than discarded. Innertube answers a
     * refused write with HTTP 200 and an `error` object in the body, so the
     * status line alone will happily report a rating that never happened.
     */
    suspend fun rate(videoId: String, status: LikeStatus) {
        requireSession()
        val endpoint = when (status) {
            LikeStatus.LIKE -> "like/like"
            LikeStatus.DISLIKE -> "like/dislike"
            LikeStatus.INDIFFERENT -> "like/removelike"
        }
        val response = postMusic(endpoint) {
            putJsonObject("target") { put("videoId", videoId) }
        }
        response["error"]?.let { error ->
            val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNull
            error("YouTube Music refused the rating: ${message ?: error}")
        }
        // YouTube states what it did in the toast it would have shown. Worth
        // keeping: a rating it declines to act on still answers 200, and this
        // one line is the difference between "the call was made" and "the
        // call did something".
        Log.d(TAG, "$endpoint $videoId -> ${findString(response, "text") ?: "no confirmation"}")
    }

    /**
     * Adds or removes a track from the library, using a token minted by
     * YouTube for exactly that transition — see [com.velthy.client.data.model.SongMenu].
     * There is no video-id form of this call; the token *is* the request.
     */
    suspend fun sendFeedback(token: String) {
        requireSession()
        postMusic("feedback") {
            putJsonArray("feedbackTokens") { add(token) }
        }
    }

    /**
     * Creates a playlist and returns its id.
     *
     * [videoIds] seeds it in the same request, which is what "add to a new
     * playlist" is: one round trip rather than a create followed by an edit
     * that could half-succeed.
     */
    suspend fun createPlaylist(
        title: String,
        privacy: PlaylistPrivacy,
        description: String? = null,
        videoIds: List<String> = emptyList(),
    ): String {
        requireSession()
        val response = postMusic("playlist/create") {
            put("title", title)
            put("description", description.orEmpty())
            put("privacyStatus", privacy.apiValue)
            if (videoIds.isNotEmpty()) {
                putJsonArray("videoIds") { videoIds.forEach { add(it) } }
            }
        }
        // Normally a bare top-level id; occasionally only inside the command
        // that would navigate the web client to the new page, so fall back to
        // finding it by name rather than by a path that would rot.
        return response["playlistId"]?.jsonPrimitive?.contentOrNull
            ?: findString(response, "playlistId")
            ?: error("playlist created but no id came back")
    }

    suspend fun deletePlaylist(playlistId: String) {
        requireSession()
        postMusic("playlist/delete") { put("playlistId", playlistId.removePrefix("VL")) }
    }

    /**
     * One or more edits to a playlist, applied together.
     *
     * The endpoint answers `STATUS_SUCCEEDED` rather than an HTTP error when
     * it refuses — a playlist the account merely saved rather than owns is
     * the usual reason — so the body is checked as well as the status line.
     */
    private suspend fun editPlaylist(
        playlistId: String,
        actions: JsonArrayBuilder.() -> Unit,
    ) {
        requireSession()
        val response = postMusic("browse/edit_playlist") {
            // The edit endpoint takes the raw id; `VL` is the browse prefix.
            put("playlistId", playlistId.removePrefix("VL"))
            putJsonArray("actions", actions)
        }
        val status = response["status"]?.jsonPrimitive?.contentOrNull
        if (status != null && status != "STATUS_SUCCEEDED") {
            error("YouTube Music refused the edit ($status)")
        }
    }

    suspend fun addToPlaylist(playlistId: String, videoIds: List<String>) =
        editPlaylist(playlistId) {
            videoIds.forEach { videoId ->
                addJsonObject {
                    put("action", "ACTION_ADD_VIDEO")
                    put("addedVideoId", videoId)
                }
            }
        }

    /**
     * Removes entries from a playlist. Keyed by set-video-id as well as video
     * id: the same track added twice is two entries, and only the pair says
     * which of them to drop.
     */
    suspend fun removeFromPlaylist(playlistId: String, entries: List<Pair<String, String>>) =
        editPlaylist(playlistId) {
            entries.forEach { (setVideoId, videoId) ->
                addJsonObject {
                    put("action", "ACTION_REMOVE_VIDEO")
                    put("setVideoId", setVideoId)
                    put("removedVideoId", videoId)
                }
            }
        }

    suspend fun renamePlaylist(playlistId: String, title: String) =
        editPlaylist(playlistId) {
            addJsonObject {
                put("action", "ACTION_SET_PLAYLIST_NAME")
                put("playlistName", title)
            }
        }

    /** A fresh client-playback-nonce, identifying one play of one track. */
    fun newCpn(): String = (1..16).map { CPN_ALPHABET.random() }.joinToString("")

    private const val CPN_ALPHABET =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"

    // ---- Request plumbing ---------------------------------------------------

    private suspend fun postMusic(
        endpoint: String,
        query: Map<String, String> = emptyMap(),
        bodyExtras: JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        val response = withRetry {
            client.post("$MUSIC_BASE/$endpoint") {
                contentType(ContentType.Application.Json)
                parameter("prettyPrint", "false")
                query.forEach { (key, value) -> parameter(key, value) }
                header("X-Origin", MUSIC_ORIGIN)
                header("Origin", MUSIC_ORIGIN)
                header("Referer", "$MUSIC_ORIGIN/")
                // Stats pings are only honoured for a session Google recognises
                // as a real client, so identify as one here too — the visitor
                // id is minted on the first call and reused for the session.
                header("X-YouTube-Client-Name", WEB_REMIX_CLIENT_ID)
                header("X-YouTube-Client-Version", WEB_REMIX_VERSION)
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
                cookie?.let { c ->
                    header("Cookie", c)
                    header("X-Goog-AuthUser", "0")
                    sapisidFrom(c)?.let { header("Authorization", sapisidHash(it)) }
                }
                setBody(
                    buildJsonObject {
                        putJsonObject("context") {
                            putJsonObject("client") {
                                put("clientName", "WEB_REMIX")
                                put("clientVersion", WEB_REMIX_VERSION)
                                put("hl", "en")
                                put("gl", "US")
                                visitorData?.let { put("visitorData", it) }
                            }
                            putJsonObject("user") {
                                put("lockedSafetyMode", false)
                                if (com.velthy.client.data.settings.AppSettings.accountMoreContent.value) {
                                    put("enableSafetyMode", false)
                                }
                            }
                            putJsonObject("request") { put("useSsl", true) }
                        }
                        if (com.velthy.client.data.settings.AppSettings.accountMoreContent.value) {
                            put("contentCheckOk", true)
                            put("racyCheckOk", true)
                        }
                        bodyExtras()
                    },
                )
            }.body<JsonObject>()
        }

        if (visitorData == null) {
            visitorData = response["responseContext"]?.jsonObject
                ?.get("visitorData")?.jsonPrimitive?.content
        }
        return response
    }

    /**
     * Unauthenticated by default.
     *
     * The app clients [StreamResolver] walks through are answered *because*
     * they look like anonymous devices; attaching the session cookie to one
     * of those is what gets it turned away with `LOGIN_REQUIRED`. Nothing
     * about the account is needed to fetch audio through them — history is
     * credited separately, by [playbackTracking] and the stats pings, which
     * do carry the session.
     *
     * [authenticated] is the one deliberate exception: [PlayerClient.WEB_REMIX]
     * is a browser identity, and a browser without the session cookie a
     * signed-in listener actually has is the thing that reads as suspicious,
     * not the other way around. Only meaningful with [cookie] set — a caller
     * asking for it while signed out gets the same unauthenticated request as
     * everything else.
     */
    private suspend fun postPlayer(
        videoId: String,
        playerClient: PlayerClient,
        signatureTimestamp: Int?,
        authenticated: Boolean = false,
    ): JsonObject =
        client.post("${playerClient.apiBase()}/player") {
            // A much tighter budget than the shared 30 seconds, because this is
            // the one request on a loop. A player call that is going to answer
            // answers in 120-330ms; one that is going to hang is indifferent to
            // how long it is given, and there are up to seven clients walked
            // per track, each of which may be retried. At the shared ceiling a
            // single unlucky client turned a walk that normally costs two
            // seconds into forty-nine, which the listener spends staring at a
            // track that will in the end be served by extraction anyway. Six
            // seconds is twenty times a healthy answer and cheap to give up on.
            //
            // Set here rather than on the shared client on purpose: browse and
            // search return payloads orders of magnitude larger over the same
            // connection, and a ceiling right for this would truncate those.
            timeout { requestTimeoutMillis = PLAYER_TIMEOUT_MS }
            contentType(ContentType.Application.Json)
            parameter("prettyPrint", "false")
            header("User-Agent", playerClient.userAgent)
            header("X-YouTube-Client-Name", playerClient.clientId)
            header("X-YouTube-Client-Version", playerClient.clientVersion)
            playerClient.origin?.let { header("Origin", it) }
            playerClient.referer?.let { header("Referer", it) }
            // Shared with browse/search so one session is seen throughout,
            // rather than a device that mints a new identity per request.
            visitorData?.let { header("X-Goog-Visitor-Id", it) }
            if (authenticated) {
                cookie?.let { c ->
                    header("Cookie", c)
                    header("X-Goog-AuthUser", "0")
                    sapisidFrom(c)?.let { header("Authorization", sapisidHash(it)) }
                }
            }
            setBody(
                buildJsonObject {
                    putJsonObject("context") {
                        putJsonObject("client") {
                            put("clientName", playerClient.clientName)
                            put("clientVersion", playerClient.clientVersion)
                            playerClient.osName?.let { put("osName", it) }
                            playerClient.osVersion?.let { put("osVersion", it) }
                            playerClient.deviceMake?.let { put("deviceMake", it) }
                            playerClient.deviceModel?.let { put("deviceModel", it) }
                            playerClient.androidSdkVersion?.let { put("androidSdkVersion", it.toInt()) }
                            put("hl", "en")
                            put("gl", "US")
                            visitorData?.let { put("visitorData", it) }
                        }
                    }
                    if (playerClient.needsSignatureTimestamp && signatureTimestamp != null) {
                        putJsonObject("playbackContext") {
                            putJsonObject("contentPlaybackContext") {
                                put("signatureTimestamp", signatureTimestamp)
                            }
                        }
                    }
                    put("videoId", videoId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                },
            )
        }.body<JsonObject>()

    /** Browser-shaped clients are served from the Music host; app clients from YouTube proper. */
    private fun PlayerClient.apiBase(): String = if (usesMusicHost) MUSIC_BASE else YT_BASE

    /** First string value under [key] anywhere in [element], depth-first. */
    private fun findString(element: JsonElement, key: String): String? = when (element) {
        is JsonObject -> (element[key] as? JsonPrimitive)?.contentOrNull
            ?: element.values.firstNotNullOfOrNull { findString(it, key) }
        is JsonArray -> element.firstNotNullOfOrNull { findString(it, key) }
        else -> null
    }

    private fun sapisidFrom(cookieHeader: String): String? {
        val cookies = cookieHeader.split(";").map { it.trim() }
        return cookies.firstOrNull { it.startsWith("SAPISID=") }?.substringAfter("=")
            ?: cookies.firstOrNull { it.startsWith("__Secure-3PAPISID=") }?.substringAfter("=")
            ?: cookies.firstOrNull { it.startsWith("__Secure-1PAPISID=") }?.substringAfter("=")
            ?: cookies.firstOrNull { it.startsWith("PAPISID=") }?.substringAfter("=")
    }

    private fun sapisidHash(sapisid: String, origin: String = MUSIC_ORIGIN): String {
        val timestamp = System.currentTimeMillis() / 1000
        val digest = MessageDigest.getInstance("SHA-1")
            .digest("$timestamp $sapisid $origin".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_$digest"
    }
}
