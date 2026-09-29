#!/usr/bin/env node
/**
 * Velthy Listen Together -- the one entry point.
 *
 * Everything about starting this server lives here, in order, so that "how does
 * it run" has exactly one answer:
 *
 *   1. environment  -- read and validate before a port is bound, so a bad value
 *                     is a startup error rather than a mystery at 3am
 *   2. app          -- build the Nest application (REST + WebSocket on one port)
 *   3. listen       -- bind, and say so
 *   4. tunnel       -- if TUNNEL_TOKEN is set, bring cloudflared up beside it
 *   5. shutdown     -- on SIGTERM/SIGINT, stop accepting, drain, exit
 *
 * It is an `.mjs` file on purpose. Nest conventionally boots from `main.ts`,
 * which means two entry points once anything needs to run before the Nest
 * container exists (validation, a preflight log, a signal handler that is not
 * inside the app). This file is that single entry point instead, and it is the
 * same file in development and in production -- `src/` is loaded through tsx when
 * the compiled `dist/` is not present, and `dist/` is preferred when it is.
 *
 * ## The tunnel runs from here, not from the panel's command line
 *
 * A control panel runs exactly one command, and the two halves of this service
 * must be alive at the same instant -- a tunnel with no server behind it answers
 * 502s, and a server with no tunnel is unreachable from a phone. Chaining them
 * with `&` in the panel's startup line works only as long as the shell keeps
 * both, and leaves the tunnel behind the moment the server is restarted. So the
 * tunnel is a *child of this process* instead: started once the port is bound,
 * and taken down with it.
 *
 *   TUNNEL_TOKEN set    -> server + cloudflared, both supervised here
 *   TUNNEL_TOKEN unset  -> server only (a purely local run is legitimate)
 *
 * Run:  node startup.mjs
 */
import { spawn, spawnSync } from 'node:child_process';
import {
  existsSync,
  readFileSync,
  readdirSync,
  renameSync,
  rmSync,
  statSync,
} from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));

/** Whether an environment flag reads as "on". Absent, blank and "0" are all off. */
function flagOn(name) {
  const value = process.env[name]?.trim().toLowerCase();
  return value === '1' || value === 'true' || value === 'yes';
}

/**
 * Run a command to completion, with its output going straight to this process's.
 *
 * Synchronous on purpose: every caller here is deciding whether the *next* thing
 * is safe to do, and a pull that has not finished is a tree that cannot be
 * built. There is no concurrency to win by doing it in the background.
 *
 * @return true when the command exited 0. A missing binary is a false rather
 *   than a throw, because "git is not installed" is an answer this needs to be
 *   able to act on, not a crash.
 */
function run(command, args) {
  const result = spawnSync(command, args, {
    cwd: here,
    stdio: 'inherit',
    // Windows resolves `npm` to `npm.cmd` only through a shell; POSIX is
    // unaffected either way.
    shell: process.platform === 'win32',
  });
  if (result.error) {
    console.warn(`[startup] could not run ${command}: ${result.error.message}`);
    return false;
  }
  return result.status === 0;
}

/** Run a command and capture its trimmed stdout, or null if it failed. */
function capture(command, args) {
  const result = spawnSync(command, args, {
    cwd: here,
    encoding: 'utf8',
    stdio: ['ignore', 'pipe', 'ignore'],
    shell: process.platform === 'win32',
  });
  if (result.error || result.status !== 0) return null;
  return (result.stdout ?? '').trim();
}

/**
 * Rebuild `dist/`, keeping the old one until the new one is known to be good.
 *
 * `npm run build` deletes `dist/` before compiling, which is fine on a
 * workstation and actively harmful here: this runs on a server that is about to
 * start, and a compile error would leave no `dist/` *and* no source fallback --
 * `tsx` is a dev dependency and production installs prune it, so the process
 * would exit with nothing to serve. So the old build is moved aside first and
 * put back if the new one fails, and the caller is told either way.
 *
 * @return true when `dist/` holds a freshly compiled build.
 */
