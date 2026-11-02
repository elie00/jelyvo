/*
 * Adapted from BitChord's party server (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
import { DurableObject } from "cloudflare:workers";
import type { BudgetLevel, Usage } from "./budget";
import { CONFIG, type Env, isAllowedOrigin } from "./config";
import { activityFrame, applyControl } from "./control";
import {
  expiredMembers,
  isExpired,
  join,
  type JoinRequest,
  markConnected,
  type Member,
  memberToWire,
  membersFrame,
  newParty,
  type PartyData,
  PartyError,
  partyPreview,
  partyToWire,
  positionAt,
  queueFrame,
  removeMember,
  stateFrame,
} from "./party";

/** The part of a party worth surviving eviction, kept in a single row. */
interface Stored {
  party: PartyData;
  level: BudgetLevel;
  lastReportMs: number;
  /** Writes since the last report. Saved with the party, so counting them costs nothing. */
  writes: number;
}

/**
 * Kept on the socket, which survives hibernation without a storage write. That is why rate limits
 * and the frame count live here instead of on the member.
 */
interface SocketState {
  memberId: string;
  lastSeenMs: number;
  frames: number;
  frameTokens: number;
  frameTokensAtMs: number;
  controlTokens: number;
  controlTokensAtMs: number;
  strikes: number;
}

const SOCKET_PROTOCOL = "jelyvo.v1";
const SOCKET_TOKEN_PREFIX = "jelyvo.token.";
const STORAGE_KEY = "party";

export function socketToken(request: Request): string {
  const auth = request.headers.get("Authorization") ?? "";
  const [scheme, value] = auth.split(/\s+/, 2);
  if (scheme?.toLowerCase() === "bearer" && value) return value.trim();
  for (const offered of (request.headers.get("Sec-WebSocket-Protocol") ?? "").split(",")) {
    const protocol = offered.trim();
    if (protocol.startsWith(SOCKET_TOKEN_PREFIX)) return protocol.slice(SOCKET_TOKEN_PREFIX.length);
  }
  return "";
}

const json = (status: number, body: unknown) => Response.json(body, { status });

function fromError(error: PartyError): Response {
  return json(error.status, { error: error.code, message: error.message });
}

function tokensEqual(a: string, b: string): boolean {
  const encoder = new TextEncoder();
  const left = encoder.encode(a);
  const right = encoder.encode(b);
  return left.byteLength === right.byteLength && crypto.subtle.timingSafeEqual(left, right);
}

/** Refills a token bucket, then spends one token if it holds a whole one. */
function spend(tokens: number, sinceMs: number, ratePerSecond: number, burst: number) {
  const refilled = Math.min(burst, tokens + (Math.max(0, sinceMs) / 1000) * ratePerSecond);
  return refilled >= 1
    ? { allowed: true, tokens: refilled - 1 }
    : { allowed: false, tokens: refilled };
}

