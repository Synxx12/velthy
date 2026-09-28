/**
 * Request bodies for the REST half, and the names the WebSocket half uses.
 *
 * The WebSocket frames are hand-validated in the gateway rather than modelled
 * here: they are small, they arrive on a hot path, and half of them are a single
 * number. What is worth pinning down is the identity a device presents when it
 * joins, which is the one place a bad or absent value has to be refused rather
 * than defaulted.
 */
import { PartyError } from '../party/party.js';

/** The identity a device presents in order to create or join a party. */
export interface JoinRequest {
  userId: string;
  deviceId: string;
  displayName: string;
  avatarUrl: string | null;
  /**
   * How many devices the creator wants to allow. Only read when a party is
   * being *created* — a join to an existing party cannot resize it, and the
   * host has a control for that once inside.
   */
  maxMembers?: number | null;
}

/**
 * Validates and normalises a join body. All three identifiers come from the
 * app's signed-in account, and a request without them is refused — listening
 * together is a named activity, and a party of "Listener", "Listener" and
 * "Listener" is not the feature.
 */
export function parseJoinRequest(raw: unknown): JoinRequest {
  if (typeof raw !== 'object' || raw === null) {
    throw new PartyError(400, 'bad_request', 'A JSON object is required.');
  }
  const body = raw as Record<string, unknown>;

  const userId = str(body['userId']);
  const deviceId = str(body['deviceId']);
  const displayName = str(body['displayName']);
  const avatarUrl = str(body['avatarUrl']);

  if (!userId || userId.length > 128) {
    throw new PartyError(400, 'bad_request', 'userId must be 1-128 characters.');
  }
  if (!deviceId || deviceId.length > 128) {
    throw new PartyError(400, 'bad_request', 'deviceId must be 1-128 characters.');
  }
  if (!displayName || displayName.length > 80) {
    throw new PartyError(400, 'bad_request', 'displayName must be 1-80 characters.');
  }
  if (avatarUrl.length > 1000) {
    throw new PartyError(400, 'bad_request', 'avatarUrl must be at most 1000 characters.');
  }
  // The avatar is rebroadcast to every other device, which will then load it.
  // Anything but http(s) — `file:`, `content:`, a `data:` URI big enough to be a
  // payload — has no business making that trip.
  if (avatarUrl && !avatarUrl.startsWith('http://') && !avatarUrl.startsWith('https://')) {
    throw new PartyError(400, 'bad_request', 'avatarUrl must be an http(s) URL.');
  }

  // Optional, and only meaningful on create. A malformed value is refused
  // rather than clamped: silently resizing somebody's party to five when they
  // asked for ten is a surprise they would only discover by counting faces.
  const rawMax = body['maxMembers'];
  let maxMembers: number | null = null;
  if (rawMax !== undefined && rawMax !== null) {
    if (typeof rawMax !== 'number' || !Number.isFinite(rawMax)) {
      throw new PartyError(400, 'bad_request', 'maxMembers must be a number.');
    }
    const value = Math.trunc(rawMax);
    if (value < 2 || value > 10) {
      throw new PartyError(400, 'bad_request', 'maxMembers must be between 2 and 10.');
    }
    maxMembers = value;
  }

  return { userId, deviceId, displayName, avatarUrl: avatarUrl || null, maxMembers };
}

function str(value: unknown): string {
  return typeof value === 'string' ? value.trim() : '';
}

// -- WebSocket frame types, server -> client --------------------------------

export const WELCOME = 'welcome';
export const STATE = 'state';
export const QUEUE = 'queue';
export const MEMBERS = 'members';
export const PONG = 'pong';
export const ERROR = 'error';
export const BYE = 'bye';
/**
 * One thing somebody did, broadcast to the party as it happens.
 *
 * Separate from [STATE] on purpose: the state says what is true now, this says
 * *who* made it true and in what words. It is not replayed — a device that was
 * away for a minute does not need to read back everything it missed, and the
 * frame carries no sequence number for exactly that reason.
 */
export const ACTIVITY = 'activity';

// -- WebSocket frame types, client -> server --------------------------------

export const PING = 'ping';
export const CONTROL = 'control';
export const SYNC = 'sync';
/**
 * "my queue is stale, send it again". The self-healing half of splitting the
 * queue out of the state frame: a client that misses a QUEUE broadcast sees a
 * queueSeq it does not have on the very next heartbeat and asks.
 */
export const SYNC_QUEUE = 'syncQueue';
export const REPORT = 'report';

// -- Control actions, any of which any member may send ----------------------

export const ACTION_PLAY = 'play';
export const ACTION_PAUSE = 'pause';
export const ACTION_SEEK = 'seek';
export const ACTION_SET_TRACK = 'setTrack';
export const ACTION_SET_QUEUE = 'setQueue';
export const ACTION_QUEUE_ADD = 'queueAdd';
export const ACTION_QUEUE_REMOVE = 'queueRemove';
export const ACTION_QUEUE_CLEAR = 'queueClear';
export const ACTION_QUEUE_MOVE = 'queueMove';
export const ACTION_NEXT = 'next';
export const ACTION_PREVIOUS = 'previous';
export const ACTION_KICK = 'kick';
export const ACTION_SET_MAX_MEMBERS = 'setMaxMembers';
export const ACTION_SET_HOST_ONLY_CONTROL = 'setHostOnlyControl';

/**
 * The actions a party's host-only setting restricts.
 *
 * Membership actions are deliberately absent. `kick` and `setMaxMembers` are
 * host-only unconditionally — the server checks the role itself — and
 * `setHostOnlyControl` has to stay reachable by the host to be turned back off.
 * Putting them here would mean the setting could lock the host out of
 * administering their own party.
 */
export const CONTROL_ACTIONS: ReadonlySet<string> = new Set([
  ACTION_PLAY,
  ACTION_PAUSE,
  ACTION_SEEK,
  ACTION_SET_TRACK,
  ACTION_SET_QUEUE,
  ACTION_QUEUE_ADD,
  ACTION_QUEUE_REMOVE,
  ACTION_QUEUE_CLEAR,
  ACTION_QUEUE_MOVE,
  ACTION_NEXT,
  ACTION_PREVIOUS,
]);
