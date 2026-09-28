/**
 * The service layer: the only thing controllers and the socket server talk to,
 * and the only place the shared singletons (store, hub, config) are held.
 *
 * Keeping it here rather than in the domain is what lets `party.ts` stay a pure
 * model with no framework in it — the same reason its sync tests need no Nest
 * container to run.
 */
import { Inject, Injectable, Logger } from '@nestjs/common';

import { nowMs } from '../common/clock.js';
import { CONFIG, type Config } from '../common/config.js';
import {
  ACTION_KICK,
  ACTION_NEXT,
  ACTION_PAUSE,
  ACTION_PLAY,
  ACTION_PREVIOUS,
  ACTION_QUEUE_ADD,
  ACTION_QUEUE_CLEAR,
  ACTION_QUEUE_MOVE,
  ACTION_QUEUE_REMOVE,
  ACTION_SEEK,
  ACTION_SET_HOST_ONLY_CONTROL,
  ACTION_SET_MAX_MEMBERS,
  ACTION_SET_QUEUE,
  ACTION_SET_TRACK,
  ACTIVITY,
  BYE,
  MEMBERS,
  QUEUE,
  STATE,
  type JoinRequest,
} from '../common/protocol.js';
import { Hub } from '../hub/hub.js';
import { Member, Party, PartyError, PartyStore } from './party.js';

@Injectable()
export class PartyService {
  private readonly log = new Logger('PartyService');
  readonly store: PartyStore;
  readonly hub: Hub;

  /**
   * The config is injected by explicit token rather than by parameter type.
   * Type-based injection depends on `emitDecoratorMetadata`, which only works
   * when TypeScript compiles the file — so it silently produces `undefined` on
   * the source-loaded development path. An explicit token works identically in
   * both, which is the whole point of one entry point for the two.
   */
  constructor(@Inject(CONFIG) readonly config: Config) {
    this.store = new PartyStore(config);
    this.hub = new Hub();
  }

  // ------------------------------------------------------------ REST ----

  /** Mint a code and put the caller in it as host. */
  create(body: JoinRequest): Record<string, unknown> {
    const party = this.store.create(body.maxMembers ?? undefined);
    const member = party.join(body.userId, body.deviceId, body.displayName, body.avatarUrl);
    this.log.log(
      `party ${party.code} created by ${member.displayName} (max ${party.maxMembers})`,
    );
    return this.membershipPayload(party, member);
  }

  /** Join an existing party, telling everyone already in it about the arrival. */
  join(code: string, body: JoinRequest): Record<string, unknown> {
    const party = this.store.get(code);
    const member = party.join(body.userId, body.deviceId, body.displayName, body.avatarUrl);
    this.log.log(
      `party ${party.code} joined by ${member.displayName} (${party.occupiedSlots}/${party.maxMembers})`,
    );
    // Everyone already in the party learns about the arrival now, rather than at
    // the next heartbeat — the member list is the one part of this feature that
    // is visible before any music plays.
    this.hub.broadcast(party.code, this.membersFrame(party));
    return this.membershipPayload(party, member);
  }

  /** The party's own snapshot, for a caller holding a valid token. */
  read(code: string, member: Member): Record<string, unknown> {
    const party = this.store.get(code);
    member.lastSeenMs = nowMs();
    return party.toWire();
  }

  /** Remove a member, ending the party when they were the last one. */
  leave(code: string, member: Member): { ok: true } {
    const party = this.store.get(code);
    party.remove(member.memberId);
    this.hub.send(party.code, member.memberId, { type: BYE, reason: 'left' });
    if (party.members.size === 0) {
      this.store.drop(party.code);
      this.hub.dropParty(party.code);
    } else {
      this.hub.broadcast(party.code, this.membersFrame(party));
    }
    return { ok: true };
  }

  /** Resolves the party and member behind a bearer token. */
  authenticate(code: string, authorization: string | undefined): { party: Party; member: Member } {
    const party = this.store.get(code);
    const token =
      authorization?.toLowerCase().startsWith('bearer ') === true
        ? authorization.slice(7).trim()
        : '';
    if (!token) throw new PartyError(401, 'no_token', 'Missing party token.');
    return { party, member: party.authenticate(token) };
  }

  // ------------------------------------------------------------ frames ----

  membershipPayload(party: Party, member: Member): Record<string, unknown> {
    return {
      code: party.code,
      // The one time this is ever sent. It is the device's key to the party for
      // as long as the membership lasts, so it lives in the client's own storage
      // and goes back as a bearer header or a socket query parameter.
      token: member.token,
      you: member.toWire(),
      party: party.toWire(),
      serverMs: nowMs(),
    };
  }

  stateFrame(party: Party): Record<string, unknown> {
    const now = nowMs();
    return { type: STATE, playback: party.playback.toWire(now), serverMs: now };
  }

