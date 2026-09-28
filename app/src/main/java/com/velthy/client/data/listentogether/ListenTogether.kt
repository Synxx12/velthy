package com.velthy.client.data.listentogether

import android.content.Context
import android.content.SharedPreferences
import com.velthy.client.BuildConfig
import com.velthy.client.data.DebugLog as Log
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Listen together: one party, shared by up to five signed-in devices.
 *
 * This object is the whole client half of the feature — membership, the socket,
 * the clock, and the controls any member may send. It does **not** touch the
 * player. What it publishes instead is [partyPositionMs]: where this device
 * ought to be, right now, on its own clock.
 *
 * ## A party lasts as long as the app is open
 *
 * Backgrounding, the screen going off, a tunnel, a handover — none of those end
 * a party; that is most of what listening together looks like and the whole
 * reconnect loop below exists for it. Closing the app does end it. The slot is
 * given back at the next launch (see [init]) rather than kept warm, because a
 * party nobody is in is a party of five that only holds four.
 *
 * ## How it stays in time
 *
 * Nothing here acts on "play now" messages, and correctness never depends on
 * when a frame arrived. The server holds a position and the server time that
 * position was true at; [ServerClock] measures this device's offset from that
 * clock; and the playhead is arithmetic from the two. A frame delayed 300 ms
 * carries an anchor 300 ms older and still lands in exactly the right place —
 * which is what makes a party survive one member being on bad mobile data.
 *
 * ## Identity
 *
 * Creating or joining requires a signed-in account — that is what puts real
 * names and faces in the member list on every device. Velthy has no per-profile
 * account tree like BitChord's, so the identity is handed in by the app layer
 * ([setIdentity]) rather than reached for here.
 */
object ListenTogether {

    enum class Connection { OFFLINE, CONNECTING, LIVE }

    data class State(
        val code: String? = null,
        val you: PartyMember? = null,
        val members: List<PartyMember> = emptyList(),
        val maxMembers: Int = 5,
        val playback: PartyPlayback = PartyPlayback(),
        /**
         * Held separately from [playback] because it arrives separately: the
         * state frame carries only a sequence number for it, and this is
         * replaced when the server says the list has actually changed.
         */
        val queue: PartyQueue = PartyQueue(),
        val connection: Connection = Connection.OFFLINE,
        /** False until the first round trip; the playhead is a guess until then. */
        val clockSynced: Boolean = false,
        val roundTripMs: Long = 0,
        /** The last thing that went wrong, for the screen to show. */
        val error: String? = null,
    ) {
        val inParty: Boolean get() = code != null
        val isFull: Boolean get() = members.size >= maxMembers
    }

    /** A refusal from the server, carrying the machine-readable half. */
    class PartyException(val code: String, message: String) : Exception(message)