function rebuild() {
  const dist = join(here, 'dist');
  const aside = join(here, 'dist.stale');
  const hadDist = existsSync(dist);

  if (hadDist) {
    rmSync(aside, { recursive: true, force: true });
    try {
      renameSync(dist, aside);
    } catch (error) {
      console.warn(`[startup] could not set the old build aside: ${error.message}`);
      return false;
    }
  }

  const built = run('npm', ['run', 'build']);
  if (built) {
    rmSync(aside, { recursive: true, force: true });
    return true;
  }

  console.warn('[startup] the rebuild failed; putting the previous build back.');
  if (hadDist) {
    rmSync(dist, { recursive: true, force: true });
    try {
      renameSync(aside, dist);
    } catch (error) {
      console.warn(`[startup] could not restore the previous build: ${error.message}`);
    }
  }
  return false;
}

/**
 * Give an uploaded tree the repo identity it was never given.
 *
 * The question this answers is "which repository is this server following?" —
 * and the honest answer for a deployment whose files were uploaded rather than
 * cloned is that it does not have one. `git pull` reads `remote.origin.url` out
 * of `.git/config`; no `.git` means no URL, no branch, and no update is
 * possible. Nothing about GitHub can fix that from its side: a repository
 * cannot reach into a machine that never told it where it is.
 *
 * So it is told, once, through `GIT_URL`. This writes the same three lines a
 * `git clone` would have — an init, a remote, and the branch's tip — and from
 * then on the ordinary update path above applies.
 *
 * ## What this does not touch
 *
 * `reset --hard` restores *tracked* files to the branch's copy and leaves
 * everything else alone. So `.env` (a credential the repo ignores), the whole
 * of `node_modules/`, and any file somebody put here by hand all survive — the
 * experiment in `checks/` asserts exactly that. What does not survive is a
 * local edit to a file the repo also tracks, which is why [autoUpdate] refuses
 * to run against a dirty tree at all.
 *
 * @return true when `.git` now exists and points at a fetched commit.
 */
function bootstrapRepo(url, branch) {
  if (!capture('git', ['--version'])) {
    console.warn('[startup] git is not available, so the repository cannot be set up.');
    return false;
  }

  console.log(`[startup] no .git here -- setting this tree up to follow ${url} (${branch}).`);
  if (!run('git', ['init', '-q', '-b', branch])) {
    console.warn('[startup] git init failed.');
    return false;
  }
  if (!run('git', ['remote', 'add', 'origin', url])) {
    console.warn('[startup] could not add the remote.');
    return false;
  }
  // Shallow on purpose: a server needs the tip of one branch, not the history
  // of a repository it will never commit to. It also makes the first fetch
  // seconds rather than minutes.
  if (!run('git', ['fetch', '-q', '--depth=1', 'origin', branch])) {
    console.warn('[startup] could not fetch from that repository -- check GIT_URL and the branch.');
    return false;
  }
  if (!run('git', ['reset', '--hard', 'FETCH_HEAD'])) {
    console.warn('[startup] fetched, but could not check the files out.');
    return false;
  }
  // Shallow fetches do not update `origin/<branch>` the way a full one does, so
  // the ref the update path compares against is written here explicitly.
  run('git', ['update-ref', `refs/remotes/origin/${branch}`, 'FETCH_HEAD']);
  console.log('[startup] repository set up; future boots can update from it.');
  return true;
}

/**
 * Bring the checkout up to date, then rebuild -- before anything is loaded.
 *
 * Off by default, and switched on with `GIT_PULL=1`. That is deliberate: this
 * rewrites the working tree of whatever it is run in, which is the right thing
 * on a server that exists to follow a branch and the wrong thing on a
 * workstation somebody is in the middle of editing. A deployment that wants it
 * sets the flag once.
 *
 * `GIT_URL` is what makes that possible on a deployment whose files were
 * uploaded rather than cloned -- see [bootstrapRepo]. It is only read when
 * there is no `.git` to read a remote from, so setting it on a real checkout
 * changes nothing.
 *
 * Every failure is survivable and every one of them is only a warning. The
 * server starting on the code it already has is always better than the server
 * not starting, so a missing `git`, a detached head, a dirty tree, a branch
 * that has moved on with a conflict -- all of them end in "carry on with what is
 * on disk" rather than an exit.
 *
 * Ordering is the whole point of where this is called from: `dist/` is preferred
 * over `src/` by [loadBootstrap], so pulling without rebuilding would change
 * nothing at all. The rebuild has to happen here, before that decision is made.
 */
