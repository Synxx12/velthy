/**
 * Deployment knobs, all overridable from the environment.
 *
 * Read once, validated once, at boot. Nothing downstream re-reads `process.env`
 * — a value that can change under a running server is a value nobody can
 * reason about, and every one of these is a fact about a party that should be
 * fixed for the life of the process.
 */

/**
 * The injection token the config is provided under.
 *
 * A string token rather than the `Config` type: type-based injection relies on
 * `emitDecoratorMetadata`, which only exists when TypeScript compiled the file,
 * so it silently yields `undefined` on the source-loaded development path. An
 * explicit token behaves the same in both, which is what lets `startup.mjs` be
 * one entry point for the compiled build and the source tree.
 */
export const CONFIG = 'VELTHY_CONFIG';


function int(name: string, fallback: number, { min = 0, max = Number.MAX_SAFE_INTEGER } = {}): number {
  const raw = process.env[name]?.trim();
  if (!raw) return fallback;
  const value = Number(raw);
  if (!Number.isFinite(value)) {
    throw new Error(`${name} must be a number, got ${JSON.stringify(raw)}`);
  }
  if (value < min || value > max) {
    throw new Error(`${name} must be between ${min} and ${max}, got ${value}`);
  }
  return Math.trunc(value);
}

function csv(name: string, fallback: string): string[] {
  const raw = process.env[name]?.trim() || fallback;
  return raw
    .split(',')
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
}

export interface Config {
  /** Where the process listens. */
  readonly bindAddress: string;
  readonly port: number;

  readonly maxMembers: number;
  readonly stateHeartbeatMs: number;
  readonly playLeadMs: number;
  readonly disconnectGraceMs: number;
  readonly emptyPartyTtlMs: number;
  readonly partyMaxAgeMs: number;
  readonly controlRatePerSecond: number;
  /**
   * How many songs may be queued *ahead* of the one playing.
   *
   * The number that matters to a listener: it is the queue they can see and
   * add to. Past this the server refuses with `queue_full` rather than
   * truncating, so a device is never left believing it queued something that
   * was silently dropped.
   */
  readonly maxUpcomingQueue: number;
  readonly maxQueueLength: number;
  readonly allowedOrigins: string[];
}

export function loadConfig(): Config {
  // PORT is what hosts and Pterodactyl set; SERVER_PORT is the panel's own name
  // for the same thing; 8080 is the fallback when neither is present.
  const port = int('PORT', int('SERVER_PORT', 8080, { min: 1, max: 65535 }), {
    min: 1,
    max: 65535,
  });
  const bindHost = process.env.BIND_ADDR?.trim();

  // Upcoming first, because the queue ceiling is derived from it: a party's
  // queue is "what is playing" plus the songs still to come, and allowing
  // more than that in total would let a `setQueue` slip past the add limit.
  const maxUpcomingQueue = int('JAM_MAX_UPCOMING_QUEUE', 25, { min: 1, max: 500 });

  return {
    bindAddress: bindHost && bindHost.length > 0 ? bindHost : `0.0.0.0:${port}`,
    port,

    // Counted in devices rather than in people: the same account signed in on a
    // phone and a tablet is two things that have to be fed audio, and that is
    // what the limit is protecting.
    maxMembers: int('JAM_MAX_MEMBERS', 5, { min: 2, max: 32 }),

    // How often the server re-states the truth to everyone, unprompted. Clients
    // correct their own drift against these; nothing waits for a user action.
    stateHeartbeatMs: int('JAM_STATE_HEARTBEAT_MS', 5_000, { min: 1_000, max: 60_000 }),

    // Playback resumes slightly in the future rather than immediately, so every
    // device has the same instant to aim at instead of each starting whenever
    // its own packet happened to arrive.
    playLeadMs: int('JAM_PLAY_LEAD_MS', 350, { min: 0, max: 5_000 }),

    // A dropped connection is not a departure — phones lose WebSockets in
    // tunnels, on screen-off, and on every network handover. The member keeps
    // their slot for this long so a reconnect is invisible to the rest.
    disconnectGraceMs: int('JAM_DISCONNECT_GRACE_MS', 45_000, { min: 5_000, max: 600_000 }),

    // A party with nobody in it, or one nobody has touched in a very long time,
    // is swept so codes and memory come back.
    emptyPartyTtlMs: int('JAM_EMPTY_PARTY_TTL_MS', 120_000, { min: 10_000, max: 3_600_000 }),
    partyMaxAgeMs: int('JAM_PARTY_MAX_AGE_MS', 12 * 60 * 60 * 1000, { min: 60_000 }),

    // Ceiling on control frames one member may send per second. A scrubbing
    // finger is a legitimate burst, so this sits well above the UI's own
    // throttle and exists only to stop a broken client shouting the party down.
    controlRatePerSecond: int('JAM_CONTROL_RATE_PER_SECOND', 25, { min: 1, max: 200 }),

    maxQueueLength: int('JAM_MAX_QUEUE_LENGTH', 1 + maxUpcomingQueue, {
      min: 1,
      max: 5_000,
    }),

    maxUpcomingQueue,

    // Browsers are not a client of this service today, so the default is closed
    // to none in particular: an empty list allows any origin, which is what the
    // Android app needs (it sends no Origin header at all).
    allowedOrigins: csv('JAM_ALLOWED_ORIGINS', ''),
  };
}

/** A one-line summary for the boot log, with no secrets in it (there are none). */
export function describeConfig(config: Config): string {
  return [
    `bind=${config.bindAddress}`,
    `maxMembers=${config.maxMembers}`,
    `heartbeat=${config.stateHeartbeatMs}ms`,
    `playLead=${config.playLeadMs}ms`,
    `grace=${config.disconnectGraceMs}ms`,
  ].join(' ');
}
