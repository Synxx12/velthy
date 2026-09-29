/**
 * What this build is, and what it can do.
 *
 * Written for the one question a running deployment cannot otherwise answer
 * about itself: *is the code I just deployed actually the code that is
 * running?* A server on an older build is indistinguishable from a current one
 * by looking at it — same banner, same health route, same socket accepting
 * connections — and the difference only shows up as a feature that quietly does
 * not work. Naming the build and the capabilities makes that answerable from
 * outside the machine, with `curl`.
 *
 * The commit is read from the environment when the host provides one. A
 * deployment that sets `GIT_COMMIT` (or the `SOURCE_COMMIT` / `GIT_SHA` names
 * other panels use) gets a hash; one that does not gets `null`, which is itself
 * an honest answer rather than a guess.
 */
import { createRequire } from 'node:module';

/**
 * The protocol version, bumped when the wire format changes in a way an older
 * client would misunderstand.
 *
 * A number rather than a date or a hash: it is compared, not read. A client
 * that speaks 2 can talk to a server that speaks 2, and what to do about a
 * mismatch — refuse, degrade, warn — is the client's decision to make.
 */
export const PROTOCOL = 2;

/**
 * The capabilities this build has, as a flat list of names.
 *
 * Flat and literal on purpose. Anything derived would be a second place for the
 * truth to live, and this list exists precisely because the truth is otherwise
 * only discoverable by trying.
 */
export const FEATURES = [
  /** `queueAdd` / `queueRemove` / `queueClear` / `queueMove` controls. */
  'queue-deltas',
  /** `activity` frames broadcast after every applied control. */
  'activity-feed',
  /** `kick` / `setMaxMembers` / `setHostOnlyControl`, and `hostOnlyControl` on the members frame. */
  'host-controls',
  /** `GET /api/parties/:code/preview`, unauthenticated. */
  'party-preview',
  /** `maxMembers` accepted when creating a party, and carried per party. */
  'per-party-size',
] as const;

/** The package version, read from package.json rather than repeated here. */
function packageVersion(): string {
  try {
    const require = createRequire(import.meta.url);
    const pkg = require('../../package.json') as { version?: string };
    return pkg.version ?? '0.0.0';
  } catch {
    // A build that cannot read its own package.json is not worth failing over.
    return '0.0.0';
  }
}

/** The commit this build came from, when the host told us. */
function commit(): string | null {
  const raw =
    process.env.GIT_COMMIT?.trim() ||
    process.env.SOURCE_COMMIT?.trim() ||
    process.env.GIT_SHA?.trim() ||
    '';
  return raw.length > 0 ? raw.slice(0, 40) : null;
}

/** What the banner and the health route report about this build. */
export const BUILD = {
  version: packageVersion(),
  /** Null when the host does not name one. See [commit]. */
  commit: commit(),
  features: [...FEATURES],
} as const;

/**
 * Whether this build has a capability. Exported for tests and for any future
 * place that has to decide behaviour from the list above.
 */
export function hasFeature(name: string): boolean {
  return (FEATURES as readonly string[]).includes(name);
}
