# Velthy Listen Together — party server (NestJS)

The party server behind **Listen together**: create a six-character code, share
it, and up to five signed-in devices listen to the same thing at the same time.
Anyone in the party can control the music.

**REST + one WebSocket per device, no database, one process.** Nest owns the REST
half and the wiring; the socket half is handled directly on `ws`, for the reason
spelled out in `src/party/party.socket.ts` — the frames are flat `{ "type": … }`
and are shared byte-for-byte with the reference server, so the Nest WS envelope
would be a second wire format.

The wire protocol is **identical** to the reference implementation. A client
written against either works with both.

```bash
npm ci
npm run build          # compile to dist/
node startup.mjs       # run it (prefers dist/, falls back to src via tsx)

npm test               # unit tests: the sync rule, as arithmetic
npm run smoke          # end-to-end, against a server already running
```

---

## `startup.mjs` — the one entry point

Everything about starting the process lives in one `.mjs` file, in order, so that
"how does it run" has exactly one answer:

1. **environment** — read and validated *before* a port is bound, so a bad value
   is a startup error rather than a mystery later;
2. **app** — build the Nest application (REST + WebSocket on one port);
3. **listen** — bind, and say so;
4. **shutdown** — on `SIGTERM`/`SIGINT`, stop accepting, drain, exit.

It is `.mjs` rather than Nest's conventional `main.ts` on purpose: Nest boots from
`main.ts`, which means two entry points the moment anything has to run before the
container exists (validation, a preflight log, a signal handler outside the app).
This file is that single entry point, and it is the *same file* in development and
production — it loads `dist/bootstrap.js` when it exists and `src/bootstrap.ts`
through `tsx` when it does not.

---

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/` | service banner, `maxMembers`, live `parties`, `serverMs` |
| `GET` | `/healthz` | liveness, and the server clock |
| `GET` | `/api/time` | one reading of the server clock, for a client with no socket yet |
| `POST` | `/api/parties` | mint a code and join it as host |
| `POST` | `/api/parties/{code}/join` | join an existing party |
| `GET` | `/api/parties/{code}` | full snapshot (bearer token) |
| `POST` | `/api/parties/{code}/leave` | leave (bearer token) |
| `WS` | `/ws/parties/{code}?token=…` | the feature: controls up, state down, clock samples both ways |

---

## How devices stay in time

This is the part worth reading before changing anything. Nothing here is
synchronised by broadcasting "play now" — a frame that arrives 40 ms late on one
phone and 300 ms late on another would start them a quarter of a second apart,
and re-sending it more often would not fix that, only re-spread it.

Instead the server holds **a position and the server time that position was true
at**:

```jsonc
{
  "positionMs": 42000,          // where the party was...
  "anchorMs": 1757630001234,    // ...at this instant on the server's clock
  "isPlaying": true
}
```

Each device knows its own offset from that clock, so it can answer "where should
I be *right now*" locally, whenever it likes, without another round trip:

```
serverNow   = deviceNow + clockOffset
playhead    = positionMs + max(0, serverNow - anchorMs)      // while playing
playhead    = positionMs                                      // while paused
```

A delayed frame carries an anchor that is correspondingly further in the past, so
it still lands the device in exactly the right place. That is the whole trick, and
four things protect it:

1. **The clock offset is measured, not assumed.** Each device sends
   `{"type":"ping","clientMs":…}`; the server replies with that stamp untouched
   plus its own. Offset is `serverMs - (t0 + t1) / 2`, error is bounded by half
   the round trip, and keeping the sample with the *smallest* round trip is what
   makes this accurate over mobile data. Same idea as NTP.
2. **The server clock never jumps.** `src/common/clock.ts` reads the wall clock
   once and advances it monotonically after that (`performance.now()`), so an NTP
   step on the host cannot rewrite the anchor under a room full of phones at once.
3. **Resuming is scheduled slightly ahead.** Play and seek anchor
   `JAM_PLAY_LEAD_MS` (default 350 ms) into the future, so every device aims at
   one common instant and has time to buffer.
4. **The truth is re-stated on a timer.** Every `JAM_STATE_HEARTBEAT_MS`
   (default 5 s) each party is told again where it is, unprompted. Lost frames, a
   phone coming back from doze, an offset that has wandered — none of those
   announce themselves, so correctness must not depend on anyone asking.

The server's state is the truth. Devices *report* their real playhead
(`{"type":"report"}`), but that is only logged — a party is never averaged
towards a straggler on a bad connection.

---

## Deploying on Pterodactyl

### Option A — import the egg (recommended)

1. Pterodactyl admin → **Nests** → **Import Egg** → upload `egg-velthy-party.json`.
2. Create a server on that egg with a Node 22 image and **one** allocation.
3. Upload the source so `/home/container` holds `package.json`, `src/`, and
   `startup.mjs`, then **Reinstall** — the install script runs `npm ci`,
   `npm run build`, and `npm prune --omit=dev`.
4. Start. The console prints `listening on 0.0.0.0:<port>`.
5. Point the app at `http://<host>:<port>`.

