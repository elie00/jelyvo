/*
 * Adapted from BitChord's party server (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */

/** Counts per address in fixed windows, and never holds more addresses than it was told to. */
export class IpRateLimiter {
  private entries = new Map<string, { startedMs: number; count: number }>();
  private sweptMs = 0;

  constructor(
    private readonly windowMs: number,
    private readonly limit: number,
    private readonly maxEntries: number,
  ) {}

  allow(ip: string, now: number = Date.now()): boolean {
    if (now - this.sweptMs >= 1000 || this.entries.size >= this.maxEntries) {
      for (const [key, entry] of this.entries) {
        if (now - entry.startedMs >= this.windowMs) this.entries.delete(key);
      }
      this.sweptMs = now;
    }
    const entry = this.entries.get(ip);
    if (!entry || now - entry.startedMs >= this.windowMs) {
      if (!entry && this.entries.size >= this.maxEntries) return false;
      this.entries.set(ip, { startedMs: now, count: 1 });
      return true;
    }
    if (entry.count >= this.limit) return false;
    entry.count++;
    return true;
  }
}