  /** Sent when the queue changes, and when a client says its copy is stale. Never on the heartbeat. */
  queueFrame(party: Party): Record<string, unknown> {
    return { type: QUEUE, queue: party.playback.queueToWire(), serverMs: nowMs() };
  }

  membersFrame(party: Party): Record<string, unknown> {
    const wire = party.toWire();
    return {
      type: MEMBERS,
      members: wire['members'],
      maxMembers: party.maxMembers,
      hostOnlyControl: party.hostOnlyControl,
      serverMs: nowMs(),
    };
  }

  /**
   * One thing somebody did, in the words the party will read it in.
   *
   * Built from the control frame rather than from the resulting state, because
   * the sentence is about the *action*: "changed the song to X" is not
   * recoverable from a playback state that has since moved on. The detail is
   * composed here rather than on each device so every listener reads the same
   * sentence, and so the wording can change without a client release.
   */
  activityFrame(member: Member, action: string, frame: Record<string, unknown>): Record<string, unknown> {
    return {
      type: ACTIVITY,
      action,
      by: member.displayName,
      detail: describeAction(action, frame),
      atMs: nowMs(),
    };
  }

  /**
   * Who is in a party, to somebody who has not joined it.
   *
   * Unauthenticated on purpose, and the reason it is safe is that it says
   * nothing a person holding the code could not learn by joining: a name, a
   * face, and how full the party is. It exists so the invite in somebody's
   * hand can be *looked at* before a device slot is committed to it — and so a
   * full or expired party is refused while the code is still on screen, rather
   * than after a confirmation the listener cannot act on.
   */
  preview(code: string): Record<string, unknown> {
    const party = this.store.get(code);
    const members = [...party.members.values()]
      .sort((a, b) =>
        a.joinedAtMs !== b.joinedAtMs ? a.joinedAtMs - b.joinedAtMs : a.memberId.localeCompare(b.memberId),
      )
      .map((member) => ({
        displayName: member.displayName,
        avatarUrl: member.avatarUrl,
        isHost: member.isHost,
      }));
    const host = members.find((member) => member.isHost);
    return {
      code: party.code,
      hostName: host?.displayName ?? '',
      memberCount: party.members.size,
      maxMembers: party.maxMembers,
      isFull: party.members.size >= party.maxMembers,
      members,
    };
  }
}

/**
 * The sentence one action reads as.
 *
 * Deliberately a fixed set of phrasings rather than anything built from user
 * text: the title of a track goes in quotes as a *value*, and everything around
 * it is this server's own wording. A client cannot put arbitrary prose on
 * everybody else's screen through an activity frame.
 */
function describeAction(action: string, frame: Record<string, unknown>): string {
  const titleOf = (raw: unknown): string => {
    if (typeof raw !== 'object' || raw === null) return '';
    const title = (raw as Record<string, unknown>)['title'];
    return typeof title === 'string' ? title : '';
  };

  switch (action) {
    case ACTION_SET_TRACK: {
      const title = titleOf(frame['track']);
      return title ? `Changed the song to “${title}”` : 'Changed the song';
    }
    case ACTION_QUEUE_ADD: {
      const raw = frame['tracks'];
      if (Array.isArray(raw) && raw.length > 0) {
        const title = titleOf(raw[0]);
        if (title) {
          return raw.length === 1
            ? `Added “${title}” to the queue`
            : `Added “${title}” and ${raw.length - 1} more to the queue`;
        }
      }
      const single = titleOf(frame['track']);
      return single ? `Added “${single}” to the queue` : 'Added songs to the queue';
    }
    case ACTION_SET_QUEUE: {
      const raw = frame['queue'];
      return Array.isArray(raw)
        ? `Replaced the queue with ${raw.length} song${raw.length === 1 ? '' : 's'}`
        : 'Replaced the queue';
    }
    case ACTION_QUEUE_REMOVE:
      return 'Removed a song from the queue';
    case ACTION_QUEUE_CLEAR:
      return 'Cleared the upcoming songs';
    case ACTION_QUEUE_MOVE:
      return 'Reordered the upcoming songs';
    case ACTION_NEXT:
      return 'Skipped to the next song';
    case ACTION_PREVIOUS:
      return 'Went back to the previous song';
    case ACTION_PLAY:
      return 'Started playback';
    case ACTION_PAUSE:
      return 'Paused playback';
    case ACTION_SEEK:
      return 'Changed the playback position';
    case ACTION_KICK:
      return 'Removed a listener from the party';
    case ACTION_SET_MAX_MEMBERS:
      return 'Changed the party size';
    case ACTION_SET_HOST_ONLY_CONTROL:
      return 'Changed who can control the music';
    default:
      return 'Did something in the party';
  }
}