/** One party. It sleeps between messages, so a party nobody is touching costs next to nothing. */
export class PartyRoom extends DurableObject<Env> {
  private stored: Stored | null = null;
  /** Lost on eviction, which only makes the estimate a little low; these are rare and cheap. */
  private httpRequests = 0;
  /** The clock only moves forward, so nothing can rewrite an anchor under a room of phones. */
  private lastNowMs = 0;

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    void ctx.blockConcurrencyWhile(async () => {
      this.stored = (await ctx.storage.get<Stored>(STORAGE_KEY)) ?? null;
    });
  }

  private now(): number {
    this.lastNowMs = Math.max(this.lastNowMs, Date.now());
    return this.lastNowMs;
  }

  private async save(): Promise<void> {
    if (!this.stored) return;
    this.stored.writes++;
    await this.ctx.storage.put(STORAGE_KEY, this.stored);
  }

  private sockets(): WebSocket[] {
    return this.ctx.getWebSockets().filter((socket) => socket.readyState === WebSocket.OPEN);
  }

  private send(socket: WebSocket, frame: unknown): void {
    try {
      socket.send(JSON.stringify(frame));
    } catch {
      // The socket is on its way out; its close event does the bookkeeping.
    }
  }

  private broadcast(frame: unknown): void {
    const text = JSON.stringify(frame);
    for (const socket of this.sockets()) {
      try {
        socket.send(text);
      } catch {
        // As above.
      }
    }
  }

  private socketsOf(memberId: string): WebSocket[] {
    return this.ctx.getWebSockets(memberId);
  }

  private closeMember(memberId: string, code: number, reason: string, frame?: unknown): void {
    for (const socket of this.socketsOf(memberId)) {
      if (frame !== undefined) this.send(socket, frame);
      try {
        socket.close(code, reason);
      } catch {
        // Already closing.
      }
    }
  }

  private heartbeatEveryMs(stored: Stored): number {
    return stored.level === "normal" ? CONFIG.heartbeatMs : CONFIG.saverHeartbeatMs;
  }

  /** Alarms run only as often as there is something to do, because each one spends quota. */
  private nextAlarmDelayMs(stored: Stored): number {
    return stored.party.playback.isPlaying && this.sockets().length > 0
      ? this.heartbeatEveryMs(stored)
      : CONFIG.sweepMs;
  }

  private async wakeForHeartbeat(stored: Stored, now: number): Promise<void> {
    const wanted = now + this.nextAlarmDelayMs(stored);
    const current = await this.ctx.storage.getAlarm();
    if (current === null || current > wanted) await this.ctx.storage.setAlarm(wanted);
  }

  /** What this party has spent since it last said. Alarms are worked out from elapsed time. */
  private takeUsage(stored: Stored, now: number): Usage {
    const alarms = Math.ceil(Math.max(0, now - stored.lastReportMs) / this.nextAlarmDelayMs(stored));
    let frames = 0;
    for (const socket of this.ctx.getWebSockets()) {
      const state = socket.deserializeAttachment() as SocketState | null;
      if (!state) continue;
      frames += state.frames;
      state.frames = 0;
      socket.serializeAttachment(state);
    }
    const usage: Usage = {
      // Cloudflare counts incoming WebSocket messages at twenty to one.
      requests: alarms + Math.ceil(frames / 20) + this.httpRequests,
      // A put can touch an index row as well as the row itself, so each write counts double.
      rowsWritten: alarms + stored.writes * 2,
    };
    this.httpRequests = 0;
    stored.writes = 0;
    stored.lastReportMs = now;
    return usage;
  }

  private budget() {
    return this.env.PARTY_BUDGET.get(this.env.PARTY_BUDGET.idFromName("global"));
  }

  /** Ends the party for everyone and leaves nothing behind in storage. */
  private async end(stored: Stored, now: number, reason: string, message?: string): Promise<void> {
    const bye = { type: "bye", reason, ...(message ? { message } : {}) };
    for (const socket of this.ctx.getWebSockets()) {
      if (socket.readyState === WebSocket.OPEN) this.send(socket, bye);
      try {
        socket.close(1000, reason);
      } catch {
        // Already closing.
      }
    }
    const usage = this.takeUsage(stored, now);
    this.stored = null;
    await this.ctx.storage.deleteAlarm();
    await this.ctx.storage.deleteAll();
    await this.budget().release(stored.party.code, { ...usage, rowsWritten: usage.rowsWritten + 2 });
  }

  override async fetch(request: Request): Promise<Response> {
    this.httpRequests++;
    const url = new URL(request.url);
    const now = this.now();
    try {
      switch (url.pathname) {
        case "/create":
          return await this.create(request, now);
        case "/join":
          return await this.join(request, now);
        case "/snapshot":
          return await this.snapshot(request, now);
        case "/preview":
          return this.stored ? json(200, partyPreview(this.stored.party)) : this.noParty();
        case "/invite":
          return this.inviteDetails();
        case "/leave":
          return await this.leave(request, now);
        case "/ws":
          return await this.openSocket(request, now);
        default:
          return json(404, { error: "not_found", message: "Not found." });
      }
    } catch (error) {
      if (error instanceof PartyError) return fromError(error);
      throw error;
    }
  }

  private noParty(): Response {
    return json(404, { error: "no_such_party", message: "No party with that code." });
  }

  private async create(request: Request, now: number): Promise<Response> {
    if (this.stored) return json(409, { error: "code_taken", message: "That code is in use." });
    const body = (await request.json()) as JoinRequest & { code: string };
    const party = newParty(body.code, now, body.maxMembers);
    // Must be set here. Waiting for the first socket control can lose the setting.
    if (body.autoplayEnabled !== undefined) party.playback.autoplayEnabled = body.autoplayEnabled;
    const member = join(party, now, body);
    this.stored = { party, level: "normal", lastReportMs: now, writes: 0 };
    await this.save();
    await this.ctx.storage.setAlarm(now + CONFIG.sweepMs);
    return json(201, {
      code: party.code,
      token: member.token,
      you: memberToWire(member),
      party: partyToWire(party, now),
    });
  }

  private async join(request: Request, now: number): Promise<Response> {
    const stored = this.stored;
    if (!stored) return this.noParty();
    const body = (await request.json()) as JoinRequest;
    const isNewDevice = !stored.party.members.some((member) => member.deviceId === body.deviceId);
    if (isNewDevice && (stored.level === "noNewMembers" || stored.level === "stop")) {
      return json(503, {
        error: "daily_budget_reached",
        message: "The party server has used most of its daily allowance. Please try again after midnight UTC.",
      });
    }
    const member = join(stored.party, now, body);
    await this.save();
    this.broadcast(membersFrame(stored.party, now));
    return json(200, {
      code: stored.party.code,
      token: member.token,
      you: memberToWire(member),
      party: partyToWire(stored.party, now),
    });
  }

  private authenticate(party: PartyData, token: string): Member {
    const member = party.members.find((candidate) => tokensEqual(candidate.token, token));
    if (!member) throw new PartyError(401, "bad_token", "This device is not a member of that party.");
    return member;
  }

  private async snapshot(request: Request, now: number): Promise<Response> {
    if (!this.stored) return this.noParty();
    this.authenticate(this.stored.party, socketToken(request));
    return json(200, partyToWire(this.stored.party, now));
  }

  private inviteDetails(): Response {
    if (!this.stored) return this.noParty();
    const { party } = this.stored;
    return json(200, {
      memberCount: party.members.length,
      title: party.playback.track?.title ?? "",
      artist: party.playback.track?.artist ?? "",
    });
  }

  private async leave(request: Request, now: number): Promise<Response> {
    const stored = this.stored;
    if (!stored) return this.noParty();
    const member = this.authenticate(stored.party, socketToken(request));
    removeMember(stored.party, now, member.memberId);
    await this.save();
    this.closeMember(member.memberId, 1000, "left", { type: "bye", reason: "left" });
    this.broadcast(membersFrame(stored.party, now));
    return json(200, { ok: true });
  }

  private async openSocket(request: Request, now: number): Promise<Response> {
    const stored = this.stored;
    if (!stored) return new Response("no such party", { status: 404 });
    const origin = request.headers.get("Origin");
    if (origin && !isAllowedOrigin(this.env, origin)) {
      return new Response("origin not allowed", { status: 403 });
    }
    const token = socketToken(request);
    const member = stored.party.members.find((candidate) => tokensEqual(candidate.token, token));
    if (!member) return new Response("bad token", { status: 401 });

    // A device has one socket: the newest one, so a reconnect replaces whatever it left behind.
    this.closeMember(member.memberId, 1000, "replaced");

    const pair = new WebSocketPair();
    const client = pair[0];
    const server = pair[1];
    this.ctx.acceptWebSocket(server, [member.memberId]);
    const state: SocketState = {
      memberId: member.memberId,
      lastSeenMs: now,
      frames: 0,
      frameTokens: CONFIG.frameBurst,
      frameTokensAtMs: now,
      controlTokens: CONFIG.controlBurst,
      controlTokensAtMs: now,
      strikes: 0,
    };
    server.serializeAttachment(state);

    markConnected(stored.party, now, member, true);
    await this.save();
    await this.wakeForHeartbeat(stored, now);

    this.send(server, {
      type: "welcome",
      you: memberToWire(member),
      party: partyToWire(stored.party, now),
      serverMs: now,
    });
    this.broadcast(membersFrame(stored.party, now));

    const offered = (request.headers.get("Sec-WebSocket-Protocol") ?? "").split(",").map((p) => p.trim());
    const headers = offered.includes(SOCKET_PROTOCOL)
      ? { "Sec-WebSocket-Protocol": SOCKET_PROTOCOL }
      : undefined;
    return new Response(null, { status: 101, webSocket: client, headers });
  }

  override async webSocketMessage(socket: WebSocket, message: string | ArrayBuffer): Promise<void> {
    const stored = this.stored;
    const state = socket.deserializeAttachment() as SocketState | null;
    if (!stored || !state) {
      socket.close(1000, "party over");
      return;
    }
    const member = stored.party.members.find((candidate) => candidate.memberId === state.memberId);
    if (!member) {
      socket.close(1000, "not a member");
      return;
    }
    const now = this.now();
    state.frames++;
    state.lastSeenMs = now;
    member.lastSeenMs = now;

    const frameBudget = spend(
      state.frameTokens,
      now - state.frameTokensAtMs,
      CONFIG.frameRatePerSecond,
      CONFIG.frameBurst,
    );
    state.frameTokensAtMs = now;
    state.frameTokens = frameBudget.tokens;
    if (!frameBudget.allowed) {
      state.strikes++;
      socket.serializeAttachment(state);
      // Every message is spent the moment it arrives, so a device that will not slow down has to go.
      if (state.strikes >= CONFIG.strikesBeforeClose) {
        socket.close(1008, "too many messages");
      } else {
        this.send(socket, { type: "error", error: "rate_limited", message: "Too many messages at once." });
      }
      return;
    }

    const size = typeof message === "string" ? message.length : message.byteLength;
    if (typeof message !== "string" || size > CONFIG.socketMaxBytes) {
      socket.serializeAttachment(state);
      socket.close(1009, "message too large");
      return;
    }

    let frame: Record<string, unknown>;
    try {
      const parsed: unknown = JSON.parse(message);
      if (typeof parsed !== "object" || parsed === null) throw new Error("not an object");
      frame = parsed as Record<string, unknown>;
    } catch {
      socket.serializeAttachment(state);
      return;
    }

    switch (frame.type) {
      case "ping":
        this.send(socket, { type: "pong", clientMs: frame.clientMs, serverMs: now });
        break;
      case "sync":
        this.send(socket, stateFrame(stored.party, now));
        break;
      case "syncQueue":
        this.send(socket, queueFrame(stored.party, now));
        break;
      case "report":
        if (typeof frame.positionMs === "number" && stored.party.playback.isPlaying) {
          const drift = frame.positionMs - positionAt(stored.party.playback, now);
          if (Math.abs(drift) > 1500) {
            console.log(`party ${stored.party.code}: ${state.memberId} drifted ${Math.trunc(drift)}ms`);
          }
        }
        break;
      case "control":
        await this.control(socket, stored, member, state, frame, now);
        break;
    }
    socket.serializeAttachment(state);
  }

  private async control(
    socket: WebSocket,
    stored: Stored,
    member: Member,
    state: SocketState,
    frame: Record<string, unknown>,
    now: number,
  ): Promise<void> {
    const controlBudget = spend(
      state.controlTokens,
      now - state.controlTokensAtMs,
      CONFIG.controlRatePerSecond,
      CONFIG.controlBurst,
    );
    state.controlTokensAtMs = now;
    state.controlTokens = controlBudget.tokens;
    if (!controlBudget.allowed) {
      this.send(socket, { type: "error", error: "rate_limited", message: "Too many controls at once." });
      return;
    }

    const party = stored.party;
    const queueBefore = party.playback.queueSeq;
    const action = typeof frame.action === "string" ? frame.action : "";
    const result = applyControl(party, now, member, action, frame);
    if (!result.ok) {
      this.send(socket, { type: "error", error: result.error, message: result.message });
      return;
    }
    party.touchedAtMs = now;
    await this.save();

    if (result.kickedMemberId) {
      this.closeMember(result.kickedMemberId, 1000, "kicked", {
        type: "bye",
        reason: "kicked",
        message: "The host removed you from this party.",
      });
    }
    // The queue goes first, so nobody holds a state pointing into a list they have not been given.
    if (party.playback.queueSeq !== queueBefore) this.broadcast(queueFrame(party, now));
    this.broadcast(stateFrame(party, now));
    if (action === "kick" || action === "setMaxMembers" || action === "setHostOnlyControl") {
      this.broadcast(membersFrame(party, now));
    }
    this.broadcast(activityFrame(member, action, frame, now));
    if (party.playback.isPlaying) await this.wakeForHeartbeat(stored, now);
  }

  override async webSocketClose(socket: WebSocket): Promise<void> {
    await this.socketGone(socket);
  }

  override async webSocketError(socket: WebSocket): Promise<void> {
    await this.socketGone(socket);
  }

  private async socketGone(socket: WebSocket): Promise<void> {
    try {
      socket.close(1000, "closing");
    } catch {
      // Already closed.
    }
    const stored = this.stored;
    const state = socket.deserializeAttachment() as SocketState | null;
    if (!stored || !state) return;
    const member = stored.party.members.find((candidate) => candidate.memberId === state.memberId);
    if (!member) return;
    // A device that reconnected has a newer socket, and is still here.
    const stillHere = this.socketsOf(member.memberId).some(
      (other) => other !== socket && other.readyState === WebSocket.OPEN,
    );
    if (stillHere) return;
    const now = this.now();
    markConnected(stored.party, now, member, false);
    await this.save();
    this.broadcast(membersFrame(stored.party, now));
  }

  override async alarm(): Promise<void> {
    const stored = this.stored;
    if (!stored) return;
    const now = this.now();
    const party = stored.party;

    this.closeIdleSockets(now);
    const reconciled = this.reconcileConnections(stored, now);

    const gone = expiredMembers(party, now);
    for (const member of gone) removeMember(party, now, member.memberId);

    if (isExpired(party, now)) {
      await this.end(stored, now, "expired");
      return;
    }

    if (now - stored.lastReportMs >= CONFIG.reportEveryMs) {
      stored.level = await this.budget().report(this.takeUsage(stored, now));
      if (stored.level === "stop") {
        await this.end(
          stored,
          now,
          "server_quota",
          "The party server has reached its daily allowance. Please start again after midnight UTC.",
        );
        return;
      }
      await this.save();
    } else if (reconciled || gone.length > 0) {
      await this.save();
    }

    if (reconciled || gone.length > 0) this.broadcast(membersFrame(party, now));
    // The truth is re-stated on a timer: a lost frame or a phone back from doze announces nothing.
    if (party.playback.isPlaying) this.broadcast(stateFrame(party, now));

    await this.ctx.storage.setAlarm(now + this.nextAlarmDelayMs(stored));
  }

  private closeIdleSockets(now: number): void {
    for (const socket of this.sockets()) {
      const state = socket.deserializeAttachment() as SocketState | null;
      if (state && now - state.lastSeenMs > CONFIG.connectionIdleMs) {
        socket.close(1001, "idle");
      }
    }
  }

  /**
   * Puts the connected flags right if a close event was missed while the object was away. Returns
   * whether it did, because the fix must be saved: the next wake would otherwise find the old flag
   * again and restart that member's grace period.
   */
  private reconcileConnections(stored: Stored, now: number): boolean {
    let changed = false;
    for (const member of stored.party.members) {
      const online = this.socketsOf(member.memberId).some((s) => s.readyState === WebSocket.OPEN);
      if (member.connected !== online) {
        markConnected(stored.party, now, member, online);
        changed = true;
      }
    }
    return changed;
  }
}
