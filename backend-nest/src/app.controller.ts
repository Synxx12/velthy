/**
 * The REST half of the service: the paperwork — mint a code, join one, read it,
 * leave. The WebSocket half is the feature.
 *
 * Deliberately thin. Every handler maps a request to one service call and lets
 * the service's `PartyError` become the response; nothing here decides anything
 * about parties.
 */
import {
  Body,
  Controller,
  Get,
  Headers,
  HttpCode,
  HttpStatus,
  Inject,
  Param,
  Post,
} from '@nestjs/common';

import { nowMs } from './common/clock.js';
import { parseJoinRequest } from './common/protocol.js';
import { isWellFormedCode, PartyError } from './party/party.js';
import { PartyService } from './party/party.service.js';

@Controller()
export class AppController {
  /** Injected by explicit token; see `PartyService` for why not by type. */
  constructor(@Inject(PartyService) private readonly parties: PartyService) {}

  /** Service banner: what this is, and how many parties it is holding. */
  @Get()
  root(): Record<string, unknown> {
    return {
      service: 'velthy-listen-together',
      maxMembers: this.parties.config.maxMembers,
      parties: this.parties.store.size,
      serverMs: nowMs(),
    };
  }

  @Get('healthz')
  health(): Record<string, unknown> {
    return { ok: true, serverMs: nowMs() };
  }

  /**
   * One reading of the server clock, for a client that has no socket yet.
   *
   * The client wants the round trip as much as the number: with `t0` and `t1`
   * taken either side of this call, the offset is `serverMs - (t0 + t1) / 2` and
   * the error is bounded by half the round trip. Repeating it and keeping the
   * sample with the smallest round trip is the same trick NTP uses, and is
   * enough to put two phones on the same millisecond-ish timeline over mobile
   * data. The socket's own ping/pong does exactly this, continuously.
   */
  @Get('api/time')
  time(): Record<string, unknown> {
    return { serverMs: nowMs() };
  }

  /** Mint a code and put the caller in it as host. */
  @Post('api/parties')
  @HttpCode(HttpStatus.CREATED)
  create(@Body() body: unknown): Record<string, unknown> {
    return this.parties.create(parseJoinRequest(body));
  }

  /** Join an existing party. */
  @Post('api/parties/:code/join')
  join(@Param('code') code: string, @Body() body: unknown): Record<string, unknown> {
    if (!isWellFormedCode(code)) {
      throw new PartyError(400, 'bad_code', 'A party code is six letters or digits.');
    }
    return this.parties.join(code, parseJoinRequest(body));
  }

  /**
   * Who is in a party, to somebody who has not joined it.
   *
   * Unauthenticated, and safe for the same reason the preview body is small:
   * everything it returns is what the holder of the code would learn by joining.
   * It is what lets an invite be looked at — a face, a name, how full it is —
   * before a device slot is committed.
   */
  @Get('api/parties/:code/preview')
  preview(@Param('code') code: string): Record<string, unknown> {
    if (!isWellFormedCode(code)) {
      throw new PartyError(400, 'bad_code', 'A party code is six letters or digits.');
    }
    return this.parties.preview(code);
  }

  /** The full snapshot, for a device holding a token. */
  @Get('api/parties/:code')
  read(
    @Param('code') code: string,
    @Headers('authorization') authorization: string | undefined,
  ): Record<string, unknown> {
    const { party, member } = this.parties.authenticate(code, authorization);
    return this.parties.read(party.code, member);
  }

  /** Leave a party. */
  @Post('api/parties/:code/leave')
  @HttpCode(HttpStatus.OK)
  leave(
    @Param('code') code: string,
    @Headers('authorization') authorization: string | undefined,
  ): Record<string, unknown> {
    const { party, member } = this.parties.authenticate(code, authorization);
    return this.parties.leave(party.code, member);
  }
}