function autoUpdate() {
  if (!flagOn('GIT_PULL')) {
    // Said plainly, because this is the setting whose absence makes a deploy
    // look like it silently did nothing: the panel pulls the repository, this
    // server never does, and the sources it starts from are whatever the panel
    // left behind. [loadBootstrap] still rebuilds when those sources are newer
    // than the build, so the code that runs is at least the code on disk.
    console.log(
      '[startup] GIT_PULL not set -- this server does not update itself.\n' +
        '          It will run the code on disk, rebuilding dist/ if src/ is newer.',
    );
    return;
  }

  const branch = process.env.GIT_BRANCH?.trim() || 'main';
  const remote = process.env.GIT_REMOTE?.trim() || 'origin';
  const url = process.env.GIT_URL?.trim() || '';

  // The one case a repository can be pointed at from outside: an uploaded tree
  // that has no identity of its own yet.
  if (!existsSync(join(here, '.git'))) {
    if (!url) {
      console.log(
        '[startup] GIT_PULL is set but there is no .git here -- nothing to pull.\n' +
          '          Set GIT_URL to the repository this server should follow and it\n' +
          '          will be set up on the next start; see the README.',
      );
      return;
    }
    if (!bootstrapRepo(url, branch)) {
      console.warn('[startup] starting on the code already here.');
      return;
    }
  }

  if (!capture('git', ['--version'])) {
    console.warn('[startup] GIT_PULL is set but git is not available -- skipping.');
    return;
  }

  // A dirty tree is not an error to report at every boot -- it is the normal
  // state of a server somebody has patched by hand. Checking out over it would
  // either fail or overwrite their change, so neither is attempted.
  const dirty = capture('git', ['status', '--porcelain']);
  if (dirty === null) {
    console.warn('[startup] could not read the git status -- skipping the update.');
    return;
  }
  if (dirty.length > 0) {
    console.warn(
      `[startup] the working tree has local changes (${dirty.split('\n').length} file(s));\n` +
        '          not updating, because it would overwrite them.\n' +
        '          Commit or discard them, or delete .git to start following the\n' +
        '          branch again from scratch.',
    );
    return;
  }

  if (!run('git', ['fetch', '-q', '--prune', remote, branch])) {
    console.warn('[startup] git fetch failed -- starting on the code already here.');
    return;
  }

  // Compared by commit id rather than by `rev-list --count`. A shallow fetch
  // has no history to count through, and this deployment's first fetch is
  // shallow by design -- so the count would be a number nobody could trust.
  const local = capture('git', ['rev-parse', 'HEAD']);
  const target = capture('git', ['rev-parse', `refs/remotes/${remote}/${branch}`]);
  if (local === null || target === null) {
    console.warn(`[startup] could not compare against ${remote}/${branch} -- skipping.`);
    return;
  }
  if (local === target) {
    console.log(`[startup] already up to date with ${remote}/${branch} (${local.slice(0, 7)}).`);
    return;
  }

  console.log(`[startup] ${local.slice(0, 7)} -> ${target.slice(0, 7)} on ${remote}/${branch}.`);
  const lockBefore = existsSync(join(here, 'package-lock.json'))
    ? readFileSync(join(here, 'package-lock.json'), 'utf8')
    : '';

  // A hard checkout rather than a merge. There is nothing here to merge with:
  // the tree is clean by the check above, this process holds no commits of its
  // own, and a merge commit on a server nobody is watching is how a deployment
  // ends up in a state its own history cannot explain. The files the repo does
  // not track -- `.env`, `node_modules/`, anything hand-written -- are outside
  // this either way.
  if (!run('git', ['reset', '--hard', `refs/remotes/${remote}/${branch}`])) {
    console.warn('[startup] could not check out the new commit -- starting on the code already here.');
    return;
  }

  // Only when the dependency graph actually moved. `npm ci` deletes
  // node_modules first, so running it on every restart would turn a fast boot
  // into a slow one for no reason.
  const lockAfter = existsSync(join(here, 'package-lock.json'))
    ? readFileSync(join(here, 'package-lock.json'), 'utf8')
    : '';
  if (lockAfter !== lockBefore) {
    console.log('[startup] dependencies changed; installing.');
    if (!run('npm', ['ci'])) {
      console.warn('[startup] npm ci failed -- the build below may not be usable.');
    }
  }

  if (rebuild()) {
    console.log('[startup] updated and rebuilt.');
  } else {
    console.warn('[startup] updated, but the rebuild did not succeed.');
  }
}

