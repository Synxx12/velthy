package com.velthy.client.playback

import android.os.SystemClock
import androidx.media3.common.Player
import com.velthy.client.data.DebugLog as Log
import com.velthy.client.data.listentogether.ListenTogether
import com.velthy.client.data.listentogether.PartyTrack
import com.velthy.client.data.model.Song
import com.velthy.client.data.sources.TrackMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
/**
 * Makes the player obey the party, and the party obey this player.
 *
 * Lives in [PlaybackService] rather than in the UI on purpose: a party has to
 * survive the app being backgrounded and the screen going off, which is most of
 * what listening together actually looks like. Bound to a composable it would
 * desync the moment somebody put their phone down.
 *
 * ## Telling the two directions apart
 *
 * The hard part of this is not moving the playhead, it is knowing *who moved
 * it*. A naive binder that reacts to player events and republishes them ends up
 * echoing its own corrections around the party forever. This one never has to
 * guess, because the two directions arrive through physically different doors:
 *
 *  - **Outbound** is [onLocalIntent], called only from [PlaybackService]'s
 *    `SessionPlayer` â€” the [androidx.media3.common.ForwardingPlayer] every
 *    *user* action passes through, wherever it came from: the app, the
 *    notification, a headset button, Android Auto. Plus the one thing that is
 *    the user's intent without being their action, the queue moving on by
 *    itself at the end of a track.
 *  - **Inbound** is [reconcile], which writes straight to the ExoPlayer,
 *    underneath that wrapper. So nothing this class does to the player can ever
 *    come back to it as an intent.
 *
 * One deliberate consequence: a pause this app did not ask for â€” audio focus
 * lost to another app, a call â€” goes directly to the player and is *not*
 * published. One person taking a call does not stop the music for everyone else.
 *
 * ## Losing the audio to another app
 *
 * That device must then also stop *following*, which is a second thing and not
 * the same one. [reconcile] exists to put a player that is not where the party
 * is back where the party is, and a player silenced by focus loss looks exactly
 * like one that has fallen behind â€” so it was dutifully seeking it into place
 * and pressing play, which took the audio straight back off whatever the
 * listener had just started. The song they had left came back over the top of
 * it, every seven hundred milliseconds, for as long as they kept trying.
 *
 * So focus loss detaches this device from the party until its own user asks to
 * come back ([focusLost]), and coming back is a catch-up rather than a control
 * ([rejoining]): the gap opened by being away belongs to the device that was
 * away, and publishing it as a seek would haul four other people back to where
 * one of them took a phone call.
 *
 * ## Following in time
 *
 * [reconcile] converges on [ListenTogether.partyPositionMs], which is the
 * party's position translated into this device's own clock. Three details carry
 * most of the weight:
 *
 *  - **Resuming waits for the scheduled start.** The server anchors a resume a
 *    few hundred milliseconds into the future so every device has one instant to
 *    aim at. Calling `play()` as soon as the frame lands would start this device
 *    early by exactly that lead â€” and being *consistently* early is worse than
 *    being occasionally late, because it is below the drift limit and would
 *    never be corrected. So a resume waits out [ListenTogether.msUntilStart].
 *  - **Drift is corrected by seeking, and only when it is real.** A seek is
 *    audible, so [DRIFT_LIMIT_MS] is set well above the jitter of two decoders
 *    running independently and at the level of an actual desync â€” a buffering
 *    stall, a doze, a device that came back from a tunnel.
 *  - **Position is only trusted once the clock is.** Before the first
 *    ping/pong there is no measured offset, so track and play/pause are applied
 *    and the playhead is left alone rather than seeked to a guess.
 */
