/**
 * The one clock every device in a party agrees on.
 *
 * Nothing in a listening party is synchronised by sending "play now" and hoping
 * the packets land together. Every playback state the server holds is a position
 * *plus the server time that position was true at*, and each client converts
 * that into its own frame using an offset it measures against this clock. So
 * this module is the reference the whole feature is built on, and it has exactly
 * one job beyond telling the time: never to jump.
 *
 * `Date.now()` alone would not do. It is wall-clock time, and wall-clock time is
 * stepped — by NTP, by the platform, by a container being migrated. A backwards
 * step of even 200 ms rewrites the anchor under every listening device at once
 * and shows up as a room full of phones seeking. So the epoch is read once, at
 * module load, and every reading after that is that epoch advanced by a
 * monotonic source (`performance.now()`, which cannot step). The result still
 * looks like Unix time in milliseconds — comparable to a phone's own clock, and
 * readable in a log — while being immune to the steps the real one takes.
 */

const epochWallMs = Date.now();
const epochMonotonic = performance.now();

/** Server time in milliseconds since the Unix epoch, monotonically. */
export function nowMs(): number {
  return Math.round(epochWallMs + (performance.now() - epochMonotonic));
}
