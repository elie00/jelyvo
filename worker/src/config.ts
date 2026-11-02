/*
 * Adapted from BitChord's party server (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */

/** What Cloudflare's free plan allows per UTC day. Going over fails, it does not charge. */
export const FREE_PLAN = {
  requestsPerDay: 100_000,
  rowsWrittenPerDay: 100_000,
} as const;

export const CONFIG = {
  defaultMaxMembers: 5,
  minMembers: 2,
  maxMembers: 10,
  playLeadMs: 350,
  maxUpcomingQueue: 25,
  maxQueueLength: 26,

  disconnectGraceMs: 45_000,
  emptyPartyTtlMs: 120_000,
  partyMaxAgeMs: 8 * 60 * 60 * 1000,
  connectionIdleMs: 15 * 60 * 1000,

  // Every alarm is one request and one row written, so this is the main running cost.
  heartbeatMs: 10_000,
  saverHeartbeatMs: 30_000,
  sweepMs: 60_000,
  reportEveryMs: 5 * 60 * 1000,

  // Well above what the app sends: a burst of five pings, then one ping every 30 seconds.
  frameRatePerSecond: 5,
  frameBurst: 20,
  controlRatePerSecond: 2,
  controlBurst: 10,
  strikesBeforeClose: 20,

  maxParties: 10,
  maxPartiesPerDay: 100,
  createsPerMinutePerIp: 2,
  requestsPerMinutePerIp: 60,
  rateLimitMaxEntries: 10_000,

  requestMaxBytes: 16_384,
  socketMaxBytes: 16_384,
} as const;

export interface Env {
  PARTY_ROOM: DurableObjectNamespace<import("./room").PartyRoom>;
  PARTY_BUDGET: DurableObjectNamespace<import("./budget").PartyBudget>;
  /** Comma-separated browser origins. Native apps send no Origin and are always allowed. */
  ALLOWED_ORIGINS?: string;
  /** Used verbatim in invite links, for example https://jam.example.com. */
  PUBLIC_ORIGIN?: string;
}

export function isAllowedOrigin(env: Env, origin: string): boolean {
  return (env.ALLOWED_ORIGINS ?? "")
    .split(",")
    .map((entry) => entry.trim().replace(/\/+$/, ""))
    .some((entry) => entry !== "" && entry === origin.replace(/\/+$/, ""));
}