    /** Who this device jams as, filled in by the app layer from the signed-in account. */
    data class Identity(
        val userId: String,
        val name: String,
        val avatarUrl: String?,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val http = HttpClient(OkHttp) {
        engine {
            config {
                // Not the shared [com.velthy.client.data.Http.client]: that one is
                // tuned for streaming media, and its read timeout would take down
                // a socket that is merely quiet. A party can sit paused for ten
                // minutes and the connection is not in trouble — the server's own
                // heartbeat and the ping below are what say whether it is.
                readTimeout(0, TimeUnit.MILLISECONDS)
                connectTimeout(15, TimeUnit.SECONDS)
                pingInterval(20, TimeUnit.SECONDS)
                retryOnConnectionFailure(true)
            }
        }
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout)
        install(WebSockets)
        // Off, so a 409 "party full" can be read out of the body and shown as
        // itself rather than arriving as a transport exception.
        expectSuccess = false
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = ServerClock()

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** The signed-in identity, or null when signed out. */
    @Volatile
    private var identity: Identity? = null

    /**
     * Points the party layer at the account the app is signed in as, or null on
     * a sign-out. Called from the app layer, since only it knows the account.
     */
    fun setIdentity(value: Identity?) {
        identity = value
    }

    /**
     * A server the user has pointed this install at instead of the built-in one.
     *
     * Blank means "the one this build ships with", and that address is
     * deliberately never published — not through this flow, not on screen, and
     * not in a log. See [redact]. So this is the only server address the app
     * will ever show back, because it is the only one the user typed.
     */
    private val _customServer = MutableStateFlow("")
    val customServerUrl: StateFlow<String> = _customServer.asStateFlow()

    /** Whether a party can be reached at all — a built-in or a custom address. */
    val hasServer: Boolean get() = httpBase().isNotBlank()

    enum class Health { UNKNOWN, CHECKING, ONLINE, OFFLINE }

    data class ServerStatus(val health: Health = Health.UNKNOWN, val latencyMs: Long = 0)

    private val _serverStatus = MutableStateFlow(ServerStatus())
    val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

    private var healthJob: Job? = null

    fun refreshServerHealth() {
        if (healthJob?.isActive == true) return
        healthJob = scope.launch {
            val base = httpBase()
            if (base.isBlank()) {
                _serverStatus.value = ServerStatus(Health.OFFLINE)
                return@launch
            }
            _serverStatus.value = ServerStatus(Health.CHECKING)
            val startedAt = ServerClock.localNowMs()
            val ok = runCatching {
                http.get("$base/healthz") {
                    // Generous on purpose: a sleeping free instance answers in
                    // about thirty seconds, and reporting that as "offline"
                    // would be wrong in the one case somebody most needs the
                    // truth — they are waiting for it to wake up.
                    timeout { requestTimeoutMillis = HEALTH_TIMEOUT_MS }
                }.status.isSuccess()
            }.getOrElse {
                Log.w(TAG, "health check failed: ${redact(it.message)}")
                false
            }
            _serverStatus.value = ServerStatus(
                health = if (ok) Health.ONLINE else Health.OFFLINE,
                latencyMs = ServerClock.localNowMs() - startedAt,
            )
        }
    }

    private lateinit var prefs: SharedPreferences
    private var token: String? = null

    @Volatile
    private var session: DefaultClientWebSocketSession? = null
    private var socketJob: Job? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _customServer.value = prefs.getString(KEY_SERVER, null)?.trim().orEmpty()
        // A membership does not survive the app being closed. Anything still on
        // disk here belongs to a process that is gone, so this launch starts out
        // of the party rather than silently back in one.
        val code = prefs.getString(KEY_CODE, null)
        val saved = prefs.getString(KEY_TOKEN, null)
        prefs.edit().remove(KEY_CODE).remove(KEY_TOKEN).apply()
        if (!code.isNullOrBlank() && !saved.isNullOrBlank()) {
            releaseStaleSlot(code, saved)
        }
    }

    /**
     * Hands a previous process's slot back, so the others see them leave now
     * rather than when the server's disconnect grace sweeps it. Best-effort and
     * deliberately unobserved.
     */
    private fun releaseStaleSlot(code: String, held: String) {
        scope.launch {
            runCatching {
                http.post("${httpBase()}/api/parties/$code/leave") {
                    header("Authorization", "Bearer $held")
                }
            }.onFailure { failure ->
                Log.i(TAG, "stale party slot left to the server's grace: ${redact(failure.message)}")
            }
        }
    }

    /** Points this install at another server, or back at the built-in one if blank. */
    fun setCustomServerUrl(value: String) {
        val cleaned = value.trim().trimEnd('/')
        _customServer.value = cleaned
        prefs.edit().putString(KEY_SERVER, cleaned).apply()
    }

    /** Whether this device has an account it can jam as. */
    fun canJoin(): Boolean = identity != null

    /**
     * Opens the socket for a membership that was restored from storage. Called
     * by the screen rather than from [init], deliberately.
     */
    fun ensureConnected() {
        if (_state.value.code == null || token == null) return
        if (socketJob?.isActive == true) return
        connect()
    }

    // ------------------------------------------------------------ joining --

    suspend fun createParty(): Result<String> = enter { who ->
        post("${httpBase()}/api/parties", JoinRequest(who.userId, who.deviceId, who.name, who.avatarUrl))
    }

