/**
 * The application module: wiring only.
 *
 * The heartbeat and the socket server are providers, not hooks on this class —
 * see `HeartbeatService` for why. This file's whole job is to say what exists and
 * how the pieces are built, so that reading it answers "what is in this app"
 * without any of the pieces having to be read first.
 *
 * Every provider is wired by explicit token rather than by parameter type. Type
 * injection depends on `emitDecoratorMetadata`, which only exists when TypeScript
 * compiled the file, so it would work in the built image and fail on the
 * source-loaded development path. Tokens behave the same in both.
 */
import { Module } from '@nestjs/common';
import { APP_FILTER } from '@nestjs/core';

import { CONFIG, loadConfig, type Config } from './common/config.js';
import { AllExceptionsFilter } from './common/all-exceptions.filter.js';
import { HeartbeatService } from './common/heartbeat.service.js';
import { AppController } from './app.controller.js';
import { PartyService } from './party/party.service.js';
import { PartySocketServer } from './party/party.socket.js';

@Module({
  controllers: [AppController],
  providers: [
    { provide: APP_FILTER, useClass: AllExceptionsFilter },

    // Read once, at module construction, and injected everywhere as a plain
    // value — so nothing can re-read the environment under a running server.
    {
      provide: CONFIG,
      useFactory: (): Config => loadConfig(),
    },
    {
      provide: PartyService,
      useFactory: (config: Config) => new PartyService(config),
      inject: [CONFIG],
    },
    HeartbeatService,
    PartySocketServer,
  ],
  exports: [PartyService, PartySocketServer],
})
export class AppModule {}
