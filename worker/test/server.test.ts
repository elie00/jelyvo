/*
 * Adapted from BitChord's party server tests (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
import { env as rawEnv, exports } from "cloudflare:workers";
import { runDurableObjectAlarm, runInDurableObject as runInObject } from "cloudflare:test";
import { beforeEach, describe, expect, it } from "vitest";
import { CONFIG, type Env } from "../src/config";

const env = rawEnv as unknown as Env;
// Its generic signature is too deep for the compiler once the stub is the real class.
const runInDurableObject = runInObject as unknown as (
  stub: unknown,
  callback: (instance: any, state: DurableObjectState) => unknown,
) => Promise<any>;
const BASE = "https://jam.test";
let ipCounter = 0;
/** Every test comes from its own address, so the limits only bite where a test means them to. */
const newIp = () => `203.0.113.${++ipCounter}`;

const worker = () => (exports as unknown as { default: Fetcher }).default;
const call = (path: string, init?: RequestInit, ip = newIp()) => {
  const headers = new Headers(init?.headers);
  headers.set("CF-Connecting-IP", ip);
  return worker().fetch(`${BASE}${path}`, { ...init, headers });
};
const post = (path: string, body: unknown, ip?: string) =>
  call(path, { method: "POST", body: JSON.stringify(body) }, ip);

const person = (name: string) => ({ userId: `u_${name}`, deviceId: `d_${name}`, displayName: name });

interface Entered {
  code: string;
  token: string;
  you: { memberId: string; isHost: boolean };
  party: { members: unknown[]; maxMembers: number; playback: { autoplayEnabled: boolean } };
}

async function create(name = "host", extra: object = {}): Promise<Entered> {
  const response = await post("/api/parties", { ...person(name), ...extra });
  expect(response.status).toBe(201);
  return response.json();
}

async function enter(code: string, name: string): Promise<Entered> {
  const response = await post(`/api/parties/${code}/join`, person(name));
  expect(response.status).toBe(200);
  return response.json();
}

type Frame = Record<string, any>;

class Client {
  readonly frames: Frame[] = [];
  private waiters: (() => void)[] = [];
  closed: { code: number } | null = null;

  constructor(readonly socket: WebSocket) {
    socket.accept();
    socket.addEventListener("message", (event) => {
      this.frames.push(JSON.parse(event.data as string));
      this.waiters.splice(0).forEach((wake) => wake());
    });
    socket.addEventListener("close", (event) => {
      this.closed = { code: event.code };
      this.waiters.splice(0).forEach((wake) => wake());
    });
  }

  send(frame: Frame) {
    this.socket.send(JSON.stringify(frame));
  }

  /** Waits for the next frame of that type, taking it and everything before it off the list. */
  async next(type: string, timeoutMs = 3000): Promise<Frame> {
    const deadline = Date.now() + timeoutMs;
    for (;;) {
      const index = this.frames.findIndex((frame) => frame.type === type);
      if (index !== -1) return this.frames.splice(0, index + 1).at(-1)!;
      if (this.closed) throw new Error(`closed while waiting for ${type}`);
      const left = deadline - Date.now();
      if (left <= 0) throw new Error(`timed out waiting for ${type}: ${JSON.stringify(this.frames)}`);
      await new Promise<void>((resolve) => {
        const timer = setTimeout(resolve, Math.min(left, 100));
        this.waiters.push(() => {
          clearTimeout(timer);
          resolve();
        });
      });
    }
  }

  async waitClosed(timeoutMs = 3000): Promise<{ code: number }> {
    const deadline = Date.now() + timeoutMs;
    while (!this.closed && Date.now() < deadline) {
      await new Promise((resolve) => setTimeout(resolve, 20));
    }
    if (!this.closed) throw new Error("socket did not close");
    return this.closed;
  }
}