    suspend fun joinParty(code: String): Result<String> {
        val previousCode = _state.value.code
        val previousToken = token
        val result = enter { who ->
            val cleaned = code.filter { it.isLetterOrDigit() }.uppercase()
            if (cleaned.length != CODE_LENGTH) {
                throw PartyException("bad_code", "A party code is six letters or digits.")
            }
            post("${httpBase()}/api/parties/$cleaned/join", JoinRequest(who.userId, who.deviceId, who.name, who.avatarUrl))
        }
        // A deep link can arrive while this device is already jamming. Join the
        // new party first so a bad/full/expired invite does not eject it from
        // the old one, then give the old slot back after the switch succeeds.
        val joinedCode = result.getOrNull()
        if (
            joinedCode != null &&
            previousCode != null &&
            previousToken != null &&
            !previousCode.equals(joinedCode, ignoreCase = true)
        ) {
            releaseStaleSlot(previousCode, previousToken)
        }
        return result
    }

    private suspend fun enter(request: suspend (Who) -> PartyMembership): Result<String> =
        withContext(Dispatchers.IO) {
            val who = identity?.let { Who(it.userId, deviceId(), it.name, it.avatarUrl) }
                ?: return@withContext Result.failure(
                    PartyException("not_signed_in", "Sign in to listen together."),
                )
            if (httpBase().isBlank()) {
                return@withContext Result.failure(
                    PartyException("no_server", "Set the party server address first."),
                )
            }
            runCatching { request(who) }
                .onSuccess { membership ->
                    token = membership.token
                    prefs.edit()
                        .putString(KEY_CODE, membership.code)
                        .putString(KEY_TOKEN, membership.token)
                        .apply()
                    clock.reset()
                    _state.value = State(
                        code = membership.code,
                        you = membership.you,
                        members = membership.party.members,
                        maxMembers = membership.party.maxMembers,
                        playback = membership.party.playback,
                        connection = Connection.CONNECTING,
                    )
                    connect()
                }
                .onFailure { failure ->
                    Log.w(TAG, "could not enter a party: ${redact(failure.message)}")
                    _state.update { it.copy(error = failure.displayMessage()) }
                }
                .map { it.code }
        }

    /** Give up this device's slot. The party carries on without it. */
    suspend fun leaveParty() = withContext(Dispatchers.IO) {
        val code = _state.value.code
        val held = token
        socketJob?.cancel()
        socketJob = null
        session = null
        clock.reset()
        token = null
        prefs.edit().remove(KEY_CODE).remove(KEY_TOKEN).apply()
        _state.value = State()
        if (code != null && held != null) {
            // Best effort, and after the local state is already clear: a leave
            // that fails must not strand this device in a party its own screen
            // says it has left. The server's disconnect grace collects the slot
            // either way.
            runCatching {
                http.post("${httpBase()}/api/parties/$code/leave") {
                    header("Authorization", "Bearer $held")
                }
            }
        }
    }

    // ------------------------------------------------------------ controls --
    //
    // Any member may send any of these. There is no host privilege in this
    // feature, on either side of the wire.

    fun play(positionMs: Long? = null): Boolean =
        control("play") { positionMs?.let { put("positionMs", it) } }

    fun pause(positionMs: Long? = null): Boolean =
        control("pause") { positionMs?.let { put("positionMs", it) } }

    fun seek(positionMs: Long): Boolean = control("seek") { put("positionMs", positionMs) }

    fun next(): Boolean = control("next") {}

    fun previous(): Boolean = control("previous") {}

    fun setTrack(track: PartyTrack, positionMs: Long = 0, isPlaying: Boolean = true): Boolean =
        control("setTrack") {
            put("track", json.encodeToJsonElement(PartyTrack.serializer(), track))
            put("positionMs", positionMs)
            put("isPlaying", isPlaying)
        }

