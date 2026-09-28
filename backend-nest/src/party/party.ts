/**
 * The domain: what a party is, who is in it, and what it is playing.
 *
 * Nothing here touches HTTP or WebSockets — that is the controllers and the hub.
 * The point of the separation is that the sync rule, which is the whole feature,
 * is testable without a socket: a PlaybackState is a position, the server time
 * that position was true at, and whether the clock is running. Every device
 * derives its own playhead from those three numbers, so "in sync" is a property
 * of arithmetic rather than of message timing.
 *
 * The wire format is camelCase throughout, which is not this language's
 * convention either: the only consumer is the Android client, and matching
 * Kotlin's own naming there keeps a serialiser off every field of every model.
 */
import { randomBytes } from 'node:crypto';

import { nowMs } from '../common/clock.js';
import { isValid as isValidCode, normalise as normaliseCode, newCode } from '../common/codes.js';
import type { Config } from '../common/config.js';

/** A request refused for a reason the caller should be told. */
export class PartyError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
    this.name = 'PartyError';
  }
}

/** A song, in the only terms every device needs to agree on. */
export interface Track {
  videoId: string;
  title: string;
  artist: string;
  thumbnailUrl: string | null;
  durationMs: number | null;
}

/**
 * Everything else — stream URLs, the source that resolved them, quality
 * ceilings, download state — is each device's own business. Two people in a
 * party may be on different sources at different bitrates; what they share is
 * which song is playing and where the playhead is.
 *
 * Strings are bounded so a client cannot hand the server an unbounded value to
 * hold and rebroadcast to everyone else.
 */
export function trackFromWire(raw: unknown): Track | null {
  if (typeof raw !== 'object' || raw === null) return null;
  const record = raw as Record<string, unknown>;
  const videoId = str(record['videoId']);
  if (!videoId) return null;
  const duration = num(record['durationMs']);

  return {
    videoId: videoId.slice(0, 128),
    title: str(record['title']).slice(0, 300),
    artist: str(record['artist']).slice(0, 300),
    thumbnailUrl: str(record['thumbnailUrl']).slice(0, 1000) || null,
    durationMs: duration !== null && duration > 0 ? Math.trunc(duration) : null,
  };
}

/**
 * A number from a decoded frame, or null. Booleans are refused on purpose: JSON
 * `true` is not a position, and `Number(true) === 1` would quietly make it one.
 */
export function asInt(value: unknown): number | null {
  if (typeof value === 'boolean') return null;
  if (typeof value === 'number' && Number.isFinite(value)) return Math.trunc(value);
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return Math.trunc(parsed);
  }
  return null;
}

