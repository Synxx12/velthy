/**
 * Which trees update themselves, and which are left alone.
 *
 * A self-update copies the branch over the files here. On a deployment that is
 * the point. On a developer's working copy it would delete uncommitted work
 * that exists nowhere else, so the two have to be told apart — and told apart
 * without being asked, because the answer is a property of the disk.
 *
 * The rule: a tree that sits *inside* somebody else's checkout is a working
 * copy. A tree with nothing above it that knows what git is, is a deployment.
 */
import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

import { insideLargerCheckout } from '../startup.mjs';

/** A throwaway directory tree, removed afterwards. */
function withRoot(run) {
  const root = mkdtempSync(join(tmpdir(), 'velthy-tree-'));
  try {
    run(root);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
}

test('a deployment folder is not inside a checkout', () => {
  withRoot((root) => {
    const deploy = join(root, 'container');
    mkdirSync(deploy, { recursive: true });
    assert.equal(insideLargerCheckout(deploy), false);
  });
});

test('a folder one level under a checkout is a working copy', () => {
  // The shape of this very repository: backend-nest/ inside the Velthy
  // checkout. A self-update here would overwrite uncommitted edits.
  withRoot((root) => {
    mkdirSync(join(root, '.git'), { recursive: true });
    const sub = join(root, 'backend-nest');
    mkdirSync(sub, { recursive: true });
    assert.equal(insideLargerCheckout(sub), true);
  });
});

test('a folder several levels down is still a working copy', () => {
  withRoot((root) => {
    mkdirSync(join(root, '.git'), { recursive: true });
    const deep = join(root, 'packages', 'server', 'backend');
    mkdirSync(deep, { recursive: true });
    assert.equal(insideLargerCheckout(deep), true);
  });
});

test('a sibling of a checkout is not inside it', () => {
  // Two directories side by side, one of them a clone: neither is the other's
  // working copy.
  withRoot((root) => {
    mkdirSync(join(root, 'clone', '.git'), { recursive: true });
    const other = join(root, 'deploy');
    mkdirSync(other, { recursive: true });
    assert.equal(insideLargerCheckout(other), false);
  });
});

test('the checkout directory itself is not "inside" a larger one', () => {
  // A tree that IS the repository updates in place, which is the other branch
  // of the decision. Its own .git is not one of its parents.
  withRoot((root) => {
    mkdirSync(join(root, '.git'), { recursive: true });
    assert.equal(insideLargerCheckout(root), false);
  });
});

test('the real tree is a working copy, so it never self-updates', () => {
  // The assertion that protects this repository: running the entry point here
  // must not copy the branch over uncommitted work.
  assert.equal(insideLargerCheckout(), true);
});