/**
 * Fold `.env` into the environment, without overwriting what is already set.
 *
 * The panel passes its variables in for real, so this only matters for a local
 * `node startup.mjs` -- and it is done here rather than by pulling in `dotenv`
 * so the one entry point stays dependency-free.
 */
function loadEnvFile() {
  const path = join(here, '.env');
  if (!existsSync(path)) return;
  for (const rawLine of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq === -1) continue;
    const key = line.slice(0, eq).trim();
    let value = line.slice(eq + 1).trim();
    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }
    if (!(key in process.env)) process.env[key] = value;
  }
}

/**
 * The newest modification time under a path, or 0 when it cannot be read.
 *
 * Recursive, because a build is driven by every file under `src/` rather than by
 * any one of them. Skipping `node_modules`, `.git` and `dist` keeps the walk
 * cheap: those are the three directories that can be large and none of them is a
 * source of the compiled output.
 */
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
  let entries;
  try {
    entries = readdirSync(path, { withFileTypes: true });
  } catch {
    return newest;
  }
  for (const entry of entries) {
    if (entry.name === 'node_modules' || entry.name === '.git' || entry.name === 'dist') continue;
    newest = Math.max(newest, newestMtimeMs(join(path, entry.name), depth + 1));
  }
  return newest;
}

/**
 * Whether `dist/` is older than the sources it is supposed to be built from.
 *
 * This is the check whose absence made a deploy look like it worked. The panel
 * pulls new code into `src/`, the old `dist/` is still there, and [loadBootstrap]
 * prefers `dist/` -- so the process starts happily on the *previous* build, with
 * no error anywhere and no way to tell from outside. "Restarted and nothing
 * changed" is exactly that.
 *
 * Modification times are what git gives us: a checkout stamps the files it
 * rewrites, and `tsc` stamps everything it emits, so a source newer than the
 * build means the build is behind. Nothing is inferred from version numbers or
 * hashes -- only from the files on this disk.
 */
function distIsStale() {
  const dist = join(here, 'dist');
  if (!existsSync(join(dist, 'bootstrap.js'))) return true;

  const built = newestMtimeMs(dist);
  const sources = Math.max(
    newestMtimeMs(join(here, 'src')),
    // The build's own configuration: a changed target or module setting is a
    // reason to re-emit, and it is not under src/.
    newestMtimeMs(join(here, 'tsconfig.json')),
  );
  if (sources === 0) return false; // no sources to compare against
  return sources > built;
}

/**
 * The module that actually builds the app, resolved against what has been built.
 *
 * `dist/` wins when it exists because that is the deployed artefact; a source
 * tree loaded through tsx is the development path. Resolving here -- rather than
 * making `npm start` differ between the two -- is what keeps this file identical
 * in both.
 *
 * ## Why this rebuilds
 *
 * Two cases, and both of them used to end in the same silent failure -- a server
 * running yesterday's code while reporting itself healthy.
 *
 * **Nothing compiled.** A panel that ran `npm install` but never `npm run build`
 * is the ordinary way to arrive here with no `dist/` at all, and the source
 * fallback below is not always available to cover it: `tsx` runs on `esbuild`,
 * and a panel with install scripts locked down refuses esbuild's postinstall --
 * the package is present, its binary is not, and importing it throws.
 *
 * **Compiled, but stale.** A panel that pulls the repository itself, before this
 * file is even reached, leaves new sources beside an old build. `dist/` is
 * preferred, so the new code never ran. See [distIsStale].
 *
 * Neither is attempted on a healthy boot: a `dist/` that is newer than its
 * sources is loaded as-is, which is the ordinary case and the fast one.
 */