> **One instance only.** A party lives in the memory of the process holding its
> WebSockets, so two instances are two disjoint sets of parties and a join lands
> wherever the load balancer picks. Do **not** enable autoscaling. See
> `PartyStore` in `src/party/party.ts`.

### Option B — Docker

```bash
docker build -t velthy-party .
docker run -p 8080:8080 velthy-party
```

---

## Deploying an update (and knowing it landed)

A restart does **not** mean new code is running. There are three separate things
that can be stale, and each used to fail quietly:

| What can be stale | What it looks like | What fixes it |
|---|---|---|
| The files on disk | the panel pulled, the code did not change | automatic: the server follows its own repository |
| `dist/` — the compiled output | sources are new, the server runs the previous build | automatic: rebuilt when `src/` is newer |
| The process | it is running the code it started with | restart |

The middle one is the trap that started all of this: `dist/` is what the server
actually loads, and it is *preferred* over `src/`, so a fresh checkout beside an
old build changes nothing at all — no error, no warning, just a server that is
healthy and out of date. `startup.mjs` now compares the two and rebuilds when
`src/` is newer, so a restart always runs at least the code that is on disk.

### 1. Nothing to configure

The server follows its own repository **by default**. The three values it needs
are compiled into `startup.mjs`:

| | |
|---|---|
| repository | `https://github.com/Synxx12/velthy.git` |
| branch | `main` |
| subfolder | `backend-nest` — this project is one folder of a monorepo |

So a restart is a deploy. The log says what happened:

```
[startup] updated to 07747ac: 3 file(s) changed.
[startup] already current at 07747ac -- nothing changed.
```

Overrides exist for a fork or another branch — `GIT_URL`, `GIT_BRANCH`,
`GIT_SUBDIR` — and `GIT_PULL=0` turns the whole thing off.

**A development tree turns it off by itself.** A folder that sits *inside*
somebody else's checkout is never updated, because the sources there are
uncommitted edits that exist nowhere else. `GIT_PULL=1` forces it on anywhere.

### How the update works on an uploaded deployment

A panel's folder holds the *contents* of this project — `package.json`, `src/`,
`startup.mjs` — and the repository those came from has all of it one level down
in `backend-nest/`. There is no `.git` to pull with, and `git init` here would be
wrong: checking the branch out into this directory would scatter the whole
monorepo across it, an `app/` folder beside `src/`.

So the repository is cloned **beside** the tree, into `.velthy-repo/`, and the
one subfolder is copied out of it. A clone carries tracked files only, so
`node_modules/`, `dist/`, `.env`, and anything put here by hand all survive —
and nothing is ever deleted, only written over.

The copy is idempotent: a restart on an unchanged commit writes nothing, which is
what makes the ordinary restart cheap.

### 2. Check what is actually running

From the repository, against the deployed server:

