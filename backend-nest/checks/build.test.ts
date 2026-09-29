/**
 * The build banner: what a running deployment says about itself.
 *
 * This exists because a stale deploy is invisible from outside. A server on an
 * older build answers `/healthz`, accepts sockets, and reports `ok: true` — and
 * the only symptom is a party feature that quietly does not work. The banner is
 * what turns that into something a person can check.
 */
import assert from 'node:assert/strict';
import { test } from 'node:test';

import { BUILD, FEATURES, PROTOCOL, hasFeature } from '../src/common/build.js';

test('the banner names every feature this build ships', () => {
  // The list is the contract: a client reads it to decide what to offer, so a
  // feature that exists but is not listed is a feature nobody will use.
  for (const name of [
    'queue-deltas',
    'activity-feed',
    'host-controls',
    'party-preview',
    'per-party-size',
  ]) {
    assert.equal(hasFeature(name), true, `missing feature: ${name}`);
  }
  assert.equal(BUILD.features.length, FEATURES.length);
});

test('an unknown feature is not claimed', () => {
  assert.equal(hasFeature('teleportation'), false);
  assert.equal(hasFeature(''), false);
});

test('the protocol version is a positive number', () => {
  assert.equal(typeof PROTOCOL, 'number');
  assert.ok(Number.isInteger(PROTOCOL));
  assert.ok(PROTOCOL >= 1);
});

test('the version comes from package.json rather than a second copy', () => {
  assert.match(BUILD.version, /^\d+\.\d+\.\d+/);
});

test('a commit is reported when the host names one, and null when it does not', () => {
  // Whatever this process was started with is what it must report — the point
  // is that the field is never invented.
  const fromEnv =
    process.env.GIT_COMMIT ?? process.env.SOURCE_COMMIT ?? process.env.GIT_SHA ?? '';
  if (fromEnv.trim().length > 0) {
    assert.equal(BUILD.commit, fromEnv.trim().slice(0, 40));
  } else {
    assert.equal(BUILD.commit, null);
  }
});