async function loadBootstrap() {
  const compiled = join(here, 'dist', 'bootstrap.js');
  const hasSources = existsSync(join(here, 'src', 'bootstrap.ts'));

  if (hasSources && distIsStale()) {
    const reason = existsSync(compiled)
      ? 'dist/ is older than src/ -- rebuilding before it is loaded.'
      : 'no dist/ -- building it once from src/.';
    console.log(`[startup] ${reason}`);
    if (!rebuild()) {
      console.error(
        '[startup] THE BUILD FAILED. What runs next is NOT the latest code:\n' +
          '          either the previous build, or the sources through tsx if that\n' +
          '          works. Fix the compile error above, or run `npm run build` by\n' +
          '          hand to see it in full.',
      );
    }
  }

  if (existsSync(compiled)) {
    return import(pathToFileURL(compiled).href);
  }
  // Development: compile the TypeScript on the fly.
  const { register } = await import('tsx/esm/api').catch(() => {
    throw new Error(
      'No dist/bootstrap.js, and tsx cannot be used either.\n' +
        'Run `npm run build` to produce dist/, or `npm install` for development.\n' +
        'On a panel that blocks install scripts, `npm run build` is the one that\n' +
        'works: it needs only the TypeScript compiler, which has no postinstall.',
    );
  });
  const unregister = register();
  try {
    return await import(pathToFileURL(join(here, 'src', 'bootstrap.ts')).href);
  } finally {
    unregister();
  }
}

/**
 * The cloudflared binary, bundled by the `cloudflared` npm package.
 *
 * The real executable is preferred over the `.bin/cloudflared.cmd` shim on
 * Windows: a shim is a batch script, a batch script cannot be spawned without a
 * shell, and a shell then re-parses the arguments -- which breaks on any path
 * containing a space, which the development tree's does. Falling back to the
 * name on PATH covers a system-wide install.
 */
function resolveCloudflared() {
  const isWindows = process.platform === 'win32';
  const bundled = join(
    here,
    'node_modules',
    'cloudflared',
    'bin',
    isWindows ? 'cloudflared.exe' : 'cloudflared',
  );
  if (existsSync(bundled)) return bundled;
  return isWindows ? 'cloudflared.exe' : 'cloudflared';
}

/** The port this process is bound to, from the same ladder the config uses. */
function resolvePort() {
  const raw = process.env.PORT?.trim() || process.env.SERVER_PORT?.trim() || '8080';
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : 8080;
}

/**
 * Poll the app's own health route until it answers, or give up.
 *
 * The tunnel is started only after this succeeds. Spawned at the same instant
 * as the server it would dial out immediately and serve 502s for however long
 * the port stayed unbound -- harmless in production, and thoroughly confusing on
 * a first run where somebody is watching for it.
 */
async function waitForServer(port, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`http://127.0.0.1:${port}/healthz`);
      if (response.ok) return true;
    } catch {
      // Not up yet, which is the expected answer for the first several tries.
    }
    await new Promise((done) => setTimeout(done, 250));
  }
  return false;
}

/** A child's output, a line at a time, tagged with which child it came from. */
function prefixOutput(stream, label, colour) {
  let buffer = '';
  stream.on('data', (chunk) => {
    buffer += chunk.toString();
    const lines = buffer.split(/\r?\n/);
    buffer = lines.pop() ?? '';
    for (const line of lines) {
      if (line.length === 0) continue;
      process.stdout.write(`${colour}[${label}]\x1b[0m ${line}\n`);
    }
  });
}

/**
 * Start the tunnel beside the server, and tie the two together.
 *
 * The tunnel is deliberately *not* fatal to the server. If cloudflared is
 * missing or cannot connect, this process keeps serving on its port -- which is
 * still useful (a local client, a reverse proxy in front) and is the only
 * behaviour that lets somebody debug the tunnel without also losing the API.
 */
async function startTunnel(port) {
  const token = process.env.TUNNEL_TOKEN?.trim();
  if (!token) {
    console.log('[startup] TUNNEL_TOKEN not set -- serving locally only.');
    return null;
  }

  const ready = await waitForServer(port, 30_000);
  if (!ready) {
    // Late enough that something is wrong, but not proof the server is dead:
    // a cold source tree compiles for a while. Warned, then attempted anyway,
    // because by the time a request arrives the port is very likely bound.
    console.warn('[startup] /healthz did not answer within 30s; starting the tunnel anyway.');
  }

  console.log('[startup] starting cloudflared.');
  const child = spawn(resolveCloudflared(), ['tunnel', '--no-autoupdate', 'run'], {
    cwd: here,
    // Through the environment, never as an argv entry: an argument is visible
    // in `ps` to every other user on the machine, and this one is a credential.
    env: { ...process.env, TUNNEL_TOKEN: token },
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  });
  prefixOutput(child.stdout, 'tunnel', '\x1b[35m');
  prefixOutput(child.stderr, 'tunnel', '\x1b[35m');

  child.on('error', (error) => {
    if (error.code === 'ENOENT') {
      console.error(
        '[startup] cloudflared not found -- the server keeps running.\n' +
          '          Install it with `npm install --save-dev cloudflared`, or\n' +
          '          `sudo apt-get install -y cloudflared` on a server.',
      );
    } else {
      console.error(`[startup] could not start cloudflared: ${error.message}`);
    }
  });

  return child;
}

