/**
 * The copy step of a self-update.
 *
 * A deployment whose files were uploaded rather than cloned has no `.git` to
 * pull with, and the repository they came from holds them one level down — so
 * the update clones the repository *beside* the running tree and copies the one
 * subdirectory out of it. That copy is the part with rules worth testing: it
 * must be idempotent (a restart on an unchanged commit copies nothing), it must
 * never carry the checkout's own identity or dependencies across, and it must
 * not delete anything that only exists on the server.
 */
import assert from 'node:assert/strict';
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

import { copyTree } from '../startup.mjs';

function makeDir(prefix) {
  return mkdtempSync(join(tmpdir(), prefix));
}

function withDirs(run) {
  const from = makeDir('velthy-from-');
  const to = makeDir('velthy-to-');
  try {
    run(from, to);
  } finally {
    rmSync(from, { recursive: true, force: true });
    rmSync(to, { recursive: true, force: true });
  }
}

function write(path, contents) {
  mkdirSync(join(path, '..'), { recursive: true });
  writeFileSync(path, contents);
}

test('a file that is already identical is not copied', () => {
  // The idempotency that makes a restart cheap: a deploy on an unchanged
  // commit has nothing to write, and the log says so.
  withDirs((from, to) => {
    write(join(from, 'a.ts'), 'same\n');
    write(join(to, 'a.ts'), 'same\n');
    assert.equal(copyTree(from, to), 0);
  });
});

test('a changed file is copied, and counted', () => {
  withDirs((from, to) => {
    write(join(from, 'a.ts'), 'new\n');
    write(join(to, 'a.ts'), 'old\n');
    assert.equal(copyTree(from, to), 1);
    assert.equal(readFileSync(join(to, 'a.ts'), 'utf8'), 'new\n');
  });
});

test('a file that only exists in the source is copied', () => {
  withDirs((from, to) => {
    write(join(from, 'fresh.ts'), 'hello\n');
    assert.equal(copyTree(from, to), 1);
    assert.equal(readFileSync(join(to, 'fresh.ts'), 'utf8'), 'hello\n');
  });
});

test('a file the source does not have is left alone', () => {
  // `.env` lives only on the server. An update must not be a delete.
  withDirs((from, to) => {
    write(join(to, '.env'), 'TUNNEL_TOKEN=secret\n');
    write(join(from, 'a.ts'), 'x\n');
    copyTree(from, to);
    assert.equal(readFileSync(join(to, '.env'), 'utf8'), 'TUNNEL_TOKEN=secret\n');
  });
});

test('skipped names are not copied', () => {
  withDirs((from, to) => {
    write(join(from, 'node_modules', 'dep', 'index.js'), 'x\n');
    write(join(from, 'dist', 'bootstrap.js'), 'x\n');
    write(join(from, '.git', 'config'), 'x\n');
    write(join(from, 'kept.ts'), 'x\n');
    const copied = copyTree(from, to, new Set(['.git', 'node_modules', 'dist']));
    assert.equal(copied, 1);
    assert.equal(existsSync(join(to, 'node_modules')), false);
    assert.equal(existsSync(join(to, 'dist')), false);
    assert.equal(existsSync(join(to, '.git')), false);
    assert.equal(existsSync(join(to, 'kept.ts')), true);
  });
});

test('nested directories come across whole', () => {
  withDirs((from, to) => {
    write(join(from, 'party', 'deep', 'thing.ts'), 'x\n');
    write(join(from, 'party', 'other.ts'), 'y\n');
    assert.equal(copyTree(from, to), 2);
    assert.equal(existsSync(join(to, 'party', 'deep', 'thing.ts')), true);
    assert.equal(existsSync(join(to, 'party', 'other.ts')), true);
  });
});

test('a second copy of the same tree copies nothing', () => {
  // The whole point, stated as one assertion: run it twice, the second is free.
  withDirs((from, to) => {
    write(join(from, 'a.ts'), '1\n');
    write(join(from, 'nested', 'b.ts'), '2\n');
    write(join(to, 'only-here.ts'), 'keep me\n');

    assert.equal(copyTree(from, to), 2, 'first pass copies both files');
    assert.equal(copyTree(from, to), 0, 'second pass copies nothing');
    assert.equal(readFileSync(join(to, 'only-here.ts'), 'utf8'), 'keep me\n');
  });
});

test('binary content is compared by bytes, not by text', () => {
  withDirs((from, to) => {
    const bytes = Buffer.from([0x00, 0xff, 0x10, 0x80]);
    mkdirSync(from, { recursive: true });
    writeFileSync(join(from, 'blob.bin'), bytes);
    writeFileSync(join(to, 'blob.bin'), bytes);
    assert.equal(copyTree(from, to), 0);
  });
});
