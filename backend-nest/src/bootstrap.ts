/**
 * Builds and runs the Nest application.
 *
 * Split from `startup.mjs` so the entry point stays about *process* concerns
 * (environment, signals, exit codes) and this stays about the *app*: one HTTP
 * server that also answers the WebSocket upgrade, and a shutdown that lets
 * in-flight requests finish.
 *
 * The REST and WebSocket halves share a single port on purpose. A party is a
 * code and the devices holding it; splitting them across two ports would mean
 * two things to proxy, two things to keep in step, and a client that has to
 * learn which is which.
 *
 * The HTTP server is created here rather than by Nest so that the socket server
 * can be bound to the *same* listener. Nest would otherwise make its own, and a
 * second `http.createServer()` on the same port is the classic "why is my
 * WebSocket never reached" bug.
 */
import 'reflect-metadata';

import { Logger } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import { ExpressAdapter } from '@nestjs/platform-express';
import type { Server as HttpServer } from 'node:http';
import express from 'express';

import { AppModule } from './app.module.js';
import { BUILD, PROTOCOL } from './common/build.js';
import { loadConfig } from './common/config.js';
import { PartySocketServer } from './party/party.socket.js';

const logger = new Logger('Bootstrap');

export async function bootstrap(): Promise<void> {
  // Said first, before anything can fail, so the log always answers "which
  // build is this?" — including for a boot that goes wrong afterwards.
  logger.log(
    `build ${BUILD.version}${BUILD.commit ? ` (${BUILD.commit.slice(0, 7)})` : ''} · protocol ${PROTOCOL} · ${BUILD.features.join(', ')}`,
  );

  // Read before the container exists, so a bad value is a startup error with no
  // half-built app left behind.
  const config = loadConfig();

  // One listener for both halves. Nest builds its router onto this Express
  // instance; the socket server is attached to the HTTP server Nest actually
  // listens on, taken from the app once it exists, rather than one created here
  // that nothing would ever bind. Creating a second server on the same port is
  // the classic "why is my WebSocket never reached" bug.
  const expressApp = express();

  const app = await NestFactory.create(AppModule, new ExpressAdapter(expressApp), {
    logger: ['error', 'warn', 'log'],
    cors: config.allowedOrigins.length > 0 ? { origin: config.allowedOrigins } : false,
  });

  // The socket server attaches its own `upgrade` handler to the same server Nest
  // is about to listen on.
  app.get(PartySocketServer).attachTo(app.getHttpServer() as HttpServer);

  // No global validation pipe on purpose. The protocol is validated by hand in
  // the join parser and the socket server, which is where the exact messages the
  // client expects live; a decorator-driven pipe would be a second, differently
  // worded refusal for the same request, and it would pull a validation library
  // in for a body of four strings.

  // A SIGTERM from the platform, or `process.exit`, must let in-flight requests
  // finish rather than cutting a frame mid-broadcast.
  app.enableShutdownHooks();

  await app.listen(config.port, '0.0.0.0');

  logger.log(`listening on ${config.bindAddress}`);
  logger.log(`  http  http://<host>:${config.port}/api/time`);
  logger.log(`  ws    ws://<host>:${config.port}/ws/parties/{code}?token=...`);

  const shutdown = async (signal: string): Promise<void> => {
    logger.log(`${signal} received, shutting down`);
    try {
      await app.close();
      logger.log('shutdown complete');
      process.exit(0);
    } catch (error) {
      logger.error(`shutdown failed: ${(error as Error).message}`);
      process.exit(1);
    }
  };

  process.once('SIGTERM', () => void shutdown('SIGTERM'));
  process.once('SIGINT', () => void shutdown('SIGINT'));
}
