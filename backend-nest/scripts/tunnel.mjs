#!/usr/bin/env node
/**
 * Run a Cloudflare tunnel for the party server, using a token from `.env`.
 *
 * Wraps `cloudflared` so the token never has to be pasted on the command line —
 * which puts it in shell history and in `ps` for anyone else on the box to read.
 * The value comes from the environment, loaded from `.env` if it is not already
 * set, and is passed to the child through `TUNNEL_TOKEN`, the variable
 * cloudflared itself reads for a remotely-managed tunnel.
 *
 * The tunnel still needs its public hostname configured once, in the dashboard:
 *
 *   Zero Trust -> Networks -> Tunnels -> (this tunnel) -> Public Hostname
 *     -> Add:  party.example.com  ->  HTTP  ->  localhost:8080
 *
 * Nothing in this file sets that up, and nothing can: a token tunnel is
 * configured remotely by design, which is the reason it needs no config file.
 *
 * Usage:
 *   node scripts/tunnel.mjs              # from .env / the environment
 *   node scripts/tunnel.mjs -- <args>    # extra args go to cloudflared
 */
import { spawn } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');

/** Load `.env` into the current process, without overwriting what is already set. */
function loadEnvFile() {
  const path = join(root, '.env');
  if (!existsSync(path)) return;
  for (const rawLine of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const eq = line.indexOf('=');
    if (eq === -1) continue;
    const key = line.slice(0, eq).trim();
    // Quotes around a value are for the file's own readability; strip one pair.
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
 * The cloudflared binary to run.
 *
 * The npm package ships the real executable at `node_modules/cloudflared/bin/`,
 * and that is preferred over the `.bin/cloudflared.cmd` shim: a shim is a batch
 * script, batch scripts are not spawnable without a shell, and a shell then
 * re-parses the arguments — which breaks on any path containing a space
 * (`D:\Vs code\…`) and warns about injection besides. Running the executable
 * directly avoids all of it, on every platform.
 *
 * The name on PATH is the last resort, for a system-wide install.
 */
function resolveBinary() {
  const isWindows = process.platform === 'win32';
  const bundled = join(
    root,
    'node_modules',
    'cloudflared',
    'bin',
    isWindows ? 'cloudflared.exe' : 'cloudflared',
  );
  if (existsSync(bundled)) return bundled;

  // A system install. On Windows, `where` finds either an .exe or a .cmd; the
  // global npm prefix installs the former alongside the shim.
  return isWindows ? 'cloudflared.exe' : 'cloudflared';
}

function main() {
  loadEnvFile();

  const token = process.env.TUNNEL_TOKEN?.trim();
  if (!token) {
    console.error(
      [
        'No TUNNEL_TOKEN.',
        '',
        'Set it in backend-nest/.env (see .env.example), or export it:',
        '  export TUNNEL_TOKEN=eyJhIjoi...',
        '',
        'Get the token from Cloudflare Zero Trust:',
        '  Networks -> Tunnels -> Create a tunnel -> Cloudflared -> copy the token',
      ].join('\n'),
    );
    process.exit(1);
  }

  // Anything after `--` is forwarded, so `-- --loglevel debug` still works.
  const passthrough = process.argv.slice(2).filter((arg) => arg !== '--');

  const args = [
    'tunnel',
    // The service is managed here; letting it update itself mid-run would
    // restart it under a party that is listening.
    '--no-autoupdate',
    'run',
    ...passthrough,
  ];

  const binary = resolveBinary();

  const child = spawn(binary, args, {
    stdio: 'inherit',
    // Not passed as an argv entry: an argument is visible in `ps` to every user
    // on the machine, and this one is the tunnel's credential.
    env: { ...process.env, TUNNEL_TOKEN: token },
  });

  child.on('error', (error) => {
    if (error.code === 'ENOENT') {
      console.error(
        [
          'cloudflared not found.',
          '',
          'Install it with npm:',
          '  npm install --save-dev cloudflared',
          '',
          'On a server where npm cannot fetch the binary, use the package instead:',
          '  sudo apt-get install -y cloudflared',
        ].join('\n'),
      );
      process.exit(1);
    }
    console.error('failed to start cloudflared:', error.message);
    process.exit(1);
  });

  child.on('exit', (code, signal) => {
    process.exit(code ?? (signal ? 1 : 0));
  });

  for (const signal of ['SIGINT', 'SIGTERM']) {
    process.on(signal, () => child.kill(signal));
  }
}

main();
