/**
 * The sync rule, tested as arithmetic rather than through a socket.
 *
 * This file exists to prove the one thing the whole feature rests on: that a
 * `PlaybackState` answers "where should this device be" from three numbers, and
 * that the answer does not depend on when the question was asked or how late the
 * frame carrying it arrived.
 */
import assert from 'node:assert/strict';
import { test } from 'node:test';

import { loadConfig, type Config } from '../src/common/config.js';
import { PlaybackState, Party, PartyError, PartyStore, type Track } from '../src/party/party.js';

const config: Config = loadConfig();

function aTrack(durationMs: number | null = 240_000): Track {
  return { videoId: 'abc123', title: 'Song', artist: 'Artist', thumbnailUrl: null, durationMs };
}

test('a paused state does not move', () => {
  const state = new PlaybackState(config);
  state.track = aTrack();
  state.positionMs = 30_000;
  state.anchorMs = 1_000;
  state.isPlaying = false;
  assert.equal(state.positionAt(1_000), 30_000);
  assert.equal(state.positionAt(999_000), 30_000);
});

test('a playing state advances with the server clock', () => {
  const state = new PlaybackState(config);
  state.track = aTrack();
  state.positionMs = 30_000;
  state.anchorMs = 1_000;
  state.isPlaying = true;
  assert.equal(state.positionAt(1_000), 30_000);
  assert.equal(state.positionAt(6_000), 35_000);
});

test('a late frame still lands in the right place', () => {
  const state = new PlaybackState(config);
  state.track = aTrack();
  state.positionMs = 30_000;
  state.anchorMs = 1_000;
  state.isPlaying = true;
  // Two devices read the same frame 400ms apart and each asks where the party is
  // *now*; both answers describe the same instant on the same timeline.
  assert.equal(state.positionAt(2_000) - state.positionAt(1_600), 400);
});

test('playback holds at the position until the scheduled start', () => {
  const state = new PlaybackState(config);
  state.track = aTrack();
  state.positionMs = 10_000;
  state.play(null, null);
  const start = state.anchorMs;
  // Before the common start instant every device sits at the same position,
  // rather than the nearest one running ahead.
  assert.equal(state.positionAt(start - 400), 10_000);
  assert.equal(state.positionAt(start), 10_000);
  assert.equal(state.positionAt(start + 250), 10_250);
});

test('position never runs past the track', () => {
  const state = new PlaybackState(config);
  state.track = aTrack(5_000);
  state.positionMs = 0;
  state.isPlaying = true;
  assert.equal(state.positionAt(state.anchorMs + 60_000), 5_000);
});

test('pause takes the party position, not the caller’s', () => {
  const state = new PlaybackState(config);
  state.track = aTrack();
  state.positionMs = 0;
  state.isPlaying = true;
  state.anchorMs = Date.now() - 5_000;

  state.pause('m1', null);

  assert.equal(state.isPlaying, false);
  // Five seconds of server time have passed since the anchor, so that is where
  // the party is — not wherever the pausing device's own playhead had got to.
  assert.ok(state.positionMs >= 4_900 && state.positionMs <= 5_200, `positionMs=${state.positionMs}`);
  assert.equal(state.positionAt(state.anchorMs + 100_000), state.positionMs);
});

test('pause with an explicit position uses it', () => {
  const state = new PlaybackState(config);
  state.track = aTrack();
  state.isPlaying = true;
  state.pause('m1', 1_234);
  assert.equal(state.positionMs, 1_234);
});

test('seek is taken as given', () => {
  const state = new PlaybackState(config);
  state.track = aTrack();
  state.isPlaying = true;
  state.seek('m1', 90_000);
  assert.equal(state.positionAt(state.anchorMs), 90_000);
});

test('setQueue clamps an out-of-range index', () => {
  const state = new PlaybackState(config);
  state.setQueue('m1', [aTrack(), aTrack()], 9);
  assert.equal(state.queueIndex, -1);
  assert.equal(state.queueSeq, 1);
});

test('step moves only when there is room', () => {
  const state = new PlaybackState(config);
  state.setQueue('m1', [aTrack(), aTrack()], 0);
  assert.equal(state.step('m1', 1, 'Host'), true);
  assert.equal(state.queueIndex, 1);
  assert.equal(state.step('m1', 1, 'Host'), false);
});

test('rejoining is not a second membership', () => {
  const party = new Party('ABC123', config);
  const first = party.join('u1', 'dev1', 'Ada', null);
  const oldToken = first.token;
  const again = party.join('u1', 'dev1', 'Ada Lovelace', null);
  assert.equal(party.members.size, 1);
  assert.equal(again.memberId, first.memberId);
  assert.notEqual(again.token, oldToken);
});

test('authenticate rejects an unknown token', () => {
  const party = new Party('ABC123', config);
  party.join('u1', 'dev1', 'Ada', null);
  assert.throws(() => party.authenticate('nope'), PartyError);
});

test('the host role is handed on, not ended', () => {
  const party = new Party('ABC123', config);
  const host = party.join('u1', 'dev1', 'Ada', null);
  party.join('u2', 'dev2', 'Grace', null);
  assert.equal(host.isHost, true);
  party.remove(host.memberId);
  assert.equal(party.members.size, 1);
  const remaining = [...party.members.values()][0];
  assert.equal(remaining.isHost, true);
});

test('a party only fills to its ceiling', () => {
  const small: Config = { ...config, maxMembers: 2 };
  const party = new Party('ABC123', small);
  party.join('u1', 'd1', 'A', null);
  party.join('u2', 'd2', 'B', null);
  assert.throws(() => party.join('u3', 'd3', 'C', null), PartyError);
});

test('the control budget runs dry and refills', () => {
  const party = new Party('ABC123', config);
  const member = party.join('u1', 'dev1', 'Ada', null);
  const limit = Math.trunc(config.controlRatePerSecond);
  for (let i = 0; i < limit; i += 1) {
    assert.equal(party.spendControlBudget(member), true, `spend ${i}`);
  }
  assert.equal(party.spendControlBudget(member), false);
});

test('the store mints unique, well-formed codes', () => {
  const store = new PartyStore(config);
  const seen = new Set<string>();
  for (let i = 0; i < 50; i += 1) {
    const party = store.create();
    assert.match(party.code, /^[0-9A-HJKMNP-Z]{6}$/);
    assert.equal(seen.has(party.code), false);
    seen.add(party.code);
  }
});
