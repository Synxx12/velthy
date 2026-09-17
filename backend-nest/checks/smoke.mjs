/**
 * End-to-end smoke test against a running server.
 *
 * Not part of `npm test` (that runs the unit tests): this one needs a live
 * process on PORT, because what it proves is that the REST half, the WebSocket
 * handshake, and the clock-sync round trip actually work together over a real
 * socket.
 *
 *   node startup.mjs &            # or: npm start
 *   node checks/smoke.mjs
 */
import assert from 'node:assert/strict';
import { WebSocket } from 'ws';

const base = `http://127.0.0.1:${process.env.PORT ?? 8080}`;
const wsBase = `ws://127.0.0.1:${process.env.PORT ?? 8080}`;

async function main() {
  // 1. REST: banner, health, clock
  const root = await (await fetch(`${base}/`)).json();
  assert.equal(root.service, 'velthy-listen-together');
  console.log('1. root:', JSON.stringify(root));

  const health = await (await fetch(`${base}/healthz`)).json();
  assert.equal(health.ok, true);
  console.log('2. health ok, serverMs:', health.serverMs);

  // 2. REST: create a party
  const created = await (
    await fetch(`${base}/api/parties`, {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ userId: 'u1', deviceId: 'd1', displayName: 'Ada' }),
    })
  ).json();
  assert.match(created.code, /^[0-9A-HJKMNP-Z]{6}$/);
  assert.ok(created.token);
  console.log('3. created party:', created.code);

  // 3. REST: refuse a bad token, and a malformed code
  const badToken = await fetch(`${base}/api/parties/${created.code}`, {
    headers: { authorization: 'Bearer nope' },
  });
  assert.equal(badToken.status, 401);
  console.log('4. bad token ->', badToken.status);

  const badCode = await fetch(`${base}/api/parties/zz/join`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ userId: 'u', deviceId: 'd', displayName: 'X' }),
  });
  assert.equal(badCode.status, 400);
  console.log('5. bad code ->', badCode.status);

  // 4. WebSocket: welcome, ping/pong, a control that comes back as state
  const socket = new WebSocket(`${wsBase}/ws/parties/${created.code}?token=${created.token}`);
  const inbox = [];
  socket.on('message', (data) => inbox.push(JSON.parse(data.toString())));
  await new Promise((resolve, reject) => {
    socket.once('open', resolve);
    socket.once('error', reject);
  });
  await waitFor(inbox, (f) => f.type === 'welcome');
  console.log('6. ws welcome');

  socket.send(JSON.stringify({ type: 'ping', clientMs: 12345 }));
  const pong = await waitFor(inbox, (f) => f.type === 'pong');
  assert.equal(pong.clientMs, 12345);
  assert.ok(typeof pong.serverMs === 'number');
  console.log('7. ws pong echoed clientMs, serverMs:', pong.serverMs);

  socket.send(
    JSON.stringify({
      type: 'control',
      action: 'setTrack',
      track: { videoId: 'vid1', title: 'Song', artist: 'Artist', durationMs: 200_000 },
      positionMs: 0,
      isPlaying: true,
    }),
  );
  const state = await waitFor(inbox, (f) => f.type === 'state' && f.playback.track);
  assert.equal(state.playback.track.title, 'Song');
  assert.equal(state.playback.isPlaying, true);
  console.log('8. ws state after control: track =', state.playback.track.title);

  socket.send(JSON.stringify({ type: 'sync' }));
  await waitFor(inbox, (f) => f.type === 'state');
  console.log('9. ws sync returned state');

  socket.close();
  console.log('\nSMOKE OK');
}

function waitFor(inbox, predicate, timeoutMs = 3000) {
  return new Promise((resolve, reject) => {
    const deadline = Date.now() + timeoutMs;
    const poll = () => {
      const found = inbox.find(predicate);
      if (found) return resolve(found);
      if (Date.now() > deadline) return reject(new Error('timed out waiting for a frame'));
      setTimeout(poll, 20);
    };
    poll();
  });
}

main().catch((error) => {
  console.error('SMOKE FAILED:', error);
  process.exit(1);
});