```bash
npm run version                                  # the built-in server
npm run version -- https://api.velthy.my.id      # or any server
```

It prints both sides and exits non-zero when the server is behind:

```
repo:   1.0.0 (d5ef700)
server: 1.0.0 (unknown) · protocol 2
        https://api.velthy.my.id

STALE — the server is missing: queue-deltas, activity-feed, host-controls
```

Or by hand — the banner names the build and every capability it has:

```bash
curl -s https://api.velthy.my.id/ | jq
{
  "service": "velthy-listen-together",
  "version": "1.0.0",
  "commit": "d5ef700…",
  "protocol": 2,
  "features": ["queue-deltas", "activity-feed", "host-controls",
               "party-preview", "per-party-size"],
  …
}
```

A server missing a feature from that list is running an older build. That is the
only reliable way to tell from outside: an out-of-date server answers `/healthz`
with `ok: true`, accepts sockets, and otherwise looks exactly like a current one.

### 3. The panel's own startup command

A generic Pterodactyl Node egg runs its own command, and some of them dispatch on
the main file with `[[ "${MAIN_FILE}" == "*.js" ]]` — quoted, so it is a literal
comparison that never matches anything, and **every** server on that egg is run
through `ts-node` instead of `node`. `ts-node` is not a dependency of this
project and is not compatible with every Node version, so the process can die
before a single line of `startup.mjs` runs.

If the log shows `ts-node --esm` rather than `node`, set the startup command
explicitly to:

```
node startup.mjs
```

That is the whole fix, and it is worth doing regardless of which egg is in use.

---

## Cloudflare Tunnel (token)

The quickest way to get a party reachable over HTTPS/WSS from a phone on mobile
data, with no port-forwarding and no certificate to manage. A **token tunnel** is
configured entirely in the Cloudflare dashboard, so there is no config file to
keep — the token is the whole setup.

### 1. Install cloudflared

```bash
npm install --save-dev cloudflared      # ships its own binary
npx cloudflared --version
```

On a server where npm cannot fetch the binary, use the package instead:
`sudo apt-get install -y cloudflared`.

### 2. Get the token

Cloudflare **Zero Trust → Networks → Tunnels → Create a tunnel → Cloudflared**,
name it (e.g. `velthy-party`), save, and copy the token from the install command
shown:

```bash
# what the dashboard shows — you do NOT need this form
sudo cloudflared service install eyJhIjoi...LONG_TOKEN...
```

### 3. Put the token in `.env`

```bash
cp .env.example .env
# edit .env, set:
#   TUNNEL_TOKEN=eyJhIjoi...LONG_TOKEN...
```

`.env` is git-ignored. The token is a credential — it lets anyone run the
tunnel — so it is the one value that must never be committed.

### 4. Run — one command, both halves

```bash
npm start          # same as: node startup.mjs
```

That is the whole of it. With `TUNNEL_TOKEN` set, `startup.mjs` starts the
server, waits for `/healthz` to answer, then starts cloudflared **as its own
child process** and ties the two together: the tunnel comes up with the server
and is taken down with it. Without the token it serves locally and says so.

There is deliberately no second command to run. A control panel runs exactly one
command, and chaining the two with `&` in the panel's startup line only holds
while that shell lives — restart the server and the tunnel is left behind,
holding the tunnel open so the next attempt fails with "already connected".

To run the tunnel on its own (a second host, or debugging), `npm run tunnel`
still works: `scripts/tunnel.mjs` reads the same token and hands it to
cloudflared through the environment rather than as a command-line argument — an
argument is visible in `ps` to every user on the machine.

### 5. Point the hostname at the server

This step is what "the origin" means, and it happens **entirely in the
dashboard** — a token tunnel carries no local config, so there is no
`TUNNEL_ORIGIN` in `.env` and editing one there would do nothing. The origin is
the local address the tunnel forwards to; the hostname is what the public sees.

On the tunnel → **Public Hostname** → **Add a public hostname**:

