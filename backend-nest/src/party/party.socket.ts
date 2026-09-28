/**
 * The WebSocket half — the feature.
 *
 * It carries controls up, state down, and the clock samples that let each device
 * translate the server's timeline into its own. A client that only ever polled
 * `GET /api/parties/{code}` would still work and would still be in sync, just
 * coarsely; this is what makes a pause land on five phones at once rather than
 * within a second or so of each other.
 *
 * ### Why this is not a Nest `@WebSocketGateway`
 *
 * The Nest WS adapter wraps messages in its own `{ event, data }` envelope and
 * dispatches on `message.event`. This protocol's frames are flat — `{ "type":
 * "ping", "clientMs": … }` — because they are shared byte-for-byte with the
 * reference server and the Android client that already speaks to it. Adopting
 * the envelope would mean a second wire format and a client that has to know
 * which server it is talking to. So the socket is handled directly here, on the
 * `ws` library, and Nest owns only the REST half — which is where the framework
 * actually earns its place.
 *
 * The upgrade is accepted by hand (`noServer: true`) so the party code in the
 * path can be read *before* the socket is opened: a code that does not exist is
 * refused with a close code and a reason, rather than accepted and then dropped.
 */
import { Inject, Injectable, Logger, type OnApplicationShutdown } from '@nestjs/common';
import type { IncomingMessage, Server as HttpServer } from 'node:http';
import type { Duplex } from 'node:stream';
import { WebSocketServer, type WebSocket } from 'ws';

import { nowMs } from '../common/clock.js';
import { normalise } from '../common/codes.js';
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
  BYE,
  CONTROL,
  CONTROL_ACTIONS,
  ERROR,
  PING,
  PONG,
  REPORT,
  SYNC,
  SYNC_QUEUE,
  WELCOME,
} from '../common/protocol.js';
import { asInt, PartyError, trackFromWire, type Member, type Party, type Track } from './party.js';
import { PartyService } from './party.service.js';

/** The path prefix the app dials: `/ws/parties/{code}?token=…`. */
const WS_PREFIX = '/ws/parties/';

/** How far a reported playhead may sit from the party's before it is logged. */
const DRIFT_TOLERANCE_MS = 1_500;

interface Attachment {
  party: Party;
  member: Member;
}

@Injectable()
export class PartySocketServer implements OnApplicationShutdown {
  private readonly log = new Logger('PartySocket');
  private readonly server = new WebSocketServer({ noServer: true });
  /** Per-socket association, so a frame does not re-look-up its member. */
  private readonly attached = new WeakMap<WebSocket, Attachment>();

  /** Injected by explicit token; see `PartyService` for why not by type. */
  constructor(@Inject(PartyService) private readonly parties: PartyService) {}

  /**
   * Binds the upgrade handler to the HTTP server Nest created.
   *
   * `ws` with `noServer` never listens on its own, so this is the one place the
   * two halves are joined — and the only place a raw socket exists before it is
   * attached to a party.
   */
  attachTo(httpServer: HttpServer): void {
    httpServer.on('upgrade', (request, socket, head) => {
      const url = new URL(request.url ?? '/', 'http://localhost');
      if (!url.pathname.startsWith(WS_PREFIX)) {
        // Not ours: leave the socket for anything else that may want it. A
        // destroy here would break a second upgrade listener on the same server.
        socket.destroy();
        return;
      }
      const token = url.searchParams.get('token') ?? '';
      const rawCode = url.pathname.slice(WS_PREFIX.length).split('/')[0] ?? '';

      const party = this.parties.store.find(normalise(rawCode));
      if (!party) {
        reject(socket, 404, 'Not Found', 'no_such_party', 'No party with that code.');
        return;
      }

      let member: Member;
      try {
        member = party.authenticate(token);
      } catch (error) {
        const code = error instanceof PartyError ? error.code : 'bad_token';
        const message =
          error instanceof PartyError ? error.message : 'This device is not a member of that party.';
        reject(socket, 401, 'Unauthorized', code, message);
        return;
      }

      this.server.handleUpgrade(request, socket, head, (ws) => {
        this.server.emit('connection', ws, request);
        this.onConnection(ws, party, member);
      });
    });

    this.log.log(`websocket upgrade listening on ${WS_PREFIX}{code}`);
  }