class PartySync(
    private val scope: CoroutineScope,
    /** Read fresh every time: the service swaps players at a crossfade. */
    private val player: () -> Player?,
) {

    private val jobs = mutableListOf<Job>()
    private var publishJob: Job? = null
    private var startJob: Job? = null

    /**
     * Until when [reconcile] should keep its hands off.
     *
     * Set when this device's user does something. Between the action and the
     * server's echo of it, the party state still describes the world before the
     * button was pressed â€” reconciling against it in that window would undo the
     * user's own action in front of them.
     */
    private var reconcileQuietUntilMs = 0L

    /**
     * The party seq at which this device's own controls will have all landed.
     *
     * Not "the seq before I published": choosing a track publishes *two*
     * controls, a queue and a track, and the party is in a torn state between
     * them â€” new queue, old track. Clearing the quiet window on the first one
     * let [reconcile] run against exactly that state and haul the player back
     * off the song the user had just picked. Which is what it did.
     */
    private var awaitSeq = Long.MAX_VALUE

    /**
     * The queue seq at which this device's own queue changes will have landed.
     *
     * Separate from [awaitSeq] because the two counters are separate on the
     * wire: a queue edit moves the running order without moving anybody's
     * playhead, and the state frame's own seq does not budge for it. Waiting on
     * the wrong one left the quiet window open for its full length on every
     * reorder, and held [reconcile] off the queue it was supposed to apply.
     */
    private var awaitQueueSeq = Long.MAX_VALUE

    /** Guards against re-issuing a load for a track already being loaded. */
    private var loadingVideoId: String? = null

    /**
     * The last party control this device has put itself exactly on.
     *
     * A control is a discontinuity â€” everybody is meant to land on the same
     * instant, precisely â€” whereas the time between controls is a slow drift
     * worth tolerating. So a seq not yet aligned to is matched exactly, and
     * after that [DRIFT_LIMIT_MS] applies. Without this the device that *issued*
     * a control keeps whatever head start issuing it gave it: below the drift
     * limit, so never corrected, and permanent.
     */
    private var alignedSeq = -1L

    /**
     * A resume this device has accepted but not yet performed.
     *
     * Deferring the local `play()` hides the user's intent from the player:
     * [publish] reads `playWhenReady`, which is still false, and would report
     * "same track, still paused" â€” so the one control the tap existed to send
     * never goes out, the party stays paused, and the only thing that starts
     * anything is the fallback, a second and a half later. This carries the
     * intent across that gap.
     */
    @Volatile
    private var deferredPlayPending = false

    /**
     * This device has been silenced by something its user did not ask for.
     *
     * Set when the player gives up audio focus permanently, which is what
     * another app starting playback looks like from here. While it is set this
     * class does nothing at all to the player: not a resume, not a load, not a
     * corrective seek. The party carries on for everybody else; this device is
     * simply no longer one of the places it is coming out of.
     *
     * Cleared by the user asking for the music back, and as a backstop by the
     * player playing again through any route at all.
     */
    @Volatile
    private var focusLost = false

    /**
     * The user has asked to come back after [focusLost], and this device is
     * behind by however long it was away.
     *
     * That gap is this device's to close. Published as a seek â€” which is what
     * an ordinary resume does, and exactly what it should do â€” it would drag
     * four other people back to the moment one of them answered a phone call.
     * So a rejoin sends nothing and lets [reconcile] do the catching up.
     */
    @Volatile
    private var rejoining = false

    /**
     * A control this device could not put on the wire.
     *
     * While it is set, [reconcile] keeps its hands off: the player is where the
     * user put it, the party has not been told yet, and reconciling against the
     * party's older state is exactly the "song changes back by itself" that the
     * hold exists to prevent. Cleared when the socket returns and the control is
     * finally sent. See [publish].
     */
    @Volatile
    private var publishPending = false

    /** Consecutive over-limit readings. See the drift branch of [reconcile]. */
    private var driftStrikes = 0

    /** When the player may next be seeked for drift, having just been. */
    private var driftCooldownUntilMs = 0L

    /**
     * A queue row is being dragged in the UI, so reorders are parked rather
     * than published.
     *
     * Dragging a row from position 5 to position 1 crosses four neighbours, and
     * each crossing is a queue change the player reports — publishing every one
     * of them would put four controls on the wire for one gesture, and the party
     * would watch the song jump through the intermediate positions. The
     * reorder is sent once, when the row lands.
     */
    private var queueDragActive = false

    /** Whether a move landed while [queueDragActive], awaiting [endQueueDrag]. */
    private var queueDragDirty = false

    /**
     * A listener in a locked party has paused their own device.
     *
     * The party plays on without them — that is the whole point of the pause
     * being local — which puts this device in the one state [reconcile] is built
     * to eliminate: the party is playing and this player is not. Left alone, the
     * very next tick would press play again, 700ms after the listener asked for
     * quiet.
     *
     * So while this is set, [reconcile] still follows the party in every respect
     * that is not audible — the track that is loaded, the queue behind it — and
     * simply does not start the player or chase the playhead. Cleared by the
     * listener pressing play, by the lock being lifted, by this device becoming
     * the host, and by leaving.
     */
    private var locallyPaused = false

    /**
     * The party this device was last seen in, so entering and leaving can be
     * told apart from every other change to the state.
     *
     * Read off the state rather than set by the join and leave calls because a
     * party can also be *restored*: a process killed mid-jam comes back holding
     * a membership it never asked for, and that arrival has to stash the
     * personal queue exactly like a hand-joined one does. Null is "not in one".
     */
    private var lastPartyCode: String? = null

    fun start() {
        jobs += scope.launch {
            ListenTogether.state
                // A new state, or leaving/joining. Not every field: this exists
                // to react promptly to a control, and the round-trip counter
                // changing is not one.
                //
                // [Signal.clockSynced] is in here for the one moment it decides
                // everything: a device that has just joined. Until the first
                // pong lands, [reconcile] refuses to act on a playing party —
                // there is no measured offset to translate its anchor with — so
                // without this the join waits out the tick below after the clock
                // is already good, and the track lands a beat late for no reason
                // anybody could see.
                .map {
                    Signal(
                        seq = it.playback.seq,
                        queueSeq = it.queue.seq,
                        code = it.code,
                        clockSynced = it.clockSynced,
                        live = it.connection == ListenTogether.Connection.LIVE,
                    )
                }
                .distinctUntilChanged()
                .collect { signal ->
                    // Entering or leaving, observed from the code itself rather
                    // than from the join/leave calls — a party restored by a
                    // cold start arrives here too, and it has to stash and
                    // restore exactly like one joined by hand.
                    if (signal.code != lastPartyCode) {
                        if (signal.code != null) onEnteredParty() else onLeftParty()
                        lastPartyCode = signal.code
                    }
                    // Every control this device sent has come back around, so
                    // the party now describes the world the user made — or
                    // somebody else has moved it on past ours, which is equally
                    // a reason to stop holding reconcile off. Both counters have
                    // to have landed: a reorder and a resume are two different
                    // controls and the party is in a torn state between them.
                    if (signal.seq >= awaitSeq && signal.queueSeq >= awaitQueueSeq) {
                        reconcileQuietUntilMs = 0L
                    }
                    // The socket is back, and something this device did while it
                    // was down never went out. Say it now, before reconcile gets
                    // a chance to undo it.
                    if (signal.live && publishPending) {
                        publishPending = false
                        publish()
                        return@collect
                    }
                    reconcile()
                }
        }
        jobs += scope.launch {
            while (true) {
                delay(TICK_MS)
                // The screen is the only thing that opens this socket otherwise,
                // and a party outlives the screen. A process restarted by the
                // system into a party it is still a member of has the membership
                // but no connection, and would sit silently out of step; this is
                // what puts it back. Idempotent â€” it returns immediately when a
                // socket is already up, or when there is no party.
                if (ListenTogether.state.value.inParty) ListenTogether.ensureConnected()
                reconcile()
            }
        }
    }

    /** The parts of a party state [start] reacts to promptly. */
    private data class Signal(
        val seq: Long,
        val queueSeq: Long,
        val code: String?,
        val clockSynced: Boolean,
        val live: Boolean,
    )

    fun stop() {
        jobs.forEach(Job::cancel)
        jobs.clear()
        publishJob?.cancel()
        startJob?.cancel()
    }

    /**
     * The user did something to playback on this device â€” or the queue moved on
     * by itself, which is the same thing as far as the party is concerned.
     *
     * Debounced, because one gesture is several calls: choosing a track in the
     * app is `setMediaItems` then `prepare` then `play`, and publishing each
     * would put three controls on the wire for one tap.
     */
    fun onLocalIntent() {
        val party = ListenTogether.state.value
        if (!party.inParty) return
        // Whatever the user just pressed, they want this device in the party
        // again â€” so it follows from here, and this one publish is a catch-up
        // rather than a control. See [focusLost].
        if (focusLost) {
            Log.i(TAG, "rejoining the party after losing the audio")
            focusLost = false
            rejoining = true
        }
        reconcileQuietUntilMs = SystemClock.elapsedRealtime() + INTENT_QUIET_MS
        // Nothing has been published yet, so there is no seq to wait for and
        // the window must not clear on somebody else's control either — it is
        // protecting an action of ours that has not gone out.
        awaitSeq = Long.MAX_VALUE
        awaitQueueSeq = Long.MAX_VALUE
        // A row being dragged through the queue calls this once per neighbour it
        // crosses. Parked here rather than published, so the party hears about
        // the reorder once, when the row lands.
        if (queueDragActive) {
            queueDragDirty = true
            publishJob?.cancel()
            return
        }
        publishJob?.cancel()
        publishJob = scope.launch {
            delay(PUBLISH_DEBOUNCE_MS)
            publish()
        }
    }

    /** A queue row started dragging in the UI. @see onLocalIntent */
    fun beginQueueDrag() {
        queueDragActive = true
    }

    /** The row was dropped, or the drag cancelled. Flushes anything parked. */
    fun endQueueDrag() {
        queueDragActive = false
        if (queueDragDirty) {
            queueDragDirty = false
            onLocalIntent()
        }
    }

    /**
     * Play or pause for a listener whose party is locked to its host.
     *
     * Returns true when it has handled the press, which it has whenever the
     * party is locked to somebody else: the press moves this device and nothing
     * else, publishes nothing, and is never deferred to a party that is not
     * waiting on this device for anything.
     *
     * Resuming rejoins wherever the party has *got to* rather than where this
     * listener left off — the radio model, and the only thing that makes sense
     * when the music never stopped for anybody else. That seek is the one this
     * device performs on its own behalf while locked.
     */
    fun onLockedTransport(playing: Boolean): Boolean {
        val party = ListenTogether.state.value
        if (!party.controlsLocked) return false
        val exo = player() ?: return false
        if (playing) {
            locallyPaused = false
            // Nothing to join while the party itself is paused. Starting here
            // would play alone for the one tick it takes [reconcile] to notice
            // and pause again — which is what a listener saw as the music
            // starting and immediately stopping.
            if (!party.playback.isPlaying) return true
            ListenTogether.partyPositionMs()
                ?.takeIf { party.clockSynced }
                ?.let(exo::seekTo)
            exo.play()
        } else {
            locallyPaused = true
            exo.pause()
        }
        return true
    }

    /**
     * Drops a local pause that has stopped meaning anything.
     *
     * Only the lock going away does that: the host handing control back, this
     * device becoming the host, or leaving the party. In each the listener is an
     * ordinary member again and [reconcile] resumes owning the player. The
     * player is left exactly as it is either way — the listener asked for quiet,
     * and only the exemption from [reconcile] is what expires.
     *
     * Notably *not* ended by the party pausing. Somebody who muted their own
     * device does not expect it to come back on because the host paused and
     * pressed play again; the pause is theirs until they lift it.
     */
    private fun clearLocalPauseIfFreed(party: ListenTogether.State) {
        if (!locallyPaused) return
        if (!party.controlsLocked) locallyPaused = false
    }

    /**
     * The track ended and the player moved on by itself — nobody pressed
     * anything.
     *
     * This is the one advance no other path reports: a tap goes through the
     * session wrapper and lands in [onLocalIntent], but ExoPlayer advancing at
     * the end of a track never touches it. Left unreported, the party stays on
     * the song that just finished while this device is already on the next one,
     * and the tick below reads that as this device having run ahead: it loads
     * the party's track, which — being the one that just ended — ends again
     * immediately, and the two take turns overwriting each other several times
     * a second. Measured on a device at a flat 640ms period, indefinitely.
     *
     * So it is reported, but not blindly. Two devices reaching the end of a
     * track at the same moment is the ordinary way this happens, and both
     * would publish a track change for one event. The grace below is the time
     * for the other one's control to arrive first: if the party has moved by
     * the time it elapses, that control is the one to follow and this device
     * says nothing.
     */
    fun onAutoAdvance() {
        val party = ListenTogether.state.value
        if (!party.inParty) return
        // A control of ours is already owed to the party, or already on its
        // way. Either one will publish the state as it stands when it runs, and
        // that state is this advance — a second publish would be two controls
        // for one thing.
        if (publishPending) return
        if (publishJob?.isActive == true) return

        val beforeSeq = party.playback.seq
        val beforeTrack = party.playback.track?.videoId
        // Held from the moment the track ends, not from the moment this device
        // decides to speak. The grace below is time for another device's
        // control to arrive, and without this the tick would spend it pulling
        // the player back onto the track that just finished.
        reconcileQuietUntilMs = SystemClock.elapsedRealtime() + INTENT_QUIET_MS
        awaitSeq = Long.MAX_VALUE
        awaitQueueSeq = Long.MAX_VALUE

        publishJob?.cancel()
        publishJob = scope.launch {
            delay(AUTO_ADVANCE_GRACE_MS)
            val now = ListenTogether.state.value
            if (!now.inParty) return@launch
            val movedOn = now.playback.seq != beforeSeq ||
                now.playback.track?.videoId != beforeTrack
            if (movedOn) {
                // Somebody else got there first, so their control is the one to
                // follow — and this device has nothing outstanding to wait for,
                // which is what lets the window go immediately rather than
                // sitting out the rest of it.
                awaitSeq = now.playback.seq
                awaitQueueSeq = now.queue.seq
                reconcileQuietUntilMs = 0L
                reconcile()
                return@launch
            }
            publish()
        }
    }

    /**
     * The player's `playWhenReady` moved, and why.
     *
     * The *why* is the whole reason this exists and is only available here:
     * [Player] has no getter for it, so a pause caused by another app taking
     * the audio is indistinguishable, a tick later, from any other pause. It
     * has to be caught as it happens. See [focusLost].
     */
    fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady) {
            // Audible again by some route â€” a rejoin, a headset button, the
            // notification. Whatever it was, following the party is right.
            focusLost = false
            return
        }
        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS &&
            ListenTogether.state.value.inParty
        ) {
            Log.i(TAG, "another app took the audio; dropping out of the party until asked back")
            focusLost = true
            deferredPlayPending = false
            startJob?.cancel()
        }
    }

    /**
     * Whether a `play()` from this device should be held back for the party.
     *
     * True means the caller must *not* start the player: this class will, at the
     * instant the server schedules for everyone. That instant is a few hundred
     * milliseconds out, and the difference between honouring it and not is the
     * difference between a party and a device that is permanently ahead of one.
     * Starting here and letting drift correction sort it out does not work â€”
     * the head start a resume gives the device that issued it is smaller than
     * any drift threshold worth having, so it would never be corrected at all.
     *
     * False whenever the party could not schedule anything â€” no party, no
     * socket, no measured clock â€” in which case play behaves exactly as it does
     * outside this feature. The press is never simply swallowed: [reconcile]
     * starts the player on the echo, and [deferredPlayFallback] starts it anyway
     * if that echo never comes.
     */
    fun shouldDeferPlay(): Boolean {
        val party = ListenTogether.state.value
        if (!party.inParty || party.connection != ListenTogether.Connection.LIVE) return false
        if (!party.clockSynced) return false
        deferredPlayPending = true
        deferredPlayFallback()
        return true
    }

    /**
     * The press must do something even if the party cannot answer.
     *
     * A control can be lost, and a socket can be up in name only. Left to the
     * echo alone, that case is a play button that does nothing â€” far worse than
     * being briefly out of step, and impossible for the listener to diagnose.
     */
    private fun deferredPlayFallback() {
        startJob?.cancel()
        startJob = scope.launch {
            delay(DEFERRED_PLAY_TIMEOUT_MS)
            val exo = player() ?: return@launch
            if (deferredPlayPending && !exo.playWhenReady) {
                Log.w(TAG, "party never acknowledged the resume; starting locally")
                deferredPlayPending = false
                exo.play()
            }
        }
    }

    // ------------------------------------------------------------ inbound --

    /**
     * The party is about to take the player over, so the listener's own queue
     * is put aside first.
     *
     * Without this the join is destructive: the party's queue replaces whatever
     * was playing, and the album or playlist somebody had lined up is gone with
     * no way back to it but finding the page again. See
     * [PartyPersonalQueueStash].
     *
     * Nothing is stashed when there is nothing to stash — an empty player and no
     * last-played queue is a listener who had not started listening yet, and
     * there is no state of theirs to protect.
     */
    private fun onEnteredParty() {
        val exo = player() ?: return
        if (PartyPersonalQueueStash.stashFromPlayer(exo)) {
            Log.i(TAG, "entered party ${ListenTogether.state.value.code}; personal queue stashed")
        }
    }

    /**
     * The party has handed the player back, so the listener's own queue goes
     * where it was.
     *
     * Position and playing state are restored too, not just the list: coming
     * back to the right queue paused at the start would be a second, quieter
     * version of the same loss.
     *
     * The stash is only cleared once it has actually been applied. A restore
     * that could not run — no player yet, during a teardown — leaves it on disk
     * for the next launch, which is the same path a process death takes.
     */
    private fun onLeftParty() {
        val stashed = PartyPersonalQueueStash.load() ?: return
        val exo = player() ?: return
        Log.i(TAG, "left the party; restoring ${stashed.songs.size} personal queue item(s)")
        val items = stashed.songs.map { it.toMediaItem() }
        exo.setMediaItems(items, stashed.index, stashed.positionMs)
        exo.prepare()
        if (stashed.wasPlaying) exo.play() else exo.pause()
        PartyPersonalQueueStash.clear()
    }

    private fun reconcile() {
        val party = ListenTogether.state.value
        if (!party.inParty) {
            loadingVideoId = null
            focusLost = false
            rejoining = false
            // Nothing is going to schedule a start now, so a resume still
            // waiting on one is never answered — and the player screen would
            // draw that wait forever.
            deferredPlayPending = false
            locallyPaused = false
            return
        }
        // Before any of the early returns below, so a local pause cannot
        // outlive the thing it was held against.
        clearLocalPauseIfFreed(party)
        // An action of this device's that the party has not been told about
        // yet. Following the party from here would undo it in front of the
        // user. See [publishPending].
        if (publishPending) return
        if (SystemClock.elapsedRealtime() < reconcileQuietUntilMs) return
        // Another app has the audio. Following the party from here means seeking
        // this player into place and pressing play, which takes the audio back
        // off whatever the listener just started â€” so this device follows
        // nothing until its own user asks it to. See [focusLost].
        if (focusLost) return
        val target = party.playback
        val exo = player() ?: return

        // A party with nothing in it yet. Somebody has to put the first song
        // on, and until they do every member hears their own separate
        // playback — which reads as "we joined the same party and are hearing
        // different things". The device that has something playing seeds the
        // party with it, so the others join in on that rather than on silence.
        // Only the *first* one lands: the server's seq settles it, and by the
        // time the second device's frame arrives the party has a track and
        // this branch is gone.
        if (target.track == null) {
            seedParty(exo)
            return
        }
        val track = target.track ?: return

        // Suppressed, not stopped: a notification chime or a short clip holds
        // the audio for a moment and hands it straight back, with `playWhenReady`
        // never going false. The playhead is frozen meanwhile, so it reads as
        // drift that is not there and would be "corrected" by seeking a player
        // nobody can hear. It converges on its own the moment the audio returns.
        if (exo.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE) return

        // Playing something off this device. The party cannot follow a
        // content:// URI and this device should not be yanked off the file
        // somebody deliberately chose, so the two are simply left uncoupled
        // until playback returns to something with a catalogue id.
        if (exo.currentMediaItem?.toSong()?.isDeviceFile() == true) return

        // A playing party cannot be joined in time without a measured clock.
        // [PartyPlayback.positionMs] is the position at an anchor that may be
        // minutes old, so acting on it unsynced would not merely be imprecise â€”
        // it would start this device at wherever the song was when the last
        // control happened. Pausing needs no clock, so that still applies.
        if (target.isPlaying && !party.clockSynced) return

        if (exo.currentMediaItem?.mediaId != track.videoId) {
            load(party)
            return
        }
        loadingVideoId = null
        // The party's running order, applied to this player's timeline. Only
        // reached once the current track matches, because before that there is
        // no index in this player for the party's queue to line up against.
        reconcileQueue(party, exo)

        // Muted by its own listener while the party plays on. Everything above
        // this line still applies — the track the party moved to is loaded, the
        // queue behind it is kept — and everything below it is sound: starting
        // the player, and chasing a playhead nobody here can hear.
        if (locallyPaused) {
            // Nothing below here will start this device while the pause holds,
            // so a resume still waiting on the party is never going to be
            // answered — and the transport would draw that wait for as long as
            // the listener stayed muted.
            deferredPlayPending = false
            return
        }

        if (!target.isPlaying) {
            deferredPlayPending = false
            if (exo.playWhenReady) exo.pause()
            // Held where the party paused it, so that everybody resumes from the
            // same place rather than from wherever their own playhead stopped.
            if (abs(exo.currentPosition - target.positionMs) > PAUSED_TOLERANCE_MS) {
                exo.seekTo(target.positionMs)
            }
            return
        }

        // Safe to read as a real position from here down: the party is playing
        // and the clock has been measured, both checked above.
        val want = ListenTogether.partyPositionMs()
        if (!exo.playWhenReady) {
            val wait = ListenTogether.msUntilStart()
            if (wait > 0) {
                // The party's resume is scheduled, not immediate. Wake up for
                // it rather than waiting for the next tick, which could be most
                // of a tick late â€” and late is what this is here to avoid.
                startJob?.cancel()
                startJob = scope.launch {
                    delay(wait)
                    reconcile()
                }
                return
            }
            if (want != null) exo.seekTo(want)
            deferredPlayPending = false
            startJob?.cancel()
            exo.play()
            alignedSeq = target.seq
            return
        }

        if (want == null) return

        // Only a settled player can be measured. While it is buffering,
        // `currentPosition` is where it will resume rather than where it is, so
        // the party runs on and this reads as drift that is not there.
        if (exo.playbackState != Player.STATE_READY) {
            driftStrikes = 0
            return
        }

        val drift = exo.currentPosition - want
        // A control this device has not yet put itself on. Everyone is meant to
        // land on it exactly, so it is matched without regard to the drift
        // limit â€” including on the device that issued it, which is otherwise
        // left holding the head start that issuing it gave it.
        if (target.seq != alignedSeq) {
            alignedSeq = target.seq
            if (abs(drift) > ALIGN_TOLERANCE_MS) {
                Log.i(TAG, "aligning ${drift}ms onto party control ${target.seq}")
                exo.seekTo(want)
            }
            return
        }
        if (abs(drift) <= DRIFT_LIMIT_MS) {
            driftStrikes = 0
            return
        }
        // Seeking is not free, and on a slow device it is not cheap either: the
        // re-buffer it costs can be longer than the gap being closed, so the
        // correction arrives already as far behind as the error it was fixing,
        // and does it again, forever. That loop is real â€” it was measured here
        // at a flat -1402ms every 1.4s, to the millisecond, for as long as it
        // was left running.
        //
        // Two things keep it out. A correction has to be asked for twice in a
        // row before it happens, so one reading taken while the pipeline was
        // catching up cannot trigger anything; and after one, the player is left
        // alone long enough to settle and show what it is really doing.
        val now = SystemClock.elapsedRealtime()
        if (now < driftCooldownUntilMs) return
        if (++driftStrikes < DRIFT_STRIKES) return
        Log.i(TAG, "correcting ${drift}ms of drift against the party")
        driftStrikes = 0
        driftCooldownUntilMs = now + DRIFT_COOLDOWN_MS
        exo.seekTo(want)
    }

    /**
     * Puts what this device is playing into an empty party.
     *
     * The first person in a party has usually already pressed play on
     * something — that is often why they started one. Without this their music
     * stays local, everyone else's stays theirs, and the party looks broken
     * until somebody picks a song on purpose.
     *
     * Nothing is seeded from a device with no track of its own: there would be
     * nothing to say, and the party would simply stay empty until somebody
     * played something, which is the correct outcome.
     */
    private fun seedParty(exo: Player) {
        val song = exo.currentMediaItem?.toSong() ?: return
        // A file on this device cannot be handed to anybody else. See [publish].
        if (song.isDeviceFile()) return
        val track = song.toPartyTrack(exo.duration)
        val position = exo.currentPosition.coerceAtLeast(0L)
        val playing = exo.playWhenReady
        val queue = (0 until exo.mediaItemCount)
            .take(MAX_PUBLISHED_QUEUE)
            .map { exo.getMediaItemAt(it).toSong() }
            .filterNot(Song::isDeviceFile)
            .map { it.toPartyTrack(0L) }
        Log.i(TAG, "seeding an empty party with ${track.videoId}")
        // The queue first, for the same reason [publish] sends it first: a
        // track pointing into a running order nobody has is the bug that
        // played the wrong song.
        val sent = if (queue.isNotEmpty()) {
            ListenTogether.setQueue(queue, queue.indexOfFirst { it.videoId == track.videoId })
        } else {
            true
        } && ListenTogether.setTrack(track, position, playing)
        if (sent) {
            reconcileQuietUntilMs = SystemClock.elapsedRealtime() + INTENT_QUIET_MS
            // Two playback controls (queue and track) and one queue control. The
            // queue counter is what the party's queue frame answers to, and
            // leaving it unarmed here would have the quiet window sit out its
            // whole length on the one moment a party is being seeded.
            awaitSeq = ListenTogether.state.value.playback.seq + 2
            awaitQueueSeq = ListenTogether.state.value.queue.seq + 1
        }
    }

    /**
     * Puts the party's running order on this player and starts at its position.
     *
     * The queue comes across as well as the track so that next and previous
     * work locally and so that the next track is prefetched â€” a device that had
     * only the current song would stall at every change while it resolved a
     * stream from cold.
     */
    private fun load(party: ListenTogether.State) {
        val track = party.playback.track ?: return
        if (loadingVideoId == track.videoId) return
        loadingVideoId = track.videoId

        scope.launch {
            // The queue is only usable if the track is actually in it. It may
            // not be: the queue and the track are two controls, and between them
            // the party holds a new running order with the old song still
            // current. `indexOfFirst(...).coerceAtLeast(0)` turned that
            // not-found into index 0 and played whatever happened to be first â€”
            // a different song entirely, with nothing on screen to explain it.
            // Falling back to the track alone is always right; falling back to
            // position zero never is.
            val partyQueue = party.queue.items
            val index = partyQueue.indexOfFirst { it.videoId == track.videoId }
            val queue = if (index >= 0) partyQueue else listOf(track)
            val startIndex = if (index >= 0) index else 0
            // Off the main thread: building an item resolves artwork sizes and
            // asks [com.velthy.client.download.Downloads] whether each track is
            // already on disk, which is a stat per downloaded song. See
            // [MediaController.playSongs], which moves it for the same reason.
            val items = withContext(Dispatchers.Default) { queue.map { it.toSong().toMediaItem() } }
            val exo = player()
            if (exo == null) {
                // Nothing was applied, so the next tick has to be free to try
                // again rather than believing this track is already on its way.
                loadingVideoId = null
                return@launch
            }
            // Read after the build, not before: assembling a long queue takes
            // real time, and the party's playhead has moved on by exactly that
            // much. Taken beforehand, every track change would start this device
            // a little behind and then be hauled forward by a correcting seek.
            val startAt = ListenTogether.partyPositionMs() ?: party.playback.positionMs
            // A playhead past the end of the track being loaded is not a
            // position to start at. It happens when the party is a step behind
            // this device — its clock has run past the song it still calls
            // current — and a seek there lands on STATE_ENDED the instant the
            // item is prepared. The player then reports the track as finished
            // and advances, which is half of the loop [onAutoAdvance] exists to
            // break: it must not be reachable from here either.
            val start = if (track.durationMs?.let { it > 0L && startAt >= it } == true) 0L else startAt
            exo.setMediaItems(items, startIndex, start)
            exo.prepare()
            // Not started here even when the party is playing: the resume may be
            // scheduled a moment out, and [reconcile] owns that wait. Preparing
            // now is what makes this device ready to hit that instant.
            if (party.playback.isPlaying && ListenTogether.msUntilStart() <= 0L) exo.play()
            reconcile()
        }
    }

    // ----------------------------------------------------------- outbound --

    private fun publish() {
        val party = ListenTogether.state.value
        if (!party.inParty) return
        // Coming back from having lost the audio. This device is behind, and
        // possibly on a track the party left minutes ago â€” everything it could
        // say right now is stale, and every one of those is a control that would
        // move four other people backwards. So it says nothing and lets
        // [reconcile], which is no longer held off, bring it to the party.
        if (rejoining) {
            rejoining = false
            reconcileQuietUntilMs = 0L
            return
        }
        val exo = player() ?: return
        val song = exo.currentMediaItem?.toSong() ?: return
        // A file on this device is not something a party can play: it is
        // identified by a content:// or file:// URI that means nothing anywhere
        // else, and handing one out would have every other member fail to
        // resolve it. So local playback simply says nothing, and [reconcile]
        // leaves this device alone while it lasts â€” the party carries on with
        // what it was doing, and normal service resumes on the next track that
        // has a catalogue id. A *downloaded* track is not this case: it has a
        // real id and everybody else can stream it perfectly well.
        if (song.isDeviceFile()) return
        val track = song.toPartyTrack(exo.duration)
        val position = exo.currentPosition.coerceAtLeast(0L)
        // What the user asked for, which during a deferred resume is not what
        // the player is doing yet â€” that is the whole point of the deferral.
        val wantsPlaying = deferredPlayPending || exo.playWhenReady
        val base = party.playback.seq
        val baseQueue = party.queue.seq
        var controls = 0
        var queueControls = 0
        var sent = 0

        // Compared by id first, which is a plain field read per item. Building
        // the full list is not — it parses a metadata bundle per track — and
        // this runs on every pause and every seek, on a queue that can be
        // hundreds long.
        //
        // The player index is carried alongside the id, because the two stop
        // agreeing the moment a device file is dropped: the filtered list is
        // shorter than the player's, so a lookup in it is not an index into the
        // player, and every row after the first local file would be read off
        // the wrong item.
        val localItems = (0 until exo.mediaItemCount)
            .map { index -> index to exo.getMediaItemAt(index).mediaId }
            .filterNot { (_, id) -> id.startsWith("content://") || id.startsWith("file://") }
        val localIds = localItems.map { (_, id) -> id }
        val trackIndex = localIds.indexOf(track.videoId)
        // What the party is allowed to know about: everything up to the current
        // track, plus the songs immediately ahead of it. A device that has
        // played through a long album has a big tail behind it, and the party's
        // copy is about what is *coming*.
        val clampedItems = if (trackIndex >= 0) {
            localItems.subList(0, (trackIndex + 1 + MAX_PARTY_UPCOMING_QUEUE).coerceAtMost(localItems.size))
        } else {
            localItems.take(1 + MAX_PARTY_UPCOMING_QUEUE)
        }
        val clampedIds = clampedItems.map { (_, id) -> id }

        /** The songs behind a slice of [localItems], read off the player by index. */
        fun songsOf(items: List<Pair<Int, String>>): List<PartyTrack> = items
            .map { (playerIndex, _) -> exo.getMediaItemAt(playerIndex).toSong() }
            .filterNot(Song::isDeviceFile)
            .map { it.toPartyTrack(0L) }

        // Covers the ways a running order changes without the playhead moving —
        // Play next, Add to queue, removing a row, dragging one. Before this,
        // none of them reached the party and its copy of the queue silently went
        // stale until the next track change happened to rebuild it.
        val partyIds = party.queue.items.map(PartyTrack::videoId)
        if (clampedIds != partyIds) {
            // An append — which is what "add to queue", "play next" at the end
            // and an AutoPlay refill all come to — is sent as an addition rather
            // than as a whole new queue. The difference is not bytes: the server
            // *refuses* an addition past its ceiling with a reason, while a
            // replacement past it is silently truncated. A listener who queued
            // something has to be told when it did not fit.
            val appended = clampedIds.startsWith(partyIds) && partyIds.isNotEmpty()
            if (appended) {
                queueControls++
                if (ListenTogether.queueAdd(songsOf(clampedItems.drop(partyIds.size)))) sent++
            } else {
                // A reorder that moved exactly one row is sent as a move rather
                // than as a whole new queue: it is the same list, and replacing
                // it would have every listener's player rebuild its timeline —
                // and drop whatever it had prefetched — for a change that moved
                // one entry.
                val singleMove = if (
                    partyIds.size == clampedIds.size &&
                    trackIndex >= 0 &&
                    trackIndex < partyIds.size &&
                    partyIds[trackIndex] == clampedIds[trackIndex]
                ) {
                    detectSingleMove(partyIds, clampedIds)
                } else {
                    null
                }

                if (singleMove != null && singleMove.fromIndex > trackIndex && singleMove.toIndex > trackIndex) {
                    queueControls++
                    if (ListenTogether.queueMove(singleMove.fromIndex, singleMove.toIndex, singleMove.videoId)) sent++
                } else {
                    queueControls++
                    if (ListenTogether.setQueue(songsOf(clampedItems), trackIndex)) sent++
                }
            }
        }

        when {
            party.playback.track?.videoId != track.videoId -> {
                // After the queue, never before: a track change that arrives
                // pointing into a running order nobody has yet is the bug that
                // played the wrong song.
                controls++
                if (ListenTogether.setTrack(track, position, wantsPlaying)) sent++
            }
            party.playback.isPlaying != wantsPlaying -> {
                controls++
                val ok = if (wantsPlaying) {
                    ListenTogether.play(position)
                } else {
                    ListenTogether.pause(position)
                }
                if (ok) sent++
            }
            // Same track, same playing state â€” so what the user did was move
            // the playhead. Unless it did not move far, in which case this is
            // not a seek at all.
            //
            // The case that matters is two devices reaching the end of a track
            // at the same moment: both report the advance, the second one finds
            // the party already on the new track and would otherwise publish its
            // own position as a seek â€” which drags the first device, which
            // republishes, and so on. Nobody asked for any of it.
            else -> {
                val partyPosition = ListenTogether.partyPositionMs()
                if (partyPosition == null || abs(position - partyPosition) > SEEK_REPORT_FLOOR_MS) {
                    controls++
                    if (ListenTogether.seek(position)) sent++
                }
            }
        }

        // A control that never reached the socket is not one to wait for: the
        // party's seq will not move, so `awaitSeq` would hold [reconcile] off
        // for the whole quiet window and then haul the player back to the track
        // the user had just left. That is the song changing back by itself,
        // with nothing on screen to explain it. So the window is kept open
        // instead, and the next publish says the whole thing again.
        val total = controls + queueControls
        if (total > 0 && sent < total) {
            Log.w(TAG, "party socket is down; holding ${total - sent} of $total controls")
            publishPending = true
            awaitSeq = Long.MAX_VALUE
            awaitQueueSeq = Long.MAX_VALUE
            reconcileQuietUntilMs = SystemClock.elapsedRealtime() + INTENT_QUIET_MS
            return
        }

        // The party has caught up with this device once every control sent has
        // come back around. Nothing sent means nothing to wait for, and the
        // quiet window should stop holding reconcile off immediately.
        awaitSeq = base + controls
        awaitQueueSeq = baseQueue + queueControls
        if (total == 0) reconcileQuietUntilMs = 0L
    }

    private companion object {
        const val TAG = "PartySync"

        /**
         * How often the playhead is checked against the party's.
         *
         * Frequent enough that a device coming back from a stall is corrected
         * within about a second, and cheap: in-process field reads off the
         * ExoPlayer, no binder call and no allocation on the quiet path.
         */
        const val TICK_MS = 700L

        /**
         * How far out of step is worth an audible seek.
         *
         * Above the jitter of two decoders on different hardware, and at the
         * level of a real desync. Tightening this does not make a party more
         * synchronised â€” it makes it seek more often, which is what people
         * actually hear.
         */
        const val DRIFT_LIMIT_MS = 1_200L

        /** Readings in a row above the limit before a correction is made. */
        const val DRIFT_STRIKES = 2

        /**
         * How long the player is left alone after a corrective seek.
         *
         * Long enough to cover the re-buffer a seek costs on a slow device
         * plus room to show a settled position afterwards. This is the value
         * that decides whether correction converges or oscillates, so it is
         * deliberately generous: a party a second out for a few seconds is
         * fine, a party seeking every 1.4s is not listenable.
         */
        const val DRIFT_COOLDOWN_MS = 6_000L

        /** While paused there is nothing to hear, so the playhead can be exact. */
        const val PAUSED_TOLERANCE_MS = 400L

        /**
         * How exactly a device lands on a control. Low enough that nobody keeps
         * a head start worth hearing, high enough not to seek over the few tens
         * of milliseconds between asking the player where it is and it acting.
         */
        const val ALIGN_TOLERANCE_MS = 120L

        /**
         * Below this, a difference from the party's playhead is not a seek
         * anybody performed â€” it is two devices being normally, slightly apart.
         */
        const val SEEK_REPORT_FLOOR_MS = 1_000L

        /**
         * How long a deferred resume waits for the party before giving up on it.
         *
         * The echo normally lands in well under a hundred milliseconds, and the
         * wait this bounds is the case where it never does — a control dropped,
         * or a socket that is up in name only. Nothing is gained by being patient
         * there: the listener pressed play and is listening to silence, and every
         * extra millisecond is another one of those. Under a second, so a failed
         * resume reads as a beat rather than as the button not working, and still
         * several times the round trip a working party needs.
         */
        const val DEFERRED_PLAY_TIMEOUT_MS = 900L

        /**
         * Long enough to coalesce the burst one tap makes â€” choosing a track
         * is `setMediaItems`, `prepare`, `play` within a few milliseconds â€”
         * and no longer, because a resume is held until this has elapsed and
         * every millisecond here is silence after the button was pressed.
         */
        const val PUBLISH_DEBOUNCE_MS = 120L

        /**
         * How long a local action is protected from being reconciled away.
         *
         * Normally irrelevant â€” the server's echo arrives in well under this and
         * clears it early. It matters when a control is lost or the socket is
         * down, where it is the difference between the action being undone in
         * front of the user and it simply not reaching the others.
         */
        const val INTENT_QUIET_MS = 2_500L

        /**
         * How long an automatic advance waits before claiming the party's next
         * track for itself — see [onAutoAdvance].
         *
         * Two devices reaching the end of a track at the same instant is the
         * ordinary case, and both will want to publish the change. This is the
         * window in which the other one's control can arrive first, so the
         * device that speaks is whichever got there a moment sooner rather than
         * both of them.
         *
         * Deliberately short. Every millisecond here is a millisecond the rest
         * of the party spends still on the track that just ended, and the thing
         * being avoided — both devices publishing the same next track — is
         * harmless when it happens: the server's sequence number settles it and
         * both devices converge on the same answer either way. So this is a
         * saving of redundant traffic, not a correctness requirement, and it is
         * sized accordingly.
         */
        const val AUTO_ADVANCE_GRACE_MS = 200L

        /**
         * How many songs the party is told about ahead of the one playing.
         *
         * The same number the server enforces as its own ceiling. Publishing
         * more would only be truncated, and the part a shared queue is *for* is
         * what is coming next rather than a hundred songs of history.
         */
        const val MAX_PARTY_UPCOMING_QUEUE = 25

        /** The server's own ceiling; publishing more would only be truncated. */
        const val MAX_PUBLISHED_QUEUE = 1 + MAX_PARTY_UPCOMING_QUEUE
    }

    /**
     * Brings this player's timeline in line with the party's running order.
     *
     * Only the part *after* the current track is touched, and the current item
     * is deliberately outside every edit below. Replacing the whole timeline
     * would tear down the active player — the decoder, the audio renderer, the
     * prefetched next track — for a change that added one song to the end, and
     * on a slow device that is an audible stall on every edit.
     *
     * The three shapes below are the three things a queue edit can be, cheapest
     * first: something appended, something trimmed off the end, or one row moved
     * to another place. Anything else is a replacement.
     */
    private fun reconcileQueue(party: ListenTogether.State, exo: Player) {
        val partyQueue = party.queue.items
        if (partyQueue.isEmpty()) return
        val currentIndex = exo.currentMediaItemIndex
        val currentId = exo.currentMediaItem?.mediaId ?: return
        val partyIndex = partyQueue.indexOfFirst { it.videoId == currentId }
        // The party's queue does not contain what this device is playing, so
        // there is no index for it to line up against. Leaving the player alone
        // is right: [reconcile] will load the party's track on the next pass.
        if (partyIndex < 0) return

        val desired = partyQueue.subList(partyIndex + 1, partyQueue.size)
            .take(MAX_PARTY_UPCOMING_QUEUE)
        val desiredIds = desired.map(PartyTrack::videoId)
        val localIds = (currentIndex + 1 until exo.mediaItemCount).map {
            exo.getMediaItemAt(it).mediaId
        }
        if (localIds == desiredIds) return

        // Appending is the ordinary case — somebody added a song, or AutoPlay
        // extended the mix — and it never touches the active item.
        if (desiredIds.startsWith(localIds)) {
            exo.addMediaItems(desired.drop(localIds.size).map { it.toSong().toMediaItem() })
            return
        }
        // So does trimming the tail: "clear upcoming" and a shortened queue.
        if (localIds.startsWith(desiredIds)) {
            exo.removeMediaItems(currentIndex + 1 + desiredIds.size, exo.mediaItemCount)
            return
        }

        val singleMove = if (localIds.size == desiredIds.size) {
            detectSingleMove(localIds, desiredIds)
        } else {
            null
        }
        if (singleMove != null) {
            exo.moveMediaItem(
                currentIndex + 1 + singleMove.fromIndex,
                currentIndex + 1 + singleMove.toIndex,
            )
            return
        }

        // One atomic edit rather than a removal followed by an add: the empty
        // intermediate timeline that two calls leave behind can briefly rebuffer
        // or interrupt the renderer on some devices.
        exo.replaceMediaItems(
            currentIndex + 1,
            exo.mediaItemCount,
            desired.map { it.toSong().toMediaItem() },
        )
    }
}