| Field | Value |
|---|---|
| Subdomain | `api` |
| Domain | `velthy.my.id` |
| Path | *(leave empty)* |
| Service Type | `HTTP` |
| URL | `localhost:33183` |

That yields the public hostname **`api.velthy.my.id`**. The port in **URL** is
the origin, and it must match whatever the server actually listens on — here
`33183`, the port this deployment's host allocates (the host offers only
`33183` / `37720` / `25620`). A tunnel whose origin names a different port than
`PORT` in `.env` is the whole of "the tunnel is up but the app can't reach it".

**One hostname is enough.** The party server serves REST and WebSocket on the
same port, and Cloudflare forwards the WebSocket upgrade on an HTTP route — there
is no separate WSS configuration.

The app then points at `https://api.velthy.my.id`, and dials
`wss://api.velthy.my.id/ws/parties/{code}?token=…` by itself.

> Only one instance of the server may run per tunnel, for the reason in
> `PartyStore`: a party lives in the memory of the process holding its WebSockets.

### WebSocket note for hosts

Pterodactyl's game eggs often block or proxy oddly. Use a webserver/web-app egg
(or the one here) and make sure your **reverse proxy** passes the WebSocket
upgrade:

```nginx
location /ws/ {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 3600s;
}
```

Terminate TLS at the proxy and point the app at `https://`/`wss://`.

---

## Configuration

Every knob is an environment variable with a working default; nothing is
required. See `src/common/config.ts` for the validation bounds, and
`egg-velthy-party.json` for the descriptions shown in the panel.

| Variable | Default | Meaning |
|---|---|---|
| `PORT` / `SERVER_PORT` | `8080` | listen port (`BIND_ADDR` overrides outright). This deployment uses **`33183`**. |
| `JAM_MAX_MEMBERS` | `5` | devices per party |
| `JAM_STATE_HEARTBEAT_MS` | `5000` | unprompted state re-broadcast interval |
| `JAM_PLAY_LEAD_MS` | `350` | scheduled-start lead |
| `JAM_DISCONNECT_GRACE_MS` | `45000` | a dropped socket keeps its slot this long |
| `JAM_EMPTY_PARTY_TTL_MS` | `120000` | empty-party reaping |
| `JAM_PARTY_MAX_AGE_MS` | `43200000` | hard party lifetime |
| `JAM_CONTROL_RATE_PER_SECOND` | `25` | per-member control token bucket |
| `JAM_MAX_QUEUE_LENGTH` | `500` | queue size ceiling |
| `JAM_ALLOWED_ORIGINS` | *(empty)* | browser origins allowed to open a socket |

---

## Layout

```
startup.mjs               the one entry point: env, app, listen, shutdown
scripts/tunnel.mjs        runs cloudflared with the token from .env
.env.example              every knob, and where the tunnel token goes
src/bootstrap.ts          builds the app; one HTTP server for REST + WS
src/app.module.ts         wiring only
src/app.controller.ts     the REST half (paperwork)
src/party/
  party.ts                the domain: Track, PlaybackState, Member, Party, Store
  party.service.ts        the service layer: store, hub, frame builders
  party.socket.ts         the WebSocket half, on `ws` directly
src/hub/hub.ts            who is holding a socket, and how a frame reaches them
src/common/
  clock.ts                the one monotonic server clock every device measures against
  codes.ts                six-character, read-aloud party codes
  config.ts               environment knobs, validated once at boot
  protocol.ts             frame names, and the join-body rules
  heartbeat.service.ts    the timer that re-states the truth, and sweeps
  all-exceptions.filter.ts one error shape for the whole app
checks/party.test.ts      the sync rule, as arithmetic
checks/smoke.mjs          end-to-end against a running server
```

Nothing under `src/party/party.ts` touches HTTP or WebSockets. That is
deliberate: the sync rule is testable as arithmetic, not through a socket, which
is what `checks/party.test.ts` does with no Nest container at all.
