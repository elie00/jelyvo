import { DurableObject } from "cloudflare:workers";
import { CONFIG, type Env, FREE_PLAN } from "./config";

/**
 * What the server still allows, from what it estimates it has spent today.
 *
 * The free plan never charges: past its limits, operations fail until 00:00 UTC. These levels exist
 * so that people lose as little as possible on the way there. A party in progress outlives new
 * ones, and it ends with a clean goodbye rather than with errors.
 */
export type BudgetLevel = "normal" | "saver" | "noNewParties" | "noNewMembers" | "stop";

export function levelFor(usedShare: number): BudgetLevel {
  if (usedShare >= 0.95) return "stop";
  if (usedShare >= 0.85) return "noNewMembers";
  if (usedShare >= 0.7) return "noNewParties";
  if (usedShare >= 0.5) return "saver";
  return "normal";
}

export interface Usage {
  requests: number;
  rowsWritten: number;
}

interface BudgetData extends Usage {
  day: string;
  created: number;
  /** Live parties and when each must be over, so one that never says goodbye still drops out. */
  parties: Record<string, number>;
}

export type Admission =
  | { ok: true }
  | { ok: false; status: number; error: string; message: string };

const utcDay = (now: number) => new Date(now).toISOString().slice(0, 10);

/** The one object that sees the whole server. Only rare events reach it, so it stays cheap. */
export class PartyBudget extends DurableObject<Env> {
  private data!: BudgetData;
  /** Per-minute counts need no durability, and writing them would spend what they protect. */
  private createsByIp = new Map<string, { startedMs: number; count: number }>();

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    void ctx.blockConcurrencyWhile(async () => {
      this.data = (await ctx.storage.get<BudgetData>("budget")) ?? {
        day: utcDay(Date.now()),
        requests: 0,
        rowsWritten: 0,
        created: 0,
        parties: {},
      };
    });
  }

  private refresh(now: number): void {
    const today = utcDay(now);
    if (this.data.day !== today) {
      this.data = { ...this.data, day: today, requests: 0, rowsWritten: 0, created: 0 };
    }
    for (const [code, expiresAtMs] of Object.entries(this.data.parties)) {
      if (expiresAtMs <= now) delete this.data.parties[code];
    }
  }

  private usedShare(): number {
    return Math.max(
      this.data.requests / FREE_PLAN.requestsPerDay,
      this.data.rowsWritten / FREE_PLAN.rowsWrittenPerDay,
    );
  }

  private async save(): Promise<void> {
    // This object's own request and row count too.
    this.data.requests += 1;
    this.data.rowsWritten += 1;
    await this.ctx.storage.put("budget", this.data);
  }

  private allowCreateFrom(ip: string, now: number): boolean {
    for (const [key, entry] of this.createsByIp) {
      if (now - entry.startedMs >= 60_000) this.createsByIp.delete(key);
    }
    const entry = this.createsByIp.get(ip);
    if (!entry) {
      if (this.createsByIp.size >= CONFIG.rateLimitMaxEntries) return false;
      this.createsByIp.set(ip, { startedMs: now, count: 1 });
      return true;
    }
    if (entry.count >= CONFIG.createsPerMinutePerIp) return false;
    entry.count++;
    return true;
  }

  /** Asked before a party exists, and holds its place until {@link release} or its last hour. */
  async admitParty(ip: string, code: string): Promise<Admission> {
    const now = Date.now();
    this.refresh(now);
    if (!this.allowCreateFrom(ip, now)) {
      return {
        ok: false,
        status: 429,
        error: "create_rate_limited",
        message: "You can create up to two parties per minute. Please try again shortly.",
      };
    }
    const level = levelFor(this.usedShare());
    if (level === "noNewParties" || level === "noNewMembers" || level === "stop") {
      return {
        ok: false,
        status: 503,
        error: "daily_budget_reached",
        message: "The party server has used most of its daily allowance. Please try again after midnight UTC.",
      };
    }
    if (this.data.created >= CONFIG.maxPartiesPerDay) {
      return {
        ok: false,
        status: 503,
        error: "daily_budget_reached",
        message: "The party server has started enough parties for today. Please try again after midnight UTC.",
      };
    }
    if (Object.keys(this.data.parties).length >= CONFIG.maxParties) {
      return {
        ok: false,
        status: 503,
        error: "server_full",
        message: "The party server is busy. Please try again in a few minutes.",
      };
    }
    this.data.created++;
    this.data.parties[code] = now + CONFIG.partyMaxAgeMs + CONFIG.sweepMs;
    await this.save();
    return { ok: true };
  }

  /** Adds what a party has spent and tells it how careful to be from now on. */
  async report(usage: Usage): Promise<BudgetLevel> {
    this.refresh(Date.now());
    this.data.requests += usage.requests;
    this.data.rowsWritten += usage.rowsWritten;
    await this.save();
    return levelFor(this.usedShare());
  }

  async release(code: string, usage: Usage): Promise<void> {
    this.refresh(Date.now());
    delete this.data.parties[code];
    this.data.requests += usage.requests;
    this.data.rowsWritten += usage.rowsWritten;
    await this.save();
  }

  async status() {
    this.refresh(Date.now());
    return {
      day: this.data.day,
      level: levelFor(this.usedShare()),
      usedPercent: Math.round(this.usedShare() * 1000) / 10,
      requests: this.data.requests,
      rowsWritten: this.data.rowsWritten,
      partiesToday: this.data.created,
      liveParties: Object.keys(this.data.parties).length,
    };
  }
}