/** Whether this list begins with exactly that one. */
private fun <T> List<T>.startsWith(prefix: List<T>): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

/** One row moved from somewhere to somewhere else. */
internal data class QueueMoveDelta(
    val fromIndex: Int,
    val toIndex: Int,
    val videoId: String,
)

/**
 * Whether [newList] is [oldList] with exactly one row moved, and if so which.
 *
 * This is what keeps a reorder from being published as a whole new queue: the
 * two lists hold the same songs in almost the same order, and sending the list
 * would have every listener's player rebuild its timeline for a change that
 * moved one entry. Null when the difference cannot be explained by a single
 * move — an addition, a removal, or several changes at once.
 */
internal fun detectSingleMove(oldList: List<String>, newList: List<String>): QueueMoveDelta? {
    if (oldList.size != newList.size || oldList == newList || oldList.isEmpty()) return null
    // Same songs, or it is not a move: this also rules out a duplicate id
    // changing hands, which the loop below would otherwise report as one.
    if (oldList.groupingBy { it }.eachCount() != newList.groupingBy { it }.eachCount()) return null

    for (from in oldList.indices) {
        val item = oldList[from]
        val without = oldList.toMutableList().apply { removeAt(from) }
        for (to in oldList.indices) {
            if (from == to) continue
            val simulated = without.toMutableList().apply { add(to, item) }
            if (simulated == newList) {
                return QueueMoveDelta(fromIndex = from, toIndex = to, videoId = item)
            }
        }
    }
    return null
}

