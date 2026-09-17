/**
 * Who is currently holding a socket, and how a frame reaches all of them.
 *
 * Kept apart from the domain so the party has no idea sockets exist: the party
 * decides *what* is true, this decides *who hears it*. One member has at most
 * one live socket — a second connection for the same membership replaces the
 * first, which is what makes a reconnect after a flaky handover land cleanly
 * instead of leaving a zombie receiving frames nobody reads.
 */
import type { WebSocket } from 'ws';

import { Logger } from '@nestjs/common';

interface Held {
  socket: WebSocket;
  alive: boolean;
}

export class Hub {
  private readonly log = new Logger('Hub');
  private readonly rooms = new Map<string, Map<string, Held>>();

  /** Binds a socket to a member, replacing and closing any socket they held. */
  attach(code: string, memberId: string, socket: WebSocket): void {
    let room = this.rooms.get(code);
    if (!room) {
      room = new Map();
      this.rooms.set(code, room);
    }
    const previous = room.get(memberId);
    room.set(memberId, { socket, alive: true });
    if (previous && previous.socket !== socket) {
      // Best effort: a peer that has already gone away cannot be told so.
      try {
        previous.socket.close();
      } catch {
        /* already gone */
      }
    }
  }

  /**
   * Removes a member's socket, but only if it is still *this* one. A reconnect
   * that already replaced it must not be torn down by the losing connection's
   * own cleanup running a moment later.
   */
  detach(code: string, memberId: string, socket: WebSocket): void {
    const room = this.rooms.get(code);
    if (!room) return;
    const held = room.get(memberId);
    if (held && held.socket === socket) room.delete(memberId);
    if (room.size === 0) this.rooms.delete(code);
  }

  /** Closes every socket in a party and forgets the room. */
  dropParty(code: string): void {
    const room = this.rooms.get(code);
    this.rooms.delete(code);
    if (!room) return;
    for (const held of room.values()) {
      try {
        held.socket.close();
      } catch {
        /* already gone */
      }
    }
  }

  /** The member ids currently holding a socket. */
  membersOnline(code: string): Set<string> {
    return new Set(this.rooms.get(code)?.keys() ?? []);
  }

  /** Every code with at least one socket. */
  activeCodes(): Set<string> {
    return new Set(this.rooms.keys());
  }

  /** Delivers a frame to one member, doing nothing when they hold no socket. */
  send(code: string, memberId: string, payload: unknown): void {
    const held = this.rooms.get(code)?.get(memberId);
    if (!held) return;
    this.writeQuietly(held.socket, payload);
  }

  /**
   * Delivers a frame to every socket in a party, optionally skipping one member.
   *
   * Sends are issued together and settled together, so one member on a slow link
   * does not delay the frame for everyone else — which in this feature is not a
   * latency nicety but the thing being synchronised.
   */
  broadcast(code: string, payload: unknown, skip?: string): void {
    const room = this.rooms.get(code);
    if (!room) return;
    const data = JSON.stringify(payload);
    for (const [memberId, held] of room) {
      if (memberId === skip) continue;
      this.writeQuietly(held.socket, data, true);
    }
  }

  /**
   * Writes a frame, swallowing the failure a dead peer produces. A closed socket
   * is routine here, not exceptional: phones drop connections constantly, and a
   * throw out of a broadcast would take the whole party's frame with it.
   */
  private writeQuietly(socket: WebSocket, payload: unknown, preSerialised = false): void {
    try {
      if (socket.readyState !== socket.OPEN) return;
      socket.send(preSerialised ? (payload as string) : JSON.stringify(payload));
    } catch (error) {
      this.log.debug(`dropped a frame to a closed socket: ${(error as Error).message}`);
    }
  }
}