async function connect(code: string, token: string): Promise<Client> {
  const response = await call(`/ws/parties/${code}`, {
    headers: { Upgrade: "websocket", Authorization: `Bearer ${token}` },
  });
  expect(response.status).toBe(101);
  return new Client(response.webSocket!);
}

const roomOf = (code: string) => env.PARTY_ROOM.get(env.PARTY_ROOM.idFromName(code));
const budgetStub = () =>
  env.PARTY_BUDGET.get(env.PARTY_BUDGET.idFromName("global")) as unknown as DurableObjectStub;

// The budget is one object for the whole server, so each test starts it from a clean day.
beforeEach(async () => {
  await runInDurableObject(budgetStub(), async (instance: any) => {
    await instance.status();
    instance.data = { ...instance.data, requests: 0, rowsWritten: 0, created: 0, parties: {} };
    instance.createsByIp.clear();
  });
});

const budgetStatus = () =>
  runInDurableObject(budgetStub(), (instance: any) => instance.status() as Promise<{ liveParties: number }>);

/** Sets today's estimate directly, as a day of real use would have left it. */
async function spendBudget(requests: number) {
  await runInDurableObject(budgetStub(), async (instance: any) => {
    await instance.status();
    await instance.report({ requests, rowsWritten: 0 });
  });
}

describe("REST", () => {
  it("answers health and time, and 404s anything else", async () => {
    const health = await call("/healthz");
    expect(health.status).toBe(200);
    expect(await health.json()).toMatchObject({ ok: true });
    for (const path of ["/1324/healthz", "/1324"]) expect((await call(path)).status).toBe(404);
    const time = (await (await call("/api/time")).json()) as { serverMs: number };
    expect(typeof time.serverMs).toBe("number");
  });

  it("creates, joins and shows a party to members only", async () => {
    const host = await create();
    expect(host.code).toHaveLength(6);
    expect(host.you.isHost).toBe(true);

    const guest = await enter(host.code, "guest");
    expect(guest.party.members).toHaveLength(2);

    const withToken = await call(`/api/parties/${host.code}`, {
      headers: { Authorization: `Bearer ${host.token}` },
    });
    expect(withToken.status).toBe(200);
    expect((await call(`/api/parties/${host.code}`)).status).toBe(401);
    const wrong = await call(`/api/parties/${host.code}`, {
      headers: { Authorization: "Bearer nope" },
    });
    expect(wrong.status).toBe(401);
  });

  it("finds a party from a mistyped code", async () => {
    const host = await create();
    const typed = `${host.code.slice(0, 3)}-${host.code.slice(3)}`.toLowerCase();
    expect((await post(`/api/parties/${typed}/join`, person("guest"))).status).toBe(200);
  });

  it("carries the AutoPlay choice and party size from creation", async () => {
    const host = await create("host", { autoplayEnabled: true, maxMembers: 2 });
    expect(host.party.playback.autoplayEnabled).toBe(true);
    expect(host.party.maxMembers).toBe(2);
    await enter(host.code, "guest");
    const full = await post(`/api/parties/${host.code}/join`, person("third"));
    expect(full.status).toBe(409);
    expect(await full.json()).toMatchObject({ error: "party_full" });
  });

  it("refuses bad bodies and unknown codes", async () => {
    const malformed = await call("/api/parties", { method: "POST", body: "{nope" });
    expect(malformed.status).toBe(422);
    expect((await post("/api/parties", { userId: "only" })).status).toBe(422);
    const big = await post("/api/parties", { ...person("a"), pad: "x".repeat(CONFIG.requestMaxBytes) });
    expect(big.status).toBe(413);
    expect((await post("/api/parties/ZZZZZZ/join", person("a"))).status).toBe(404);
    expect((await post("/api/parties/%E0%A4%A/join", person("a"))).status).toBe(404);
  });

  it("previews who is in a party without a token", async () => {
    const host = await create("Alice");
    const response = await call(`/api/parties/${host.code}/preview`);
    expect(response.status).toBe(200);
    const text = await response.text();
    expect(text).toContain("Alice");
    expect(text).not.toContain(host.token);
  });

  it("gives up a slot on leaving", async () => {
    const host = await create();
    const guest = await enter(host.code, "guest");
    const left = await call(`/api/parties/${host.code}/leave`, {
      method: "POST",
      headers: { Authorization: `Bearer ${guest.token}` },
    });
    expect(left.status).toBe(200);
    const preview = (await (await call(`/api/parties/${host.code}/preview`)).json()) as {
      memberCount: number;
    };
    expect(preview.memberCount).toBe(1);
  });
});

