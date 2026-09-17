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

  return { userId, deviceId, displayName, avatarUrl: avatarUrl || null };
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
export const ACTION_NEXT = 'next';
export const ACTION_PREVIOUS = 'previous';
