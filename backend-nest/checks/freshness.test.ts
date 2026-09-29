/**
 * The freshness check that makes a deploy actually take effect.
 *
 * `dist/` is preferred over `src/` when it exists -- that is what a deployment
 * runs -- and preferring it *unconditionally* is what made a panel restart look
 * like it did nothing: the panel pulled new sources, the old build stayed, and
 * the process started happily on yesterday's code with no error anywhere.
 *
 * These tests exercise the same rule the entry point uses, on a tree built here
 * rather than on the repository's own, so the answer does not depend on when
 * anybody last ran a build.
 */
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, statSync, utimesSync, writeFileSync } from 'node:fs';
import { readdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

/** The same walk the entry point does, over a tree this test owns. */
function newestMtimeMs(path, depth = 0) {
  if (depth > 12) return 0;
  let stats;
  try {
    stats = statSync(path);
  } catch {
    return 0;
  }
  if (!stats.isDirectory()) return stats.mtimeMs;
  let newest = 0;
  for (const entry of readdirSync(path, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === '.git' || entry.name === 'dist') continue;
    newest = Math.max(newest, newestMtimeMs(join(path, entry.name), depth + 1));
  }
  return newest;
}

/** The same rule the entry point applies. */
function distIsStale(root) {
  const dist = join(root, 'dist');
  try {
    statSync(join(dist, 'bootstrap.js'));
  } catch {
    return true;
  }
  const built = newestMtimeMs(dist);
  const sources = Math.max(
    newestMtimeMs(join(root, 'src')),
    newestMtimeMs(join(root, 'tsconfig.json')),
  );
  if (sources === 0) return false;
  return sources > built;
}

/** A throwaway tree with the shape the entry point looks for. */
function makeTree({ dist = true, sourceAgeMs = 0, buildAgeMs = 0 } = {}) {
  const root = mkdtempSync(join(tmpdir(), 'velthy-fresh-'));
  mkdirSync(join(root, 'src'), { recursive: true });
  writeFileSync(join(root, 'src', 'bootstrap.ts'), 'export {};\n');
  writeFileSync(join(root, 'tsconfig.json'), '{}\n');

  if (dist) {
    mkdirSync(join(root, 'dist'), { recursive: true });
    writeFileSync(join(root, 'dist', 'bootstrap.js'), 'export {};\n');
  }

  const now = Date.now();
  const sourceTime = new Date(now - sourceAgeMs);
  const buildTime = new Date(now - buildAgeMs);
  utimesSync(join(root, 'src', 'bootstrap.ts'), sourceTime, sourceTime);
  utimesSync(join(root, 'tsconfig.json'), sourceTime, sourceTime);
  if (dist) utimesSync(join(root, 'dist', 'bootstrap.js'), buildTime, buildTime);

  return root;
}

function withTree(options, run) {
  const root = makeTree(options);
  try {
    run(root);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

test('a build newer than its sources is not stale', () => {
  // Built a minute ago from sources ten minutes old: the ordinary healthy boot.
  withTree({ sourceAgeMs: 600_000, buildAgeMs: 60_000 }, (root) => {
    assert.equal(distIsStale(root), false);
  });
});

test('a build older than its sources is stale', () => {
  // This is the deploy that used to do nothing: new sources, old build.
  withTree({ sourceAgeMs: 60_000, buildAgeMs: 600_000 }, (root) => {
    assert.equal(distIsStale(root), true);
  });
});

test('no build at all is stale', () => {
  withTree({ dist: false }, (root) => {
    assert.equal(distIsStale(root), true);
  });
});

test('a changed tsconfig is enough to make the build stale', () => {
  withTree({ sourceAgeMs: 600_000, buildAgeMs: 60_000 }, (root) => {
    assert.equal(distIsStale(root), false, 'precondition');
    const now = new Date();
    utimesSync(join(root, 'tsconfig.json'), now, now);
    assert.equal(distIsStale(root), true);
  });
});

test('node_modules is not walked', () => {
  // A huge dependency tree must not be scanned on every boot, and its mtimes
  // have nothing to do with whether the build is current.
  withTree({ sourceAgeMs: 600_000, buildAgeMs: 60_000 }, (root) => {
    const dep = join(root, 'node_modules', 'some-package');
    mkdirSync(dep, { recursive: true });
    writeFileSync(join(dep, 'index.js'), 'export {};\n');
    const now = new Date();
    utimesSync(join(dep, 'index.js'), now, now);
    assert.equal(distIsStale(root), false);
  });
});

test('a nested source file is what decides', () => {
  withTree({ sourceAgeMs: 600_000, buildAgeMs: 60_000 }, (root) => {
    const nested = join(root, 'src', 'party');
    mkdirSync(nested, { recursive: true });
    writeFileSync(join(nested, 'party.ts'), 'export {};\n');
    const now = new Date();
    utimesSync(join(nested, 'party.ts'), now, now);
    assert.equal(distIsStale(root), true);
  });
});