  /**
   * The join handshake, once the socket is open.
   *
   * A socket that failed the path or token check never reaches here — it was
   * refused at the upgrade, which is what avoids ever leaving a socket open
   * receiving nothing. On a phone, that is a radio kept awake for nothing.
   */
  private onConnection(ws: WebSocket, party: Party, member: Member): void {
    this.attached.set(ws, { party, member });
    this.parties.hub.attach(party.code, member.memberId, ws);
    party.markConnected(member, true);

    this.send(ws, {
      type: WELCOME,
      you: member.toWire(),
      party: party.toWire(),
      serverMs: nowMs(),
    });
    this.parties.hub.broadcast(party.code, this.parties.membersFrame(party), member.memberId);

    ws.on('message', (data) => this.onMessage(ws, data.toString()));

    ws.on('close', () => {
      this.attached.delete(ws);
      this.parties.hub.detach(party.code, member.memberId, ws);
      // Still a member, just not currently holding a socket: the grace period is
      // what turns this into an actual departure, so a tunnel or a screen-off
      // does not empty the room.
      party.markConnected(member, false);
      this.parties.hub.broadcast(party.code, this.parties.membersFrame(party));
    });

    ws.on('error', (error) => {
      // A dead peer is routine here, not exceptional.
      this.log.debug(`socket error in ${party.code}: ${error.message}`);
    });
  }

  /**
   * One frame from one device. Anything malformed costs *this frame* its turn,
   * never the socket and never the party — a client bug should not be able to
   * bring a room down.
   */
  private onMessage(ws: WebSocket, raw: string): void {
    const attachment = this.attached.get(ws);
    if (!attachment) return;
    let frame: Record<string, unknown>;
    try {
      const parsed: unknown = JSON.parse(raw);
      if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) return;
      frame = parsed as Record<string, unknown>;
    } catch {
      return;
    }

    const { party, member } = attachment;
    member.lastSeenMs = nowMs();

    switch (frame['type']) {
      case PING:
        // Echoed back unread. The client's own send timestamp is what lets it
        // pair the reply with the request and halve the round trip; the server
        // has no use for it and no business interpreting it.
        this.send(ws, { type: PONG, clientMs: frame['clientMs'] ?? null, serverMs: nowMs() });
        return;

      case SYNC:
        this.send(ws, this.parties.stateFrame(party));
        return;

      case SYNC_QUEUE:
        this.send(ws, this.parties.queueFrame(party));
        return;

      case REPORT: {
        // A device saying where it actually is. Nothing is done with it beyond
        // keeping the membership alive and making drift visible in the log — the
        // server's state is the truth, not an average of what devices report, or
        // a straggler on a bad connection would drag the party to it.
        const reported = asInt(frame['positionMs']);
        if (reported !== null && party.playback.isPlaying) {
          const drift = reported - party.playback.positionAt(nowMs());
          if (Math.abs(drift) > DRIFT_TOLERANCE_MS) {
            this.log.log(`party ${party.code}: ${member.displayName} drifted ${drift}ms`);
          }
        }
        return;
      }

      case CONTROL:
        this.onControl(ws, party, member, frame);
        return;

      default:
        return;
    }
  }

  private onControl(
    ws: WebSocket,
    party: Party,
    member: Member,
    frame: Record<string, unknown>,
  ): void {
    if (!party.spendControlBudget(member)) {
      this.send(ws, { type: ERROR, error: 'rate_limited', message: 'Too many controls at once.' });
      return;
    }

    const queueBefore = party.playback.queueSeq;
    const action = typeof frame['action'] === 'string' ? frame['action'] : '';
    const result = applyControl(party, member, frame);
    if (!result.ok) {
      this.send(ws, { type: ERROR, error: result.code, message: result.message });
      return;
    }

    party.touch();
    // The queue first, so that nobody is holding a state frame that points at an
    // index in a list they have not been given yet.
    if (party.playback.queueSeq !== queueBefore) {
      this.parties.hub.broadcast(party.code, this.parties.queueFrame(party));
    }
    // To everyone, the sender included. The device that pressed pause re-anchors
    // off the same frame as the rest, so nobody is running on a locally
    // predicted state the server never confirmed.
    this.parties.hub.broadcast(party.code, this.parties.stateFrame(party));
    // The member list and the control policy travel together, and only when
    // something about the membership actually changed — a kick, a resize, or the
    // lock being turned on. Sending it on every control would be a frame per
    // pause to say nothing new.
    if (
      action === ACTION_KICK ||
      action === ACTION_SET_MAX_MEMBERS ||
      action === ACTION_SET_HOST_ONLY_CONTROL
    ) {
      this.parties.hub.broadcast(party.code, this.parties.membersFrame(party));
    }
    this.parties.hub.broadcast(party.code, this.parties.activityFrame(member, action, frame));
    // Last, and after the frame that says so: the removed listener is told why
    // before their socket is closed under them. Told first, because a close
    // alone is indistinguishable from a network drop — the client would simply
    // reconnect to a party that no longer knows it.
    if (action === ACTION_KICK) {
      const targetId = typeof frame['memberId'] === 'string' ? frame['memberId'] : '';
      if (targetId) this.eject(party, targetId);
    }
  }

  /** Tells a removed member why, then closes their socket. */
  private eject(party: Party, memberId: string): void {
    this.parties.hub.send(party.code, memberId, {
      type: BYE,
      reason: 'kicked',
      message: 'The host removed you from this party.',
    });
    this.parties.hub.closeMember(party.code, memberId);
  }

  private send(ws: WebSocket, payload: unknown): void {
    try {
      if (ws.readyState !== ws.OPEN) return;
      ws.send(JSON.stringify(payload));
    } catch (error) {
      this.log.debug(`dropped a frame to a closed socket: ${(error as Error).message}`);
    }
  }

  onApplicationShutdown(): void {
    this.server.close();
  }
}

