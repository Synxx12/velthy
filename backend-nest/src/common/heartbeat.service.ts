/**
 * The heartbeat: re-states the truth on a timer, and sweeps what has gone quiet.
 *
 * A service of its own rather than a hook on the module. A module's constructor
 * cannot receive its own providers by argument position, and reaching for them
 * through `ModuleRef` from a lifecycle hook is a workaround for a problem a small
 * injectable does not have — it is simply given what it needs, the way every
 * other provider is.
 *
 * The unprompted re-broadcast is the backstop for everything the protocol cannot
 * notice: a frame that never arrived, a device whose clock offset has wandered, a
 * phone that came back from doze believing it is still where it was. None of
 * those announce themselves, so correctness cannot depend on anybody asking —
 * every few seconds each party is simply told again where it is, and each device
 * re-derives its playhead from that.
 */
import {
  Inject,
  Injectable,
  Logger,
  type OnApplicationBootstrap,
  type OnApplicationShutdown,
} from '@nestjs/common';

import { nowMs } from '../common/clock.js';
import { describeConfig } from '../common/config.js';
import { PartyService } from '../party/party.service.js';

@Injectable()
export class HeartbeatService implements OnApplicationBootstrap, OnApplicationShutdown {
  private readonly log = new Logger('Heartbeat');
  private timer: NodeJS.Timeout | null = null;

  /** Injected by explicit token; see `PartyService` for why not by type. */
  constructor(@Inject(PartyService) private readonly parties: PartyService) {}

  onApplicationBootstrap(): void {
    const config = this.parties.config;
    this.log.log(`config: ${describeConfig(config)}`);

    // unref so the ticker never keeps the process alive on its own; a shutting
    // down server should exit because nothing is left, not because a timer says
    // so.
    this.timer = setInterval(() => this.tick(), config.stateHeartbeatMs);
    this.timer.unref();
  }

  onApplicationShutdown(): void {
    if (this.timer) clearInterval(this.timer);
    this.timer = null;
  }

  private tick(): void {
    try {
      const now = nowMs();
      for (const party of this.parties.store.all()) {
        if (this.parties.hub.membersOnline(party.code).size > 0) {
          this.parties.hub.broadcast(party.code, this.parties.stateFrame(party));
        }
      }
      for (const party of this.parties.store.sweep(now)) {
        this.parties.hub.broadcast(party.code, this.parties.membersFrame(party));
      }
      // Sockets whose party the sweep has just deleted. Left attached they would
      // sit open forever receiving nothing, which on a phone is a radio kept
      // awake for a party that no longer exists.
      const live = new Set(this.parties.store.all().map((party) => party.code));
      for (const code of this.parties.hub.activeCodes()) {
        if (!live.has(code)) this.parties.hub.dropParty(code);
      }
    } catch (error) {
      // The ticker must outlive any one bad pass.
      this.log.warn(`heartbeat pass failed: ${(error as Error).message}`);
    }
  }
}
