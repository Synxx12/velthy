/**
 * How the entry point runs `npm`, and why it is not `npm`.
 *
 * `npm` is not an executable: it is a shell script on POSIX and a `.cmd` batch
 * file on Windows. Node refuses to spawn a batch file without a shell — a
 * deliberate security decision, and it surfaces as `EINVAL` — while spawning it
 * *with* a shell means the shell re-parses the arguments, which is how a path
 * containing a space becomes two arguments.
 *
 * The entry point sidesteps both by running npm's JavaScript entry point under
 * the Node that is already running. These tests pin that decision down, because
 * getting it wrong is a deploy that cannot install its own dependencies and
 * says only `EINVAL`.
 */
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, isAbsolute, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const here = dirname(fileURLToPath(import.meta.url));
const startupSource = readFileSync(join(here, '..', 'startup.mjs'), 'utf8');

/**
 * The same lookup the entry point does.
 *
 * Repeated rather than imported because the decision being tested is *where*
 * npm's entry point lives relative to Node — a fact about this machine, not a
 * rule the entry point owns. If the layout changes under us, this fails here
 * rather than in a deploy.
 */
function npmCliCandidates() {
  return [
    join(dirname(process.execPath), 'node_modules', 'npm', 'bin', 'npm-cli.js'),
    join(dirname(process.execPath), '..', 'lib', 'node_modules', 'npm', 'bin', 'npm-cli.js'),
  ];
}

test('npm has a JavaScript entry point beside this Node', () => {
  // Not "npm exists on PATH": the point is that a path exists that can be
  // spawned without a shell, on every platform, including a container.
  const found = npmCliCandidates().filter((candidate) => existsSync(candidate));
  assert.ok(
    found.length > 0,
    `no npm-cli.js found relative to ${process.execPath}.\nLooked at:\n  ${npmCliCandidates().join('\n  ')}`,
  );
  for (const candidate of found) {
    assert.ok(isAbsolute(candidate), 'the path must be absolute to be spawned directly');
  }
});

test('the entry point resolves npm without a shell', () => {
  // Read as source: the decision is what is written, and a behavioural test
  // would have to run an actual install to observe it.
  assert.ok(
    !/shell:\s*(process\.platform|true)/.test(startupSource),
    'startup.mjs must not pass `shell` to spawnSync -- it re-parses arguments',
  );
  assert.ok(
    startupSource.includes('npm-cli.js'),
    "startup.mjs must locate npm's JavaScript entry point",
  );
  assert.ok(
    startupSource.includes('process.execPath'),
    'the npm entry point must run under the same Node that is running startup.mjs',
  );
});

test('git is spawned as a plain executable', () => {
  // git ships a real binary everywhere, so it needs none of npm's handling.
  assert.ok(startupSource.includes("capture('git'"), 'git is used through capture()');
  assert.ok(startupSource.includes("run('git'"), 'git is used through run()');
});

test('no command is spawned through a shell', () => {
  // The blanket rule, so a future command does not quietly reintroduce it.
  const spawnCalls = startupSource.match(/spawnSync\([^)]*\)/gs) ?? [];
  assert.ok(spawnCalls.length > 0, 'expected spawnSync calls in startup.mjs');
  for (const call of spawnCalls) {
    assert.ok(!/\bshell\s*:/.test(call), `a spawnSync call passes shell:\n${call}`);
  }
});