/** What a control came to: applied, or a reason it was refused. */
type ControlResult = { ok: true } | { ok: false; code: string; message: string };

/**
 * Applies one member's control.
 *
 * Who may send what is enforced here rather than left to the clients. An app
 * that hides its own next button is a courtesy; this is what actually stops a
 * listener's device — a stale build, a backgrounded one still echoing an old
 * intent, or something else entirely — from moving the music for everybody.
 * The host-only setting is one half of that and the membership actions the
 * other, and both are checked below.
 */
function applyControl(party: Party, member: Member, frame: Record<string, unknown>): ControlResult {
  const playback = party.playback;
  const who = member.memberId;
  const action = typeof frame['action'] === 'string' ? frame['action'] : '';

  // A party locked to its host refuses the transport and the queue from anyone
  // else. Membership actions are not in this set and carry their own checks.
  if (CONTROL_ACTIONS.has(action) && !party.mayControl(member)) {
    return {
      ok: false,
      code: 'host_only',
      message: 'Only the host can control the music in this party.',
    };
  }

  switch (action) {
    case ACTION_PLAY:
      playback.play(who, asInt(frame['positionMs']));
      return { ok: true };
    case ACTION_PAUSE:
      playback.pause(who, asInt(frame['positionMs']));
      return { ok: true };
    case ACTION_SEEK: {
      const position = asInt(frame['positionMs']);
      if (position === null) {
        return { ok: false, code: 'missing_position', message: 'seek requires positionMs.' };
      }
      playback.seek(who, position);
      return { ok: true };
    }
    case ACTION_SET_TRACK:
      playback.setTrack(
        who,
        trackFromWire(frame['track']),
        asInt(frame['positionMs']) ?? 0,
        frame['isPlaying'] === undefined ? true : frame['isPlaying'] === true,
        asInt(frame['queueIndex']),
        member.displayName,
      );
      return { ok: true };
    case ACTION_SET_QUEUE: {
      const raw = frame['queue'];
      if (!Array.isArray(raw)) {
        return { ok: false, code: 'missing_queue', message: 'setQueue requires a queue array.' };
      }
      const queue = raw.map(trackFromWire).filter((track): track is Track => track !== null);
      playback.setQueue(who, queue, asInt(frame['queueIndex']) ?? -1);
      return { ok: true };
    }
    case ACTION_QUEUE_ADD: {
      // Either one track or a batch: a tap on a row sends the first, "add
      // album" and an AutoPlay refill send the second.
      const single = trackFromWire(frame['track']);
      const raw = frame['tracks'];
      const batch = Array.isArray(raw)
        ? raw.map(trackFromWire).filter((track): track is Track => track !== null)
        : [];
      const tracks = single !== null ? [single] : batch;
      const added = playback.addUpcoming(who, tracks, frame['playNext'] === true);
      if (!added.ok) {
        return added.reason === 'queue_full'
          ? {
              ok: false,
              code: 'queue_full',
              message: 'The upcoming queue is full.',
            }
          : { ok: false, code: 'no_tracks', message: 'No songs to add.' };
      }
      return { ok: true };
    }
    case ACTION_QUEUE_REMOVE: {
      let videoId = typeof frame['videoId'] === 'string' ? frame['videoId'] : '';
      if (!videoId) {
        const index = asInt(frame['index']);
        if (index !== null && index >= 0 && index < playback.queue.length) {
          videoId = playback.queue[index].videoId;
        }
      }
      if (!videoId) {
        return {
          ok: false,
          code: 'missing_track',
          message: 'queueRemove requires videoId or index.',
        };
      }
      if (!playback.removeUpcoming(who, videoId)) {
        return { ok: false, code: 'not_found', message: 'That song is not in the upcoming queue.' };
      }
      return { ok: true };
    }
    case ACTION_QUEUE_CLEAR:
      if (!playback.clearUpcoming(who)) {
        return { ok: false, code: 'no_upcoming', message: 'No upcoming songs to clear.' };
      }
      return { ok: true };
    case ACTION_QUEUE_MOVE: {
      const from = asInt(frame['fromIndex']);
      const to = asInt(frame['toIndex']);
      if (from === null || to === null) {
        return {
          ok: false,
          code: 'missing_indices',
          message: 'queueMove requires fromIndex and toIndex.',
        };
      }
      const videoId = typeof frame['videoId'] === 'string' ? frame['videoId'] : '';
      if (!playback.moveUpcoming(who, from, to, videoId)) {
        return { ok: false, code: 'invalid_move', message: 'That reorder is not possible.' };
      }
      return { ok: true };
    }
    case ACTION_NEXT:
      if (!playback.step(who, 1, member.displayName)) {
        return { ok: false, code: 'end_of_queue', message: 'Already at the end of the queue.' };
      }
      return { ok: true };
    case ACTION_PREVIOUS:
      if (!playback.step(who, -1, member.displayName)) {
        return { ok: false, code: 'start_of_queue', message: 'Already at the beginning of the queue.' };
      }
      return { ok: true };
    case ACTION_SET_MAX_MEMBERS: {
      const value = asInt(frame['maxMembers']);
      if (value === null) {
        return {
          ok: false,
          code: 'invalid_capacity',
          message: 'Choose a party size between 2 and 10.',
        };
      }
      return asControlResult(() => party.setMaxMembers(member, value));
    }
    case ACTION_SET_HOST_ONLY_CONTROL: {
      const enabled = frame['enabled'];
      if (typeof enabled !== 'boolean') {
        return {
          ok: false,
          code: 'invalid_control_policy',
          message: 'Host-only control must be enabled or disabled.',
        };
      }
      return asControlResult(() => party.setHostOnlyControl(member, enabled));
    }
    case ACTION_KICK: {
      const targetId = typeof frame['memberId'] === 'string' ? frame['memberId'] : '';
      if (!member.isHost) {
        return { ok: false, code: 'host_only', message: 'Only the host can remove listeners.' };
      }
      if (!targetId || targetId === member.memberId) {
        return { ok: false, code: 'invalid_member', message: 'Choose another listener to remove.' };
      }
      if (!party.members.has(targetId)) {
        return {
          ok: false,
          code: 'not_found',
          message: 'That listener is no longer in this party.',
        };
      }
      party.remove(targetId);
      return { ok: true };
    }
    default:
      return { ok: false, code: 'unknown_action', message: 'Unsupported control.' };
  }
}