describe("invite page", () => {
  it("speaks French", async () => {
    const host = await create();
    const page = await (await call(`/invite/${host.code}`)).text();
    expect(page).toContain('<html lang="fr">');
    expect(page).toContain("Rejoindre dans Jelyvo");
    expect(page).not.toMatch(/Join|listening|Party code|Download/);
    const gone = await (await call("/invite/ZZZZZZ")).text();
    expect(gone).toContain("Partie introuvable");
    expect(gone).not.toMatch(/Party|expired|Please/);
  });

  it("opens the app for a live party and escapes what it shows", async () => {
    const host = await create("<b>");
    const page = await call(`/invite/${host.code}`);
    expect(page.status).toBe(200);
    const html = await page.text();
    expect(html).toContain(`jelyvo://party/${host.code}?server=${encodeURIComponent(BASE)}`);
    expect(html).toContain("intent://party/");
  });

  it("explains a dead or malformed code", async () => {
    expect((await call("/invite/ZZZZZZ")).status).toBe(404);
    const bad = await call("/invite/%3Cscript%3Ealert");
    expect(bad.status).toBe(400);
    expect(await bad.text()).not.toContain("<script>alert");
  });
});

describe("sockets", () => {
  it("refuses a socket without the right token", async () => {
    const host = await create();
    const upgrade = { Upgrade: "websocket" };
    expect((await call(`/ws/parties/${host.code}`, { headers: upgrade })).status).toBe(401);
    const wrong = await call(`/ws/parties/${host.code}`, {
      headers: { ...upgrade, Authorization: "Bearer nope" },
    });
    expect(wrong.status).toBe(401);
    expect((await call(`/ws/parties/${host.code}`)).status).toBe(426);
  });

  it("accepts the token as a subprotocol, and never echoes it", async () => {
    const host = await create();
    const response = await call(`/ws/parties/${host.code}`, {
      headers: {
        Upgrade: "websocket",
        "Sec-WebSocket-Protocol": `jelyvo.v1, jelyvo.token.${host.token}`,
      },
    });
    expect(response.status).toBe(101);
    expect(response.headers.get("Sec-WebSocket-Protocol")).toBe("jelyvo.v1");
    new Client(response.webSocket!).socket.close();
  });

  it("refuses a browser from an origin it was not told about", async () => {
    const host = await create();
    const response = await call(`/ws/parties/${host.code}`, {
      headers: {
        Upgrade: "websocket",
        Authorization: `Bearer ${host.token}`,
        Origin: "https://evil.example",
      },
    });
    expect(response.status).toBe(403);
  });

  it("welcomes, answers pings and shares controls with everyone", async () => {
    const host = await create("host");
    const guest = await enter(host.code, "guest");
    const a = await connect(host.code, host.token);
    const welcome = await a.next("welcome");
    expect(welcome.you.memberId).toBe(host.you.memberId);
    expect(welcome.party.code).toBe(host.code);

    const b = await connect(host.code, guest.token);
    await b.next("welcome");

    a.send({ type: "ping", clientMs: 123456789 });
    const pong = await a.next("pong");
    expect(pong.clientMs).toBe(123456789);
    expect(typeof pong.serverMs).toBe("number");

    a.send({
      type: "control",
      action: "queueAdd",
      track: { videoId: "abc", title: "Song", artist: "Artist" },
    });
    // The queue comes before the state that points into it.
    const queue = await b.next("queue");
    expect(queue.queue.items).toHaveLength(1);
    expect(b.frames.findIndex((frame) => frame.type === "state")).toBeLessThanOrEqual(0);
    const state = await b.next("state");
    expect(state.playback.queueLength).toBe(1);
    expect(await b.next("activity")).toMatchObject({ by: "host", action: "queueAdd" });

    a.send({ type: "control", action: "play", positionMs: 1000 });
    const playing = await b.next("state");
    expect(playing.playback.isPlaying).toBe(true);
    expect(playing.playback.anchorMs).toBeGreaterThan(playing.serverMs);

    a.socket.close();
    b.socket.close();
  });

  it("tells a device what went wrong with a control", async () => {
    const host = await create();
    const a = await connect(host.code, host.token);
    a.send({ type: "control", action: "next" });
    expect(await a.next("error")).toMatchObject({ error: "end_of_queue" });
    a.socket.close();
  });

  it("ignores frames that are not JSON objects", async () => {
    const host = await create();
    const a = await connect(host.code, host.token);
    a.socket.send("not json");
    a.socket.send("42");
    a.send({ type: "ping", clientMs: 1 });
    expect(await a.next("pong")).toMatchObject({ clientMs: 1 });
    a.socket.close();
  });

  it("replaces a device's old socket when it reconnects", async () => {
    const host = await create();
    const first = await connect(host.code, host.token);
    await first.next("welcome");
    const second = await connect(host.code, host.token);
    const welcome = await second.next("welcome");
    expect(welcome.party.members[0].connected).toBe(true);
    await first.waitClosed();
    second.send({ type: "ping", clientMs: 7 });
    await second.next("pong");
    const snapshot = (await (
      await call(`/api/parties/${host.code}`, { headers: { Authorization: `Bearer ${host.token}` } })
    ).json()) as { members: { connected: boolean }[] };
    expect(snapshot.members[0]!.connected).toBe(true);
    second.socket.close();
  });

  it("removes a kicked listener with a reason", async () => {
    const host = await create("host");
    const guest = await enter(host.code, "guest");
    const a = await connect(host.code, host.token);
    const b = await connect(host.code, guest.token);
    await b.next("welcome");
    a.send({ type: "control", action: "kick", memberId: guest.you.memberId });
    expect(await b.next("bye")).toMatchObject({ reason: "kicked" });
    await b.waitClosed();
    const members = await a.next("members");
    expect(members.members.length).toBeGreaterThan(0);
    a.socket.close();
  });

  it("says goodbye to a device that leaves", async () => {
    const host = await create();
    const a = await connect(host.code, host.token);
    await a.next("welcome");
    await call(`/api/parties/${host.code}/leave`, {
      method: "POST",
      headers: { Authorization: `Bearer ${host.token}` },
    });
    expect(await a.next("bye")).toMatchObject({ reason: "left" });
    await a.waitClosed();
  });

  it("slows, then closes, a device that floods", async () => {
    const host = await create();
    const a = await connect(host.code, host.token);
    await a.next("welcome");
    for (let i = 0; i < CONFIG.frameBurst + CONFIG.strikesBeforeClose + 5; i++) {
      a.send({ type: "ping", clientMs: i });
    }
    await a.next("error");
    expect((await a.waitClosed()).code).toBe(1008);
  });
});