    fun setQueue(queue: List<PartyTrack>, index: Int): Boolean = control("setQueue") {
        put("queue", json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(PartyTrack.serializer()), queue))
        put("queueIndex", index)
    }

    /**
     * Sends one control, and reports whether it was actually handed to a live
     * socket.
     *
     * The return value is not decoration. A control that goes nowhere leaves the
     * party describing the world before the button was pressed, so the sender's
     * own reconcile would undo the action a moment later — the song someone just
     * picked snapping back to the one they left. A caller that is told the frame
     * did not go out can hold off instead, and say it again when the socket
     * returns. See `PartySync.publish`.
     */
    private fun control(
        action: String,
        body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): Boolean {
        val frame = buildJsonObject {
            put("type", "control")
            put("action", action)
            body()
        }
        return send(frame)
    }

    /** False when there is no socket to carry the frame. */
    private fun send(frame: JsonObject): Boolean {
        val live = session ?: return false
        scope.launch {
            runCatching { live.send(Frame.Text(frame.toString())) }
                .onFailure { Log.w(TAG, "control not sent: ${redact(it.message)}") }
        }
        return true
    }

    // --------------------------------------------------------- the playhead --

    /**
     * Where this device should be in the current track, right now. The one
     * number the player layer will need. Null when there is no party, or
     * nothing playing in it.
     */
    fun partyPositionMs(): Long? {
        val playback = _state.value.playback
        playback.track ?: return null
        if (!playback.isPlaying) return playback.positionMs
        val serverNow = clock.serverNowMs() ?: return playback.effectivePositionMs
        val elapsed = (serverNow - playback.anchorMs).coerceAtLeast(0)
        val position = playback.positionMs + elapsed
        val duration = playback.track.durationMs
        return if (duration != null) minOf(position, duration) else position
    }

    /**
     * How far in the future the party's next resume is scheduled, or 0 if it is
     * already under way.
     */
    fun msUntilStart(): Long {
        val playback = _state.value.playback
        if (!playback.isPlaying) return 0
        val serverNow = clock.serverNowMs() ?: return 0
        return (playback.anchorMs - serverNow).coerceAtLeast(0)
    }

    // ------------------------------------------------------------- socket --

    private fun connect() {
        socketJob?.cancel()
        socketJob = scope.launch { runSocketLoop() }
    }

    /**
     * Connect, pump frames until the connection goes away for any reason, back
     * off, go round again. The loop is the design, not error handling bolted
     * onto one.
     */
    private suspend fun runSocketLoop() {
        var backoffMs = 1_000L
        while (currentScopeActive()) {
            val code = _state.value.code ?: return
            val held = token ?: return
            try {
                _state.update { it.copy(connection = Connection.CONNECTING) }
                http.webSocket("${wsBase()}/ws/parties/$code?token=$held") {
                    session = this
                    backoffMs = 1_000L
                    _state.update { it.copy(connection = Connection.LIVE, error = null) }
                    launch { pingLoop() }
                    launch { reportLoop() }
                    for (frame in incoming) {
                        if (frame is Frame.Text) onFrame(frame.readText())
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "party socket dropped: ${redact(failure.message)}")
            } finally {
                session = null
            }
            if (!currentScopeActive()) return
            _state.update { it.copy(connection = Connection.CONNECTING) }
            // The clock is not carried across a gap.
            clock.reset()
            _state.update { it.copy(clockSynced = false) }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(20_000L)
        }
    }

    private suspend fun currentScopeActive(): Boolean =
        kotlinx.coroutines.currentCoroutineContext().isActive

    /**
     * A burst on arrival, then a slow trickle. The burst is what makes the first
     * seconds of a party accurate.
     *
     * Six pings at 120ms rather than four at 300ms, which is the same number of
     * round trips in half the time. The point of the burst is to get enough
     * samples that the *best* one — the smallest round trip, which is what
     * [ServerClock] picks — is a good one, and that is a question of how many
     * attempts have been made rather than how long they were spaced. Nothing
     * here waits for the previous answer before sending the next, so a slower
     * connection simply has more of them in flight, and the wait before the
     * party can act on a measured clock drops from about 1.2s to about 0.7s.
     */
    private suspend fun DefaultClientWebSocketSession.pingLoop() {
        repeat(PING_BURST_COUNT) {
            ping()
            delay(PING_BURST_STEP_MS)
        }
        while (true) {
            delay(PING_INTERVAL_MS)
            ping()
        }
    }

    private suspend fun DefaultClientWebSocketSession.ping() {
        val sentAt = ServerClock.localNowMs()
        val frame = buildJsonObject {
            put("type", "ping")
            put("clientMs", sentAt)
        }
        runCatching { send(Frame.Text(frame.toString())) }
    }

    /**
     * Tells the server where this device actually is. Nothing is decided by it —
     * the server's state is the truth — but it keeps the membership alive.
     */
    private suspend fun DefaultClientWebSocketSession.reportLoop() {
        while (true) {
            delay(REPORT_INTERVAL_MS)
            val position = partyPositionMs() ?: continue
            val frame = buildJsonObject {
                put("type", "report")
                put("positionMs", position)
                put("isPlaying", _state.value.playback.isPlaying)
            }
            runCatching { send(Frame.Text(frame.toString())) }
        }
    }

    private fun onFrame(text: String) {
        val received = ServerClock.localNowMs()
        val frame = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        when (frame["type"]?.jsonPrimitive?.content) {
            "welcome" -> {
                val party = frame["party"]?.let {
                    runCatching { json.decodeFromJsonElement(PartySnapshot.serializer(), it) }.getOrNull()
                } ?: return
                val you = frame["you"]?.let {
                    runCatching { json.decodeFromJsonElement(PartyMember.serializer(), it) }.getOrNull()
                }
                _state.update { it.copy(
                    code = party.code,
                    you = you ?: it.you,
                    members = party.members,
                    maxMembers = party.maxMembers,
                    playback = party.playback,
                    // A snapshot is the one message that carries the queue
                    // unconditionally — a device that has just arrived has no
                    // other way to learn it.
                    queue = party.queue,
                    connection = Connection.LIVE,
                    error = null,
                ) }
            }

            "pong" -> {
                val sentAt = frame["clientMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: return
                val serverMs = frame["serverMs"]?.jsonPrimitive?.content?.toLongOrNull() ?: return
                clock.record(sentAt, serverMs, received)
                _state.update { it.copy(
                    clockSynced = clock.synced,
                    roundTripMs = clock.roundTripMs,
                ) }
            }

            "state" -> {
                val playback = frame["playback"]?.let {
                    runCatching { json.decodeFromJsonElement(PartyPlayback.serializer(), it) }.getOrNull()
                } ?: return
                // Older than what is already applied, so it says nothing.
                if (playback.seq < _state.value.playback.seq) return
                _state.update { it.copy(playback = playback) }
                // The queue does not ride along with the state — only its
                // sequence number does.
                if (playback.queueSeq != _state.value.queue.seq) {
                    send(buildJsonObject { put("type", "syncQueue") })
                }
            }

            "queue" -> {
                val queue = frame["queue"]?.let {
                    runCatching { json.decodeFromJsonElement(PartyQueue.serializer(), it) }.getOrNull()
                } ?: return
                if (queue.seq < _state.value.queue.seq) return
                _state.update { it.copy(queue = queue) }
            }

            "members" -> {
                val members = frame["members"]?.let {
                    runCatching {
                        json.decodeFromJsonElement(
                            kotlinx.serialization.builtins.ListSerializer(PartyMember.serializer()),
                            it,
                        )
                    }.getOrNull()
                } ?: return
                _state.update { it.copy(members = members) }
            }

            "error" -> {
                val reason = frame["error"]?.jsonPrimitive?.content
                val message = frame["message"]?.jsonPrimitive?.content
                Log.w(TAG, "party server refused a frame: $reason ${redact(message)}")
                _state.update { it.copy(error = message) }
                // These two are terminal, and the reconnect loop cannot learn
                // that on its own.
                if (reason == "bad_token" || reason == "no_such_party") {
                    scope.launch { leaveParty() }
                }
            }

            "bye" -> {
                // The server has let this membership go — the slot was swept, or
                // the party ended.
                scope.launch { leaveParty() }
            }
        }
    }

    // ------------------------------------------------------------- plumbing --

    private data class Who(
        val userId: String,
        val deviceId: String,
        val name: String,
        val avatarUrl: String?,
    )

    /**
     * This install's identity to the party, independent of who is signed in.
     *
     * The server counts devices, not people, and uses this to tell a rejoin from
     * a sixth device — so it has to survive a sign-out, an account switch and a
     * process death, and must not survive an uninstall.
     */
    private fun deviceId(): String {
        prefs.getString(KEY_DEVICE, null)?.let { return it }
        return UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE, it).apply()
        }
    }

    private suspend fun post(url: String, body: JoinRequest): PartyMembership {
        val response = http.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        if (!response.status.isSuccess()) throw response.toPartyException()
        return response.body()
    }

    private suspend fun HttpResponse.toPartyException(): PartyException {
        val bodyText = runCatching { bodyAsText() }.getOrDefault("")
        val parsed = runCatching { json.decodeFromString(ApiError.serializer(), bodyText) }.getOrNull()
        return PartyException(
            code = parsed?.code.orEmpty().ifBlank { "http_${status.value}" },
            message = parsed?.message?.takeIf { it.isNotBlank() }
                // 422 is the server rejecting an identity, which here can only
                // mean the account layer handed over something blank.
                ?: if (status.value == 422) "This account can't be used to jam."
                else "The party server said ${status.value}.",
        )
    }

    /**
     * The address requests actually go to: the user's override, or the built-in
     * one. Private, and the only place the built-in address is read.
     */
    private fun httpBase(): String {
        val raw = _customServer.value.trim().trimEnd('/').ifBlank { DEFAULT_SERVER }
        if (raw.isBlank()) return ""
        return if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
    }

    /**
     * A message with every server address taken out of it. Failures from the
     * HTTP and WebSocket layers name the host they were talking to, and those
     * messages end up in logcat and on the party screen — neither of which may
     * carry the address.
     */
    private fun redact(text: String?): String {
        var out = text.orEmpty()
        if (out.isEmpty()) return out
        listOf(DEFAULT_SERVER, _customServer.value)
            .filter { it.isNotBlank() }
            .flatMap { listOf(it, it.substringAfter("://")) }
            .sortedByDescending(String::length)
            .forEach { out = out.replace(it, SERVER_PLACEHOLDER, ignoreCase = true) }
        return out.replace(ABSOLUTE_URL, SERVER_PLACEHOLDER)
    }

    /**
     * What the listener is told when the transport fails. Deliberately not the
     * exception's own words even after redaction.
     */
    private fun Throwable.displayMessage(): String = when (this) {
        is PartyException -> message ?: UNREACHABLE
        else -> UNREACHABLE
    }

    private fun wsBase(): String = httpBase()
        .replaceFirst("https://", "wss://")
        .replaceFirst("http://", "ws://")

    const val CODE_LENGTH = 6

    private const val TAG = "ListenTogether"
    private const val PREFS = "velthy_listen_together"
    private const val KEY_SERVER = "server_url"

    /** What a redacted address reads as. Not a hostname, so it cannot be resolved back. */
    private const val SERVER_PLACEHOLDER = "<party server>"

    private const val UNREACHABLE = "Couldn’t reach the party server."

    private val ABSOLUTE_URL = Regex("""(?:https?|wss?)://[^\s,;)\]}'"]+""", RegexOption.IGNORE_CASE)
    private const val KEY_CODE = "party_code"
    private const val KEY_TOKEN = "party_token"
    private const val KEY_DEVICE = "device_id"

    /**
     * The party server this build ships pointed at, from `PARTY_SERVER_URL` in
     * `local.properties` (see app/build.gradle.kts).
     *
     * Only a default. It seeds the address box on the Listen Together screen and
     * is then overridden by anything typed there, which persists. Empty is a
     * supported state: a checkout without that line builds fine and simply asks
     * for an address.
     */
    private val DEFAULT_SERVER: String = BuildConfig.PARTY_SERVER_URL
    private const val PING_INTERVAL_MS = 15_000L
    private const val REPORT_INTERVAL_MS = 10_000L

    /** The connect-time burst — see [pingLoop] for why these numbers. */
    private const val PING_BURST_COUNT = 6
    private const val PING_BURST_STEP_MS = 120L
    private const val HEALTH_TIMEOUT_MS = 45_000L
}