/**
 * What the party needs to know about a track: enough to name it and show it.
 *
 * Everything else about how this device is playing it â€” which source answered,
 * at what quality, from the network or from a download â€” stays here, which is
 * what lets two people in a party be on different sources and still be in the
 * same place in the same song.
 */
/**
 * Whether this is a file on the device rather than a track from a catalogue.
 *
 * Asked of the *id*, not of where the bytes are coming from, and the difference
 * matters: a downloaded catalogue track also plays off disk, but it has a real
 * id, so every other device in the party can find and stream it. Only something
 * whose whole identity is a `content://` or `file://` URI is unshareable â€” that
 * URI names a row in this device's media store and nothing at all anywhere else.
 */
private fun Song.isDeviceFile(): Boolean =
    videoId.startsWith("content://") || videoId.startsWith("file://")

private fun Song.toPartyTrack(playerDurationMs: Long): PartyTrack = PartyTrack(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    // The player's own figure when it has one, since it comes from the decoder;
    // otherwise what the row that queued the track claimed.
    durationMs = playerDurationMs.takeIf { it > 0L }
        ?: TrackMatcher.secondsOf(durationText)?.let { it * 1000L },
    // Carried across so a listener's player keeps the boundary between what
    // somebody asked for and what the mix added — the queue panel draws its
    // AutoPlay heading on it, and a queue that lost the boundary puts the mix
    // above the listener's own picks.
    fromAutoplay = fromAutoplay,
)

private fun PartyTrack.toSong(): Song = Song(
    videoId = videoId,
    title = title,
    artist = artist,
    thumbnailUrl = thumbnailUrl,
    // Not cosmetic: this is what a cross-source match is made on, so a device
    // whose sources differ from the sender's needs it to find the same
    // recording. See [Song.matchQuery].
    durationText = durationMs?.let { ms ->
        val total = ms / 1000
        "%d:%02d".format(total / 60, total % 60)
    },
    fromAutoplay = fromAutoplay,
)