describe("alarms", () => {
  it("re-states the truth while playing", async () => {
    const host = await create();
    const a = await connect(host.code, host.token);
    await a.next("welcome");
    a.send({ type: "control", action: "play", positionMs: 0 });
    const first = await a.next("state");
    await a.next("activity");
    expect(await runDurableObjectAlarm(roomOf(host.code))).toBe(true);
    const beat = await a.next("state");
    expect(beat.playback.seq).toBe(first.playback.seq);
    a.socket.close();
  });

  it("stays quiet and slow while paused", async () => {
    const host = await create();
    const a = await connect(host.code, host.token);
    await a.next("welcome");
    await a.next("members");
    expect(await runDurableObjectAlarm(roomOf(host.code))).toBe(true);
    const next = await runInDurableObject(roomOf(host.code), (_instance, state) =>
      state.storage.getAlarm(),
    );
    expect(next! - Date.now()).toBeGreaterThan(CONFIG.heartbeatMs);
    expect(a.frames.some((frame) => frame.type === "state")).toBe(false);
    a.socket.close();
  });

  it("removes a member once the grace period is over and tells the rest", async () => {
    const host = await create("host");
    const guest = await enter(host.code, "guest");
    const a = await connect(host.code, host.token);
    await a.next("welcome");
    // The guest never connected, and joined longer ago than the grace period.
    await runInDurableObject(roomOf(host.code), async (instance: any) => {
      const member = instance.stored.party.members.find(
        (candidate: { memberId: string }) => candidate.memberId === guest.you.memberId,
      );
      member.lastSeenMs -= CONFIG.disconnectGraceMs + 1;
    });
    await a.next("members");
    await runDurableObjectAlarm(roomOf(host.code));
    const members = await a.next("members");
    expect(members.members).toHaveLength(1);
    a.socket.close();
  });

  it("ends an abandoned party, frees its place and stores nothing", async () => {
    const host = await create();
    const before = await budgetStatus();
    await runInDurableObject(roomOf(host.code), async (instance: any) => {
      instance.stored.party.emptySinceMs -= CONFIG.emptyPartyTtlMs + 1;
    });
    await runDurableObjectAlarm(roomOf(host.code));
    expect((await call(`/api/parties/${host.code}/preview`)).status).toBe(404);
    const stored = await runInDurableObject(roomOf(host.code), (_instance, state) =>
      state.storage.list(),
    );
    expect(stored.size).toBe(0);
    const after = await budgetStatus();
    expect(after.liveParties).toBe(before.liveParties - 1);
  });

  it("survives eviction: state comes back from storage", async () => {
    const host = await create();
    const a = await connect(host.code, host.token);
    await a.next("welcome");
    a.send({ type: "control", action: "seek", positionMs: 4242 });
    await a.next("state");
    const stored = await runInDurableObject(roomOf(host.code), (_instance, state) =>
      state.storage.get<{ party: { playback: { positionMs: number } } }>("party"),
    );
    expect(stored?.party.playback.positionMs).toBe(4242);
    a.socket.close();
  });

  it("keeps pings out of storage, so they spend no written rows", async () => {
    const host = await create();
    const a = await connect(host.code, host.token);
    await a.next("welcome");
    const writes = () =>
      runInDurableObject(roomOf(host.code), (_instance, state) =>
        state.storage.get<{ writes: number }>("party").then((stored) => stored!.writes),
      );
    const before = await writes();
    a.send({ type: "ping", clientMs: 1 });
    await a.next("pong");
    a.send({ type: "sync" });
    await a.next("state");
    expect(await writes()).toBe(before);
    a.socket.close();
  });
});

