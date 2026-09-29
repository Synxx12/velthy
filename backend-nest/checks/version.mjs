#!/usr/bin/env node
/**
 * Which build is the server actually running?
 *
 * Asks a deployed party server what it is, and compares the answer against this
 * checkout. Exists because "did my deploy take?" is otherwise unanswerable from
 * outside: a server on an older build looks exactly like a current one — same
 * routes, same health, same socket — and the only difference is a feature that
 * quietly does not work.
 *
 *   node checks/version.mjs                                  # the built-in server
 *   node checks/version.mjs https://api.example.com          # any server
 *   node checks/version.mjs http://127.0.0.1:33183           # one on this machine
 *
 * Exit code is 0 when the server is current, 1 when it is not or cannot be
 * reached — so a deploy script can gate on it.
 */
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = join(here, '..');

/** The address to ask. Falls back to the build's own configured server. */
function resolveTarget() {
  const fromArgv = process.argv[2]?.trim();
  if (fromArgv) return fromArgv.replace(/\/+$/, '');

  const fromEnv = process.env.PARTY_SERVER_URL?.trim();
  if (fromEnv) return fromEnv.replace(/\/+$/, '');

  // local.properties in the Android tree, which is where the app's own default
  // is set — the same value the phone will dial.
  try {
    const properties = readFileSync(
      join(repoRoot, '..', 'local.properties'),
      'utf8',
    );
    const match = properties.match(/^\s*PARTY_SERVER_URL\s*=\s*(.+)$/m);
    if (match?.[1]) return match[1].trim().replace(/\/+$/, '');
  } catch {
    /* not present, which is normal */
  }

  return null;
}

/** The commit this checkout is on, or null outside a git tree. */
function localCommit() {
  try {
    return execFileSync('git', ['rev-parse', '--short', 'HEAD'], {
      cwd: repoRoot,
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
  } catch {
    return null;
  }
}

/** The version this checkout's package.json declares. */
function localVersion() {
  const pkg = JSON.parse(readFileSync(join(repoRoot, 'package.json'), 'utf8'));
  return pkg.version ?? '0.0.0';
}

/** What this checkout says it can do, read from the source of truth. */
async function localFeatures() {
  const { FEATURES } = await import('../dist/common/build.js').catch(async () => {
    return import('../src/common/build.ts').catch(() => ({ FEATURES: [] }));
  });
  return [...FEATURES];
}

async function main() {
  const target = resolveTarget();
  if (!target) {
    console.error(
      'No server address. Pass one:\n' +
        '  node checks/version.mjs https://api.example.com\n' +
        'or set PARTY_SERVER_URL.',
    );
    process.exit(1);
  }

  console.log(`repo:   ${localVersion()}${localCommit() ? ` (${localCommit()})` : ''}`);

  let answer;
  try {
    const response = await fetch(`${target}/`, { signal: AbortSignal.timeout(20_000) });
    answer = await response.json();
  } catch (error) {
    console.error(`server: ${target} — UNREACHABLE (${error.message})`);
    process.exit(1);
  }

  const commit = answer.commit ? String(answer.commit).slice(0, 7) : 'unknown';
  console.log(`server: ${answer.version ?? '?'} (${commit}) · protocol ${answer.protocol ?? '?'}`);
  console.log(`        ${target}`);

  const expected = await localFeatures();
  const actual = Array.isArray(answer.features) ? answer.features : [];
  const missing = expected.filter((name) => !actual.includes(name));

  if (missing.length > 0) {
    console.error(`\nSTALE — the server is missing: ${missing.join(', ')}`);
    console.error(
      'It is running an older build. Re-deploy: pull the code and rebuild\n' +
        '(`npm ci && npm run build`), then restart.',
    );
    process.exit(1);
  }

  console.log(`\nCURRENT — all ${expected.length} features present.`);
  console.log('  ' + actual.join(', '));
}

main().catch((error) => {
  console.error('version check failed:', error);
  process.exit(1);
});