/** Runs a domain method whose only failure is a [PartyError], as a control result. */
function asControlResult(run: () => void): ControlResult {
  try {
    run();
    return { ok: true };
  } catch (error) {
    if (error instanceof PartyError) {
      return { ok: false, code: error.code, message: error.message };
    }
    throw error;
  }
}

/**
 * Refuses an upgrade before the socket is opened, telling the client why.
 *
 * A raw `socket.destroy()` here would be the shortest path and the worst one:
 * the app would see a connection that simply vanished, with no way to tell "no
 * such party" from "bad token" from "the server is down". So the refusal is a
 * plain HTTP response carrying the same `{ error, message }` body the REST half
 * uses — the client reads it off the failed handshake and can say what went
 * wrong.
 */
function reject(
  socket: Duplex,
  status: number,
  reason: string,
  error: string,
  message: string,
): void {
  if (socket.destroyed) return;
  const body = JSON.stringify({ type: ERROR, error, message });
  const payload = Buffer.from(body, 'utf8');
  const head = [
    `HTTP/1.1 ${status} ${reason}`,
    'Content-Type: application/json; charset=utf-8',
    `Content-Length: ${payload.length}`,
    'Connection: close',
    '',
    '',
  ].join('\r\n');
  try {
    socket.write(head);
    socket.write(payload);
    socket.end();
  } catch {
    socket.destroy();
  }
}

export type { IncomingMessage };
