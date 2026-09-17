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
import { BYE, MEMBERS, QUEUE, STATE, type JoinRequest } from '../common/protocol.js';
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
    const party = this.store.create();
    const member = party.join(body.userId, body.deviceId, body.displayName, body.avatarUrl);
    this.log.log(`party ${party.code} created by ${member.displayName}`);
    return this.membershipPayload(party, member);
  }

  /** Join an existing party, telling everyone already in it about the arrival. */
  join(code: string, body: JoinRequest): Record<string, unknown> {
    const party = this.store.get(code);
    const member = party.join(body.userId, body.deviceId, body.displayName, body.avatarUrl);
    this.log.log(
      `party ${party.code} joined by ${member.displayName} (${party.occupiedSlots}/${this.config.maxMembers})`,
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
      maxMembers: this.config.maxMembers,
      serverMs: nowMs(),
    };
  }
}