describe("free plan guards", () => {
  it("refuses a second party from one address within the minute's limit", async () => {
    const ip = newIp();
    const body = person("a");
    expect((await post("/api/parties", body, ip)).status).toBe(201);
    expect((await post("/api/parties", body, ip)).status).toBe(201);
    const third = await post("/api/parties", body, ip);
    expect(third.status).toBe(429);
    expect(await third.json()).toMatchObject({ error: "create_rate_limited" });
  });

  it("caps live parties", async () => {
    await runInDurableObject(budgetStub(), async (instance: any) => {
      await instance.status();
      for (let i = 0; i < CONFIG.maxParties; i++) instance.data.parties[`P${i}`] = Date.now() + 60_000;
    });
    const response = await post("/api/parties", person("a"));
    expect(response.status).toBe(503);
    expect(await response.json()).toMatchObject({ error: "server_full" });
  });

  it("caps parties per day", async () => {
    await runInDurableObject(budgetStub(), async (instance: any) => {
      await instance.status();
      instance.data.created = CONFIG.maxPartiesPerDay;
    });
    const response = await post("/api/parties", person("a"));
    expect(response.status).toBe(503);
    expect(await response.json()).toMatchObject({ error: "daily_budget_reached" });
  });

  it("stops new parties at 70% of the allowance", async () => {
    await spendBudget(69_000);
    expect((await post("/api/parties", person("a"))).status).toBe(201);
    await spendBudget(2_000);
    const refused = await post("/api/parties", person("a"));
    expect(refused.status).toBe(503);
    expect(await refused.json()).toMatchObject({ error: "daily_budget_reached" });
    const status = (await (await call("/api/budget")).json()) as { level: string; usedPercent: number };
    expect(status.level).toBe("noNewParties");
    expect(status.usedPercent).toBeGreaterThanOrEqual(70);
  });

  it("slows the heartbeat in saver mode", async () => {
    const host = await create();
    const a = await connect(host.code, host.token);
    await a.next("welcome");
    a.send({ type: "control", action: "play", positionMs: 0 });
    await a.next("activity");
    await spendBudget(55_000);
    await runInDurableObject(roomOf(host.code), async (instance: any) => {
      instance.stored.lastReportMs -= CONFIG.reportEveryMs;
    });
    await runDurableObjectAlarm(roomOf(host.code));
    const next = await runInDurableObject(roomOf(host.code), (_instance, state) =>
      state.storage.getAlarm(),
    );
    expect(next! - Date.now()).toBeGreaterThan(CONFIG.heartbeatMs);
    expect(next! - Date.now()).toBeLessThanOrEqual(CONFIG.saverHeartbeatMs);
    a.socket.close();
  });

  it("refuses new devices near the limit but lets one come back", async () => {
    const host = await create();
    await spendBudget(90_000);
    await runInDurableObject(roomOf(host.code), async (instance: any) => {
      instance.stored.lastReportMs -= CONFIG.reportEveryMs;
    });
    await runDurableObjectAlarm(roomOf(host.code));
    const refused = await post(`/api/parties/${host.code}/join`, person("new"));
    expect(refused.status).toBe(503);
    expect((await post(`/api/parties/${host.code}/join`, person("host"))).status).toBe(200);
  });

  it("ends parties with a clean goodbye just before the limit", async () => {
    const host = await create();
    const a = await connect(host.code, host.token);
    await a.next("welcome");
    await spendBudget(96_000);
    await runInDurableObject(roomOf(host.code), async (instance: any) => {
      instance.stored.lastReportMs -= CONFIG.reportEveryMs;
    });
    await runDurableObjectAlarm(roomOf(host.code));
    expect(await a.next("bye")).toMatchObject({ reason: "server_quota" });
    await a.waitClosed();
    expect((await call(`/api/parties/${host.code}/preview`)).status).toBe(404);
  });

  it("starts each UTC day afresh", async () => {
    await spendBudget(99_000);
    await runInDurableObject(budgetStub(), async (instance: any) => {
      instance.data.day = "2000-01-01";
    });
    const status = (await (await call("/api/budget")).json()) as { level: string; requests: number };
    expect(status.level).toBe("normal");
    expect(status.requests).toBe(0);
  });

  it("limits requests per address before anything reaches a Durable Object", async () => {
    const ip = newIp();
    const statuses: number[] = [];
    for (let i = 0; i <= CONFIG.requestsPerMinutePerIp; i++) {
      statuses.push((await call("/healthz", undefined, ip)).status);
    }
    expect(statuses.at(-1)).toBe(429);
    expect(statuses.slice(0, -1).every((status) => status === 200)).toBe(true);
  });
});