async function main() {
  loadEnvFile();

  // Before the application is loaded, not after -- see [autoUpdate]. `dist/` is
  // preferred over `src/` by [loadBootstrap], so a pull that is not followed by
  // a rebuild changes nothing at all.
  autoUpdate();

  let bootstrap;
  try {
    ({ bootstrap } = await loadBootstrap());
  } catch (error) {
    console.error('[startup] could not load the application:', error.message);
    process.exit(1);
  }

  try {
    await bootstrap();
  } catch (error) {
    console.error('[startup] failed to start:', error);
    process.exit(1);
  }

  // After `bootstrap` has resolved, the port is bound and the app is serving.
  const tunnel = await startTunnel(resolvePort());

  /**
   * Take the tunnel down with the server.
   *
   * A process killed from a panel sends no signal this can rely on, which is
   * why the tie is drawn both ways: the handlers below cover an interactive
   * Ctrl-C, and `exit` is the last word for everything else.
   */
  const stopTunnel = () => {
    if (tunnel && tunnel.exitCode === null && tunnel.signalCode === null) {
      // Windows has no SIGTERM to deliver to a non-console child; kill() is the
      // portable spelling and is what actually stops it here.
      tunnel.kill();
    }
  };

  /**
   * How long the app's own drain may take before the process is ended anyway.
   *
   * Generous, because what is being waited for is open sockets closing -- and a
   * party member mid-request is the thing this is protecting. The point is only
   * that it is finite: a drain that hangs must not leave a process a panel
   * cannot restart.
   */
  const SHUTDOWN_GRACE_MS = 10_000;

  let shuttingDown = false;

  /**
   * Stop the tunnel, then let the application drain -- in that order, and
   * without exiting here.
   *
   * The exit used to happen in this handler, and it was the reason the drain
   * never completed. `bootstrap` registers its own SIGTERM/SIGINT handler that
   * awaits `app.close()`, and both handlers fire on the same signal in
   * registration order -- but an `await` suspends, so this one ran to its
   * `process.exit(0)` in the same tick and the process was gone before a single
   * socket had been closed. The server looked like it shut down cleanly and had
   * in fact been killed mid-flight.
   *
   * So this only arms a deadline. The normal path is the app's own handler
   * exiting after a clean close, and the watchdog covers the case where it
   * cannot -- which is the one where waiting forever would be worse.
   */
  const onSignal = (signal) => {
    if (shuttingDown) return;
    shuttingDown = true;
    console.log(`[startup] ${signal} -- shutting down.`);
    stopTunnel();
    const watchdog = setTimeout(() => {
      console.warn(`[startup] the app did not finish closing within ${SHUTDOWN_GRACE_MS}ms; exiting.`);
      process.exit(1);
    }, SHUTDOWN_GRACE_MS);
    // Deliberately *not* unref'd. The happy path is the app's own handler
    // exiting 0 the moment `app.close()` resolves, so this never fires -- and
    // the case it exists for is precisely the one where nothing else is left to
    // keep the process alive. An unref'd timer would let the loop drain and the
    // process exit 0 on a drain that never happened, which is the failure this
    // is here to catch.
    void watchdog;
  };

  for (const signal of ['SIGINT', 'SIGTERM']) {
    process.on(signal, () => onSignal(signal));
  }
  process.on('exit', stopTunnel);
}

// Nothing below `main` should throw synchronously; a rejected promise here is a
// process that would otherwise exit 0 on a failure to boot.
main().catch((error) => {
  console.error('[startup] unhandled failure:', error);
  process.exit(1);
});
