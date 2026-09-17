#!/usr/bin/env node
/**
 * Velthy Listen Together — the one entry point.
 *
 * Everything about starting this server lives here, in order, so that "how does
 * it run" has exactly one answer:
 *
 *   1. environment  — read and validate before a port is bound, so a bad value
 *                     is a startup error rather than a mystery at 3am
 *   2. app          — build the Nest application (REST + WebSocket on one port)
 *   3. listen       — bind, and say so
 *   4. tunnel       — if TUNNEL_TOKEN is set, bring cloudflared up beside it
 *   5. shutdown     — on SIGTERM/SIGINT, stop accepting, drain, exit
 *
 * It is an `.mjs` file on purpose. Nest conventionally boots from `main.ts`,
 * which means two entry points once anything needs to run before the Nest
 * container exists (validation, a preflight log, a signal handler that is not
 * inside the app). This file is that single entry point instead, and it is the
 * same file in development and in production — `src/` is loaded through tsx when
 * the compiled `dist/` is not present, and `dist/` is preferred when it is.
 *
 * ## The tunnel runs from here, not from the panel's command line
 *
 * A control panel runs exactly one command, and the two halves of this service
 * must be alive at the same instant — a tunnel with no server behind it answers
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
import { spawn } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));

/**
 * Fold `.env` into the environment, without overwriting what is already set.
 *
 * The panel passes its variables in for real, so this only matters for a local
 * `node startup.mjs` — and it is done here rather than by pulling in `dotenv`
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
 * The module that actually builds the app, resolved against what has been built.
 *
 * `dist/` wins when it exists because that is the deployed artefact; a source
 * tree loaded through tsx is the development path. Resolving here — rather than
 * making `npm start` differ between the two — is what keeps this file identical
 * in both.
 */
async function loadBootstrap() {
  const compiled = join(here, 'dist', 'bootstrap.js');
  if (existsSync(compiled)) {
    return import(pathToFileURL(compiled).href);
  }
  // Development: compile the TypeScript on the fly.
  const { register } = await import('tsx/esm/api').catch(() => {
    throw new Error(
      'No dist/bootstrap.js and tsx is not installed.\n' +
        'Run `npm run build` for production, or `npm install` for development.',
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
 * shell, and a shell then re-parses the arguments — which breaks on any path
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
 * the port stayed unbound — harmless in production, and thoroughly confusing on
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
 * missing or cannot connect, this process keeps serving on its port — which is
 * still useful (a local client, a reverse proxy in front) and is the only
 * behaviour that lets somebody debug the tunnel without also losing the API.
 */
async function startTunnel(port) {
  const token = process.env.TUNNEL_TOKEN?.trim();
  if (!token) {
    console.log('[startup] TUNNEL_TOKEN not set — serving locally only.');
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
        '[startup] cloudflared not found — the server keeps running.\n' +
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
  for (const signal of ['SIGINT', 'SIGTERM']) {
    process.on(signal, () => {
      stopTunnel();
      process.exit(0);
    });
  }
  process.on('exit', stopTunnel);
}

// Nothing below `main` should throw synchronously; a rejected promise here is a
// process that would otherwise exit 0 on a failure to boot.
main().catch((error) => {
  console.error('[startup] unhandled failure:', error);
  process.exit(1);
});
