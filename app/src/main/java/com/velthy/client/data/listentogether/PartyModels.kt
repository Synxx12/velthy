package com.velthy.client.data.listentogether

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The party server's wire format, one-for-one.
 *
 * The server speaks camelCase for exactly this reason â€” it has no other client
 * â€” so these carry no `@SerialName` and will not need any. If a field ever has
 * to be renamed on one side, rename it on both rather than papering over the
 * difference here; the protocol is documented in `backend/README.md` and that
 * document is the contract.
 */

/**
 * A song as the party knows it.
 *
 * Deliberately *not* [Song][com.velthy.client.data.model.Song]. What a party
 * shares is which track is playing and where the playhead is; how each device
 * gets the audio â€” which source answered, at what quality, from the network or
 * from a download â€” stays that device's own business. Two people in a party can
 * be on entirely different sources and still be in the same place in the same
 * song, and keeping this type small is what guarantees that.
 */
@Serializable
data class PartyTrack(
    val videoId: String,
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String? = null,
    val durationMs: Long? = null,
    /**
     * Whether this entry came from AutoPlay rather than from somebody asking
     * for it.
     *
     * Carried on the wire so a device that receives a shared queue can tell the
     * two sections apart, and so the queue it builds locally keeps the boundary
     * the sender had: a listener's "add to queue" lands above the mix rather
     * than inside it.
     */
    val fromAutoplay: Boolean = false,
)

/** One signed-in device in the party, as every other device sees it. */
@Serializable
data class PartyMember(
    val memberId: String,
    val userId: String = "",
    val displayName: String = "",
    val avatarUrl: String? = null,
    val isHost: Boolean = false,
    /** Whether they are currently holding a socket â€” not whether they are still in. */
    val connected: Boolean = false,
    val joinedAtMs: Long = 0,
    val lastSeenMs: Long = 0,
)

/**
 * Where the party is, as of a server timestamp.
 *
 * [positionMs] is not a current position. It is the position at [anchorMs], and
 * it only becomes a current position once the reader adds the time since â€” see
 * [ListenTogether.partyPositionMs]. That indirection is the entire sync
 * mechanism: a frame delayed by 300 ms carries an anchor 300 ms older and still
 * lands this device in exactly the right place.
 */
@Serializable
data class PartyPlayback(
    /**
     * Bumped by the server on every change. A state whose [seq] is not greater
     * than the one already applied is dropped unread â€” which is what makes two
     * people hitting pause at the same moment settle rather than oscillate.
     */
    val seq: Long = 0,
    val track: PartyTrack? = null,
    /**
     * Bumped only when the queue's *contents* change, and deliberately the only
     * thing about the queue that rides along with the state.
     *
     * The queue itself travels as [PartyQueue], separately and rarely. This
     * frame is re-sent to every device every few seconds forever, and a queue
     * inside it would be large, near-constant, and paid for continuously on
     * somebody's mobile data. So all that arrives here is a number to compare
     * against the copy already held â€” see [ListenTogether] for the refetch.
     */
    val queueSeq: Long = 0,
    val queueLength: Int = 0,
    val queueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val anchorMs: Long = 0,
    /** The server's own reading of [positionMs] at the instant it sent the frame. */
    val effectivePositionMs: Long = 0,
    val updatedBy: String? = null,
    /** Member who selected this track; stable across pause, play, and seek controls. */
    val startedBy: String? = null,
    /** Snapshot of their name so attribution survives that member leaving. */
    val startedByName: String? = null,
    val updatedAtMs: Long = 0,
)

/**
 * The party's running order, which travels on its own schedule.
 *
 * Sent whole when a device joins â€” there is no other way for it to learn the
 * list â€” and after that only when it actually changes. [seq] is how a device
 * knows its copy is stale: every state frame carries the server's current one,
 * so a missed update is noticed on the very next heartbeat rather than lived
 * with until somebody presses something.
 */
@Serializable
data class PartyQueue(
    val seq: Long = 0,
    val index: Int = -1,
    val items: List<PartyTrack> = emptyList(),
)

@Serializable
data class PartySnapshot(
    val code: String = "",
    val createdAtMs: Long = 0,
    val maxMembers: Int = 5,
    /**
     * Whether only the host may drive the music here.
     *
     * Defaulted false so a party on a server that predates the setting reads as
     * the shared free-for-all this feature shipped as, rather than as locked.
     */
    val hostOnlyControl: Boolean = false,
    val members: List<PartyMember> = emptyList(),
    val playback: PartyPlayback = PartyPlayback(),
    val queue: PartyQueue = PartyQueue(),
    val serverMs: Long = 0,
)

/**
 * Who is in a party, to somebody who has not joined it.
 *
 * Deliberately smaller than [PartySnapshot]: enough to show a face and a name
 * before committing a device slot, and nothing that would let the holder of a
 * code act on a party they are not in.
 */
@Serializable
data class PartyPreview(
    val code: String = "",
    val hostName: String = "",
    val memberCount: Int = 0,
    val maxMembers: Int = 5,
    val isFull: Boolean = false,
    val members: List<PartyPreviewMember> = emptyList(),
)

/** One face in a [PartyPreview], as the join confirmation shows it. */
@Serializable
data class PartyPreviewMember(
    val displayName: String = "",
    val avatarUrl: String? = null,
    val isHost: Boolean = false,
)

/**
 * One thing somebody did in a party, as the activity feed shows it.
 *
 * Local to this app session and not persisted: it is a live read of what is
 * happening now, not a record anybody needs tomorrow.
 */
@Serializable
data class PartyActivity(
    val action: String,
    val by: String,
    val atMs: Long,
    val detail: String = "",
)

/** The answer to a create or a join: the code, and this device's key to it. */
@Serializable
data class PartyMembership(
    val code: String,
    val token: String,
    val you: PartyMember,
    val party: PartySnapshot,
    val serverMs: Long = 0,
)

@Serializable
internal data class JoinRequest(
    val userId: String,
    val deviceId: String,
    val displayName: String,
    val avatarUrl: String? = null,
    /**
     * How many devices the creator wants to allow.
     *
     * Only read by the server when a party is being *created*: a join to an
     * existing party cannot resize it, and the host has a control for that once
     * inside.
     */
    val maxMembers: Int? = null,
)

@Serializable
internal data class ApiError(
    @SerialName("error") val code: String = "",
    val message: String = "",
)