function str(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

function num(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

/**
 * Where the party is in what it is playing, as of a server timestamp.
 *
 * `positionMs` is not "the current position" — it is the position at `anchorMs`,
 * and it only becomes a current position once a device adds the time since. That
 * indirection is what survives the network: a packet delayed by 300 ms carries
 * an anchor 300 ms in the past and still lands the receiving device in exactly
 * the right place.
 */
export class PlaybackState {
  track: Track | null = null;
  queue: Track[] = [];
  queueIndex = -1;
  isPlaying = false;
  positionMs = 0;
  anchorMs = nowMs();
  /**
   * Bumped on every mutation. Clients drop any state whose seq they have already
   * passed, which is what makes two controllers pressing pause at the same
   * moment settle rather than oscillate.
   */
  seq = 0;
  /**
   * Bumped only when the *contents* of the queue change, which `seq` cannot
   * distinguish because it counts pauses and seeks too. It is what lets the
   * queue travel separately from the state, and what lets a client notice it has
   * missed a queue it was never sent.
   */
  queueSeq = 0;
  updatedBy: string | null = null;
  updatedAtMs = nowMs();
  /** The member who selected the current track. Not changed by pause/seek. */
  startedBy: string | null = null;
  startedByName: string | null = null;

  constructor(private readonly config: Config) {}

  /** The playhead this state implies at a given server time. */
  positionAt(serverMs: number): number {
    if (!this.isPlaying) return this.positionMs;
    // Clamped at zero because an anchor is allowed to be in the future: a resume
    // schedules the start slightly ahead so every device aims at one instant
    // rather than at its own arrival time. Before that instant the party holds,
    // which is exactly what a device that has already buffered should show.
    const elapsed = Math.max(0, serverMs - this.anchorMs);
    const position = this.positionMs + elapsed;
    const duration = this.track?.durationMs;
    return duration != null ? Math.min(position, duration) : position;
  }

  private touch(memberId: string | null): void {
    this.seq += 1;
    this.updatedBy = memberId;
    this.updatedAtMs = nowMs();
  }

  /**
   * Marks the queue as changed, without touching [seq].
   *
   * The two counters are separate for a reason the client depends on: [seq] is
   * "the playhead moved", and a device reacts to it by re-aligning its own
   * player exactly. A queue edit is not that — nobody's playhead moved, and
   * treating it as one made every listener seek on somebody else's reorder.
   * The queue's own counter is what tells a client its copy is stale.
   */
  private touchQueue(memberId: string | null): void {
    this.queueSeq += 1;
    this.updatedBy = memberId;
    this.updatedAtMs = nowMs();
  }

  play(memberId: string | null, positionMs: number | null): void {
    const now = nowMs();
    const start = positionMs ?? this.positionAt(now);
    this.positionMs = Math.max(0, start);
    // The lead time is the difference between "everyone starts when their packet
    // lands" and "everyone starts together". Anchoring a few hundred ms out gives
    // every device a common instant to aim at, and time to have buffered by it.
    this.isPlaying = true;
    this.anchorMs = now + this.config.playLeadMs;
    this.touch(memberId);
  }

  /**
   * Where the *party* was, not where the pausing device's own playhead happened
   * to be. Taking the client's number would fold that one device's drift into
   * the state everybody else then corrects to.
   */
  pause(memberId: string | null, positionMs: number | null): void {
    const now = nowMs();
    this.positionMs = Math.max(0, positionMs ?? this.positionAt(now));
    this.isPlaying = false;
    this.anchorMs = now;
    this.touch(memberId);
  }

  /**
   * A seek *is* the client's number — it is a user intent, not a measurement —
   * so unlike pause it is taken as given.
   */
  seek(memberId: string | null, positionMs: number): void {
    this.positionMs = Math.max(0, positionMs);
    this.anchorMs = nowMs() + (this.isPlaying ? this.config.playLeadMs : 0);
    this.touch(memberId);
  }

  setTrack(
    memberId: string | null,
    track: Track | null,
    positionMs = 0,
    isPlaying = true,
    queueIndex?: number | null,
    memberName?: string | null,
  ): void {
    this.track = track;
    this.positionMs = Math.max(0, positionMs);
    this.isPlaying = isPlaying && track !== null;
    this.anchorMs = nowMs() + (this.isPlaying ? this.config.playLeadMs : 0);
    if (queueIndex != null) {
      this.queueIndex = queueIndex;
    } else if (track !== null) {
      this.queueIndex = this.queue.findIndex((item) => item.videoId === track.videoId);
    }
    this.startedBy = memberId;
    this.startedByName = memberName ?? null;
    this.touch(memberId);
  }

  setQueue(memberId: string | null, queue: Track[], queueIndex: number): void {
    // A track that is playing is kept as the current one even when the sender's
    // index says otherwise: the two are separate controls, and a queue arriving
    // mid-flight from a device that has not yet published its track change would
    // otherwise move the party to a different song by itself.
    if (this.track !== null) {
      const match = queue.findIndex((item) => item.videoId === this.track?.videoId);
      if (match >= 0) queueIndex = match;
    }

    if (queueIndex >= 0 && queueIndex < queue.length) {
      // Past and current are kept whole; upcoming is capped. A device that has
      // played through an album has a long tail behind it and no reason to lose
      // it — the queue panel scrolls back — while the part that matters for
      // "what is next" is bounded.
      const pastAndCurrent = queue.slice(0, queueIndex + 1);
      const upcoming = queue.slice(queueIndex + 1, queueIndex + 1 + this.config.maxUpcomingQueue);
      this.queue = [...pastAndCurrent, ...upcoming];
      this.queueIndex = queueIndex;
    } else {
      this.queue = queue.slice(0, this.config.maxQueueLength);
      this.queueIndex =
        queueIndex >= 0 && queueIndex < this.queue.length ? queueIndex : -1;
    }
    this.touchQueue(memberId);
  }

  /**
   * How many songs are ahead of the one playing, counting from the whole queue
   * when nothing is current — which is the state a party is in before its first
   * track, and one where everything in the queue is still to come.
   */
  private upcomingCount(): number {
    if (this.queueIndex < 0) return this.queue.length;
    return Math.max(0, this.queue.length - 1 - this.queueIndex);
  }

  /**
   * Adds songs to the queue, at the end or right after the current one.
   *
   * Refused rather than truncated when there is no room, because the sender is
   * a person who just pressed "add to queue": silently dropping the song they
   * picked, with nothing on screen to say so, is worse than saying the queue is
   * full.
   */
  addUpcoming(
    memberId: string | null,
    tracks: Track[],
    playNext = false,
  ): { ok: true } | { ok: false; reason: string } {
    const slotsLeft = this.config.maxUpcomingQueue - this.upcomingCount();
    if (slotsLeft <= 0) return { ok: false, reason: 'queue_full' };
    if (tracks.length === 0) return { ok: false, reason: 'no_tracks' };

    const toAdd = tracks.slice(0, slotsLeft);
    if (playNext && this.queueIndex >= 0 && this.queueIndex < this.queue.length) {
      const at = this.queueIndex + 1;
      this.queue = [...this.queue.slice(0, at), ...toAdd, ...this.queue.slice(at)];
    } else {
      this.queue = [...this.queue, ...toAdd];
    }
    this.touchQueue(memberId);
    return { ok: true };
  }

  /**
   * Removes one song by id.
   *
   * The current track is not removable: it is what the party is listening to,
   * and taking it out would leave the state pointing at an index that no longer
   * exists. "Remove" on that row is a different intent — skipping — and it has
   * its own control.
   */
  removeUpcoming(memberId: string | null, videoId: string): boolean {
    const match = this.queue.findIndex((item) => item.videoId === videoId);
    if (match < 0 || match === this.queueIndex) return false;
    this.queue = [...this.queue.slice(0, match), ...this.queue.slice(match + 1)];
    if (this.queueIndex > match) this.queueIndex -= 1;
    this.touchQueue(memberId);
    return true;
  }

  /** Drops everything after the current track. */
  clearUpcoming(memberId: string | null): boolean {
    if (this.queueIndex >= 0) {
      if (this.queue.length <= this.queueIndex + 1) return false;
      this.queue = this.queue.slice(0, this.queueIndex + 1);
    } else {
      if (this.queue.length === 0) return false;
      this.queue = [];
    }
    this.touchQueue(memberId);
    return true;
  }

  /**
   * Moves one upcoming song to another position.
   *
   * Both ends are required to be *after* the current track. A reorder is a
   * statement about what is coming, and letting one reach back past the needle
   * would move a song the party is part-way through — a different operation,
   * with a different name, that nobody asked for.
   */
  moveUpcoming(
    memberId: string | null,
    fromIndex: number,
    toIndex: number,
    videoId: string,
  ): boolean {
    let from = fromIndex;
    if (videoId !== '') {
      const match = this.queue.findIndex((item) => item.videoId === videoId);
      if (match < 0) return false;
      from = match;
    }
    if (from < 0 || from >= this.queue.length) return false;
    if (toIndex < 0 || toIndex >= this.queue.length) return false;
    if (from <= this.queueIndex || toIndex <= this.queueIndex) return false;
    if (from === toIndex) return true;

    const item = this.queue[from];
    const without = [...this.queue.slice(0, from), ...this.queue.slice(from + 1)];
    this.queue = [...without.slice(0, toIndex), item, ...without.slice(toIndex)];
    this.touchQueue(memberId);
    return true;
  }

  /** Next (`delta` +1) or previous (`delta` -1). False when the queue has nowhere to go. */
  step(memberId: string | null, delta: number, memberName: string | null = null): boolean {
    const target = this.queueIndex + delta;
    if (target < 0 || target >= this.queue.length) return false;
    this.setTrack(memberId, this.queue[target], 0, true, target, memberName);
    return true;
  }

  /**
   * The playback state — deliberately without the queue in it.
   *
   * This frame goes to every device every few seconds, forever, and the queue is
   * the one field in it that is both large and almost never different. Sending a
   * 50-track queue twelve times a minute to each member was about twenty times
   * the bytes of everything else here combined, paid continuously, on phones. So
   * it travels on its own (see `queueToWire`) and this carries only `queueSeq` —
   * enough for a client to notice its copy is stale and ask, and nothing more.
   */
  toWire(serverMs = nowMs()): Record<string, unknown> {
    return {
      seq: this.seq,
      track: this.track,
      queueSeq: this.queueSeq,
      queueIndex: this.queueIndex,
      queueLength: this.queue.length,
      isPlaying: this.isPlaying,
      positionMs: this.positionMs,
      anchorMs: this.anchorMs,
      // Redundant with the three fields above, and worth the bytes: it is what a
      // log, a test, or a human reading a frame needs in order to tell "everyone
      // is at 1:03" from "everyone agrees on the formula".
      effectivePositionMs: this.positionAt(serverMs),
      updatedBy: this.updatedBy,
      startedBy: this.startedBy,
      startedByName: this.startedByName,
      updatedAtMs: this.updatedAtMs,
    };
  }

  /** The queue, sent on joining and thereafter only when it changes. */
  queueToWire(): Record<string, unknown> {
    return { seq: this.queueSeq, index: this.queueIndex, items: this.queue };
  }
}

/**
 * One signed-in device in a party.
 *
 * Identity comes from the app's own account layer: `userId` is derived from the
 * signed-in Google/YouTube profile, and a request without one is refused — that
 * is what "you have to be signed in to jam" means on this side of the wire. The
 * server does not and cannot verify that claim, so treat it as an assertion the
 * client makes, not as proof. What *is* server-held is `token`: minted here,
 * never guessable, and required on every subsequent call. So a member can lie
 * about who they are, but cannot act as a member they are not.
 */
export class Member {
  controlBudget: number;
  controlBudgetAt = nowMs();
  connected = false;
  lastSeenMs = nowMs();

  constructor(
    readonly memberId: string,
    public userId: string,
    readonly deviceId: string,
    public displayName: string,
    public avatarUrl: string | null,
    public token: string,
    public isHost: boolean,
    readonly joinedAtMs: number,
    controlRatePerSecond: number,
  ) {
    this.controlBudget = controlRatePerSecond;
  }

  toWire(): Record<string, unknown> {
    return {
      memberId: this.memberId,
      userId: this.userId,
      displayName: this.displayName,
      avatarUrl: this.avatarUrl,
      isHost: this.isHost,
      connected: this.connected,
      joinedAtMs: this.joinedAtMs,
      lastSeenMs: this.lastSeenMs,
    };
  }
}

/** One code, its members, and its playback. */
export class Party {
  readonly members = new Map<string, Member>();
  readonly playback: PlaybackState;
  readonly createdAtMs = nowMs();
  /** Last time anything happened — a join, a control, a heartbeat. Drives the sweep. */
  touchedAtMs = nowMs();
  /** When the last connected member dropped off, or null while someone is on. */
  emptySinceMs: number | null = nowMs();
  /**
   * Whether only the host may drive the music.
   *
   * Held here rather than on the playback state because it changes rarely and
   * `PlaybackState.toWire` rides the heartbeat to every device every few
   * seconds. It travels with the member list instead, which is only sent when
   * something about the membership actually changed.
   *
   * The default is the behaviour this feature shipped with: everyone controls
   * the music, and a party that never touches the setting never notices it.
   */
  hostOnlyControl = false;
  /**
   * This party's own ceiling, which may differ from the server default because
   * the creator chose it. Rejoining an existing party does not change it.
   */
  maxMembers: number;

  constructor(
    readonly code: string,
    private readonly config: Config,
    maxMembers?: number,
  ) {
    this.playback = new PlaybackState(config);
    this.maxMembers =
      maxMembers !== undefined && maxMembers >= 2 && maxMembers <= 10
        ? maxMembers
        : config.maxMembers;
  }

  get occupiedSlots(): number {
    return this.members.size;
  }

  /**
   * Admit a device, or hand back the slot it already holds.
   *
   * Rejoining is not a second membership. A reinstall, a force-stop, a lost
   * WebSocket that the grace period outlived — all of them come back through
   * this path, and a party of five would otherwise fill up with ghosts of the
   * same five devices.
   */
  join(userId: string, deviceId: string, displayName: string, avatarUrl: string | null): Member {
    for (const member of this.members.values()) {
      if (member.deviceId === deviceId) {
        member.displayName = displayName;
        member.avatarUrl = avatarUrl;
        member.userId = userId;
        member.lastSeenMs = nowMs();
        // A fresh token: the old one may be on a device that lost the session,
        // and a membership should only ever have one live key.
        member.token = tokenUrlSafe(24);
        this.touch();
        return member;
      }
    }

    if (this.members.size >= this.maxMembers) {
      throw new PartyError(409, 'party_full', `This party is full (${this.maxMembers} devices).`);
    }

    const member = new Member(
      randomBytes(8).toString('hex'),
      userId,
      deviceId,
      displayName,
      avatarUrl,
      tokenUrlSafe(24),
      this.members.size === 0,
      nowMs(),
      this.config.controlRatePerSecond,
    );
    this.members.set(member.memberId, member);
    this.touch();
    return member;
  }

  /** The member holding `token`, or an error. */
  authenticate(token: string): Member {
    for (const member of this.members.values()) {
      if (timingSafeEqual(member.token, token)) return member;
    }
    throw new PartyError(401, 'bad_token', 'This device is not a member of that party.');
  }

  /**
   * Resizes the party. Host only, and refused by the server from anybody else —
   * a listener who could raise the ceiling is not governed by it.
   *
   * The new size cannot be below the number of people already in: shrinking a
   * party past its own membership would mean evicting somebody as a side effect
   * of a settings change.
   */
  setMaxMembers(member: Member, maxMembers: number): void {
    if (!member.isHost) {
      throw new PartyError(403, 'host_only', 'Only the host can change the party size.');
    }
    if (!Number.isFinite(maxMembers) || maxMembers < 2 || maxMembers > 10) {
      throw new PartyError(422, 'invalid_capacity', 'Party size must be between 2 and 10.');
    }
    if (maxMembers < this.members.size) {
      throw new PartyError(
        409,
        'party_too_small',
        'Party size cannot be smaller than the current member count.',
      );
    }
    this.maxMembers = Math.trunc(maxMembers);
    this.touch();
  }

  /**
   * Restricts the music to the host, or hands it back to everyone.
   *
   * Host only, like [setMaxMembers] and for the same reason: a listener who
   * could turn this off is not restricted by it.
   */
  setHostOnlyControl(member: Member, enabled: boolean): void {
    if (!member.isHost) {
      throw new PartyError(403, 'host_only', 'Only the host can change who controls the music.');
    }
    if (this.hostOnlyControl === enabled) return;
    this.hostOnlyControl = enabled;
    this.touch();
  }

  /**
   * Whether this member is allowed to drive playback and the queue right now.
   *
   * The host always may. Everyone else may until the host says otherwise —
   * which is the behaviour this feature shipped with and stays the default for
   * a party that never touches the setting.
   *
   * The host is reassigned when a host leaves (see [remove]), so a listener can
   * find themselves promoted mid-party and immediately allowed through here.
   */
  mayControl(member: Member): boolean {
    return !this.hostOnlyControl || member.isHost;
  }

  /**
   * Drops a member. The host leaving hands the party on rather than ending it:
   * everyone else is still listening, and the only thing the role carries is who
   * gets asked to leave last.
   */
  remove(memberId: string): void {
    const member = this.members.get(memberId);
    if (!member) return;
    this.members.delete(memberId);
    if (member.isHost) {
      const successor = this.members.values().next().value as Member | undefined;
      if (successor) successor.isHost = true;
    }
    this.touch();
    this.refreshEmptiness();
  }

  /** One token from the member's bucket; false when they have run dry. */
  spendControlBudget(member: Member): boolean {
    const now = nowMs();
    const elapsedSeconds = Math.max(0, now - member.controlBudgetAt) / 1000;
    member.controlBudget = Math.min(
      this.config.controlRatePerSecond,
      member.controlBudget + elapsedSeconds * this.config.controlRatePerSecond,
    );
    member.controlBudgetAt = now;
    if (member.controlBudget < 1) return false;
    member.controlBudget -= 1;
    return true;
  }

  touch(): void {
    this.touchedAtMs = nowMs();
  }

  markConnected(member: Member, connected: boolean): void {
    member.connected = connected;
    member.lastSeenMs = nowMs();
    this.touch();
    this.refreshEmptiness();
  }

  private refreshEmptiness(): void {
    for (const member of this.members.values()) {
      if (member.connected) {
        this.emptySinceMs = null;
        return;
      }
    }
    if (this.emptySinceMs === null) this.emptySinceMs = nowMs();
  }

  /** Members whose disconnect grace has run out. */
  expiredMembers(now: number): Member[] {
    const out: Member[] = [];
    for (const member of this.members.values()) {
      if (!member.connected && now - member.lastSeenMs > this.config.disconnectGraceMs) {
        out.push(member);
      }
    }
    return out;
  }

  isExpired(now: number): boolean {
    if (now - this.createdAtMs > this.config.partyMaxAgeMs) return true;
    // An empty party is not immediately a dead one, and a brand new party is
    // empty by definition — the code is minted first and the creator joins
    // against it. Both cases fall to the same grace below rather than to a
    // shortcut, which would let the sweeper delete a code in the window between
    // handing it out and the creator using it.
    if (this.emptySinceMs !== null) {
      return now - this.emptySinceMs > this.config.emptyPartyTtlMs;
    }
    return false;
  }

  /** The whole party snapshot sent on join. */
  toWire(): Record<string, unknown> {
    const now = nowMs();
    return {
      code: this.code,
      createdAtMs: this.createdAtMs,
      maxMembers: this.maxMembers,
      // Sent here as well as on the members frame: a device arriving has to
      // learn the setting from the same snapshot as everything else, or its
      // first frames would draw controls the party is not offering.
      hostOnlyControl: this.hostOnlyControl,
      members: this.orderedMembers().map((member) => member.toWire()),
      playback: this.playback.toWire(now),
      // A snapshot is the one place the queue always travels: it is what a
      // device arriving has no other way to learn.
      queue: this.playback.queueToWire(),
      serverMs: now,
    };
  }

  private orderedMembers(): Member[] {
    return [...this.members.values()].sort((a, b) =>
      a.joinedAtMs !== b.joinedAtMs ? a.joinedAtMs - b.joinedAtMs : a.memberId.localeCompare(b.memberId),
    );
  }
}

/**
 * Every live party, in this process's memory.
 *
 * In memory on purpose, and the one thing to know before scaling this: a party
 * lives entirely inside the instance that is holding its WebSockets, so two
 * instances would be two disjoint sets of parties and a join would land in
 * whichever one the load balancer picked. That means a single instance with no
 * autoscaling — the right shape for this anyway, since a party is at most five
 * devices and the state is a few hundred bytes. Outgrowing it is a Redis
 * pub/sub swap behind this class, not a rewrite of anything above it.
 */
export class PartyStore {
  private readonly parties = new Map<string, Party>();

  constructor(private readonly config: Config) {}

  get size(): number {
    return this.parties.size;
  }

  create(maxMembers?: number): Party {
    for (let attempt = 0; attempt < 12; attempt += 1) {
      const code = newCode();
      if (!this.parties.has(code)) {
        const party = new Party(code, this.config, maxMembers);
        this.parties.set(code, party);
        return party;
      }
    }
    // 33**6 possibilities against a handful of live parties: twelve collisions
    // in a row is not luck, it is a broken RNG, and carrying on would mean
    // handing somebody a code into a stranger's party.
    throw new PartyError(503, 'code_exhausted', 'Could not allocate a party code.');
  }

  get(code: string): Party {
    const party = this.parties.get(normaliseCode(code));
    if (!party) throw new PartyError(404, 'no_such_party', 'No party with that code.');
    return party;
  }

  find(code: string): Party | undefined {
    return this.parties.get(normaliseCode(code));
  }

  drop(code: string): void {
    this.parties.delete(code);
  }

  all(): Party[] {
    return [...this.parties.values()];
  }

  /** Evicts timed-out members and dead parties. Returns parties that changed. */
  sweep(now: number): Party[] {
    const changed: Party[] = [];
    for (const [code, party] of this.parties) {
      const gone = party.expiredMembers(now);
      for (const member of gone) party.remove(member.memberId);
      if (party.isExpired(now)) {
        this.parties.delete(code);
        continue;
      }
      if (gone.length > 0) changed.push(party);
    }
    return changed;
  }
}

/** Whether a typed code is well-formed, for the join route. */
export function isWellFormedCode(code: string): boolean {
  return isValidCode(normaliseCode(code));
}

function tokenUrlSafe(bytes: number): string {
  return randomBytes(bytes).toString('base64url');
}

/** A constant-time comparison for secrets of the same length. */
function timingSafeEqual(a: string, b: string): boolean {
  if (typeof a !== 'string' || typeof b !== 'string' || a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i += 1) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}
