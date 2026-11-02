/*
 * Adapted from BitChord's party server tests (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
import { describe, expect, it } from "vitest";
import { levelFor } from "../src/budget";
import { isValid, newCode, normalise } from "../src/codes";
import { CONFIG } from "../src/config";
import { applyControl } from "../src/control";
import {
  addUpcoming,
  clearUpcoming,
  expiredMembers,
  isExpired,
  join,
  markConnected,
  mayControl,
  moveUpcoming,
  newParty,
  newPlayback,
  parseJoinRequest,
  partyPreview,
  partyToWire,
  pause,
  play,
  positionAt,
  removeMember,
  removeUpcoming,
  seek,
  setHostOnlyControl,
  setQueue,
  setTrack,
  stateFrame,
  step,
  type Track,
} from "../src/party";
import { IpRateLimiter } from "../src/rateLimit";

const T0 = 1_000_000;
const track = (videoId: string, fromAutoplay = false): Track => ({
  videoId,
  title: videoId,
  artist: "",
  fromAutoplay,
});
const ids = (tracks: Track[]) => tracks.map((item) => item.videoId);
const identity = (name: string) => ({
  userId: `u_${name}`,
  deviceId: `d_${name}`,
  displayName: name,
  avatarUrl: null,
});

describe("codes", () => {
  it("reads a mistyped code charitably", () => {
    const cases: [string, string][] = [
      ["abc123", "ABC123"],
      ["abc-def", "ABCDEF"],
      [" 123 456 ", "123456"],
      ["o 1 l i", "0111"],
      ["OIL", "011"],
      ["a/b.c", "ABC"],
      ["", ""],
    ];
    for (const [input, expected] of cases) expect(normalise(input)).toBe(expected);
  });

  it("accepts only six characters of the alphabet", () => {
    expect(isValid("ABCDEF")).toBe(true);
    for (const bad of ["", "ABCDE", "ABCDEFG", "ABCDEI", "ABCDEL", "ABCDEO", "abcDEF"]) {
      expect(isValid(bad)).toBe(false);
    }
  });

  it("makes valid codes that differ", () => {
    const codes = new Set(Array.from({ length: 200 }, newCode));
    expect(codes.size).toBeGreaterThan(190);
    for (const code of codes) expect(isValid(code)).toBe(true);
  });
});

describe("the sync rule", () => {
  it("runs from the anchor while playing and holds while paused", () => {
    const p = newPlayback(T0);
    play(p, T0, "m", 1000);
    expect(p.anchorMs).toBe(T0 + CONFIG.playLeadMs);
    // Before the common instant it holds, rather than running backwards.
    expect(positionAt(p, T0)).toBe(1000);
    expect(positionAt(p, p.anchorMs + 500)).toBe(1500);
    pause(p, p.anchorMs + 500, "m");
    expect(p.isPlaying).toBe(false);
    expect(positionAt(p, p.anchorMs + 60_000)).toBe(1500);
  });

  it("stops at the end of the track", () => {
    const p = newPlayback(T0);
    setTrack(p, T0, "m", { ...track("a"), durationMs: 2000 }, 0, true, null, "M");
    expect(positionAt(p, T0 + 999_999)).toBe(2000);
  });

  it("schedules a seek ahead only while playing", () => {
    const p = newPlayback(T0);
    seek(p, T0, "m", 5000);
    expect(p.anchorMs).toBe(T0);
    play(p, T0, "m");
    seek(p, T0, "m", 6000);
    expect(p.anchorMs).toBe(T0 + CONFIG.playLeadMs);
  });

  it("bumps the sequence on every mutation of playback", () => {
    const p = newPlayback(T0);
    play(p, T0, "m");
    pause(p, T0, "m");
    seek(p, T0, "m", 1);
    expect(p.seq).toBe(3);
  });

  it("never sends the queue on the state frame", () => {
    const party = newParty("ABCDEF", T0);
    setQueue(party.playback, T0, null, [track("a"), track("b")], 0);
    const { playback } = stateFrame(party, T0);
    expect(playback).not.toHaveProperty("queue");
    expect(playback.queueLength).toBe(2);
  });
});

describe("the queue", () => {
  it("holds at most 25 upcoming tracks", () => {
    const p = newPlayback(T0);
    const current = track("now");
    setTrack(p, T0, null, current, 0, true, null, null);
    setQueue(p, T0, null, [current], 0);

    const batch = (from: number, to: number) =>
      Array.from({ length: to - from }, (_, i) => track(`t${from + i}`));
    expect(addUpcoming(p, T0, null, batch(0, 20), false).ok).toBe(true);
    expect(p.queue).toHaveLength(21);
    expect(addUpcoming(p, T0, null, batch(20, 30), false).ok).toBe(true);
    expect(p.queue).toHaveLength(26);
    expect(addUpcoming(p, T0, null, [track("overflow")], false)).toEqual({
      ok: false,
      reason: "queue_full",
    });
  });

  it("removes and clears upcoming tracks but never the playing one", () => {
    const p = newPlayback(T0);
    const current = track("curr");
    setTrack(p, T0, null, current, 0, true, null, null);
    setQueue(p, T0, null, [current, track("t1"), track("t2")], 0);
    expect(removeUpcoming(p, T0, null, "t1")).toBe(true);
    expect(p.queue).toHaveLength(2);
    expect(removeUpcoming(p, T0, null, "curr")).toBe(false);
    expect(clearUpcoming(p, T0, null)).toBe(true);
    expect(ids(p.queue)).toEqual(["curr"]);
  });

  it("steps through the queue and stops at its end", () => {
    const p = newPlayback(T0);
    const first = track("t1");
    setTrack(p, T0, null, first, 0, true, null, null);
    setQueue(p, T0, null, [first, track("t2")], 0);
    expect(step(p, T0, null, 1, "Alice")).toBe(true);
    expect(p.queueIndex).toBe(1);
    expect(p.track?.videoId).toBe("t2");
    expect(p.startedByName).toBe("Alice");
    expect(step(p, T0, null, 1, "Alice")).toBe(false);
  });

  it("moves upcoming tracks, resolving by videoId when given one", () => {
    const p = newPlayback(T0);
    const t0 = track("t0");
    setTrack(p, T0, null, t0, 0, true, null, null);
    setQueue(p, T0, null, [t0, track("t1"), track("t2"), track("t3")], 0);
    const seq = p.queueSeq;

    expect(moveUpcoming(p, T0, "m", 0, 2, "")).toBe(false);
    expect(moveUpcoming(p, T0, "m", 2, 0, "")).toBe(false);
    expect(moveUpcoming(p, T0, "m", 1, 3, "")).toBe(true);
    expect(p.queueSeq).toBe(seq + 1);
    expect(ids(p.queue)).toEqual(["t0", "t2", "t3", "t1"]);
    expect(moveUpcoming(p, T0, "m", 3, 1, "")).toBe(true);
    expect(ids(p.queue)).toEqual(["t0", "t1", "t2", "t3"]);
    expect(moveUpcoming(p, T0, "m", 0, 3, "t2")).toBe(true);
    expect(ids(p.queue)).toEqual(["t0", "t1", "t3", "t2"]);
    expect(moveUpcoming(p, T0, "m", 1, 2, "unknown")).toBe(false);
    expect(moveUpcoming(p, T0, "m", 1, 10, "")).toBe(false);
    expect(moveUpcoming(p, T0, "m", 2, 2, "")).toBe(true);
  });

  it("does not touch the playback sequence", () => {
    const p = newPlayback(T0);
    const t0 = track("t0");
    setTrack(p, T0, null, t0, 0, true, null, null);
    const seq = p.seq;
    setQueue(p, T0, "m", [t0, track("t1"), track("t2")], 0);
    moveUpcoming(p, T0, "m", 1, 2, "");
    addUpcoming(p, T0, "m", [track("t3")], false);
    removeUpcoming(p, T0, "m", "t3");
    clearUpcoming(p, T0, "m");
    expect(p.seq).toBe(seq);
    expect(p.queueSeq).toBe(5);
  });

  it("skips AutoPlay tracks already waiting but keeps hand-queued duplicates", () => {
    const p = newPlayback(T0);
    const current = track("curr");
    setTrack(p, T0, null, current, 0, true, null, null);
    setQueue(p, T0, null, [current], 0);
    const station = Array.from({ length: 10 }, (_, i) => track(`a${i}`, true));
    expect(addUpcoming(p, T0, null, station, false).ok).toBe(true);
    const seq = p.queueSeq;
    expect(addUpcoming(p, T0, null, station, false).ok).toBe(true);
    expect(p.queue).toHaveLength(11);
    expect(p.queueSeq).toBe(seq);
    expect(addUpcoming(p, T0, null, [track("a0")], false).ok).toBe(true);
    expect(p.queue).toHaveLength(12);
  });

  it("prefers the copy of a repeated track at the needle", () => {
    const p = newPlayback(T0);
    const x = track("x");
    const b = track("b");
    setTrack(p, T0, null, x, 0, true, null, null);
    setQueue(p, T0, null, [x, track("a"), x, b], 2);
    expect(p.queueIndex).toBe(2);
    setTrack(p, T0, null, b, 0, true, null, null);
    expect(p.queueIndex).toBe(3);
    setTrack(p, T0, null, x, 0, true, null, null);
    expect(p.queueIndex).toBe(2);
    // An index that names a different track is not trusted.
    setTrack(p, T0, null, b, 0, true, 0, null);
    expect(p.queueIndex).toBe(3);
  });
});

describe("members", () => {
  it("makes the first member host and refuses past capacity", () => {
    const party = newParty("ABCDEF", T0);
    expect(join(party, T0, identity("a")).isHost).toBe(true);
    for (const name of ["b", "c", "d", "e"]) expect(join(party, T0, identity(name)).isHost).toBe(false);
    expect(() => join(party, T0, identity("f"))).toThrow("full");
  });

  it("lets a device reclaim its slot with a new token", () => {
    const party = newParty("ABCDEF", T0);
    const first = join(party, T0, identity("a"));
    const oldToken = first.token;
    const again = join(party, T0 + 1, { ...identity("a"), displayName: "Renamed" });
    expect(party.members).toHaveLength(1);
    expect(again.displayName).toBe("Renamed");
    expect(again.token).not.toBe(oldToken);
  });

  it("passes host to the oldest member, who inherits the host-only setting", () => {
    const party = newParty("ABCDEF", T0);
    const host = join(party, T0, identity("a"));
    const second = join(party, T0 + 1, identity("b"));
    join(party, T0 + 2, identity("c"));
    setHostOnlyControl(party, T0, host, true);
    expect(mayControl(party, second)).toBe(false);
    removeMember(party, T0 + 3, host.memberId);
    expect(second.isHost).toBe(true);
    expect(mayControl(party, second)).toBe(true);
    expect(partyToWire(party, T0).hostOnlyControl).toBe(true);
  });

  it("keeps a dropped socket's slot through the grace period", () => {
    const party = newParty("ABCDEF", T0);
    const member = join(party, T0, identity("a"));
    markConnected(party, T0, member, true);
    expect(expiredMembers(party, T0 + CONFIG.disconnectGraceMs * 2)).toHaveLength(0);
    markConnected(party, T0, member, false);
    expect(expiredMembers(party, T0 + CONFIG.disconnectGraceMs)).toHaveLength(0);
    expect(expiredMembers(party, T0 + CONFIG.disconnectGraceMs + 1)).toHaveLength(1);
  });

  it("ends a party when empty for too long and past its ceiling regardless", () => {
    const party = newParty("ABCDEF", T0);
    expect(isExpired(party, T0 + CONFIG.emptyPartyTtlMs)).toBe(false);
    expect(isExpired(party, T0 + CONFIG.emptyPartyTtlMs + 1)).toBe(true);
    markConnected(party, T0, join(party, T0, identity("a")), true);
    expect(isExpired(party, T0 + CONFIG.emptyPartyTtlMs + 1)).toBe(false);
    expect(isExpired(party, T0 + CONFIG.partyMaxAgeMs + 1)).toBe(true);
  });

  it("shows a preview with no ids, no token and nothing about the music", () => {
    const party = newParty("ABCDEF", T0);
    join(party, T0, identity("a"));
    const text = JSON.stringify(partyPreview(party));
    expect(partyPreview(party).hostName).toBe("a");
    for (const secret of ["token", "userId", "deviceId", "memberId", "playback", "queue"]) {
      expect(text).not.toContain(secret);
    }
  });

  it("validates identities", () => {
    expect(() => parseJoinRequest({})).toThrow("userId");
    expect(() => parseJoinRequest({ userId: "u", deviceId: "d" })).toThrow("displayName");
    const base = { userId: "u", deviceId: "d", displayName: "n" };
    expect(() => parseJoinRequest({ ...base, avatarUrl: "file:///x" })).toThrow("http");
    expect(() => parseJoinRequest({ ...base, maxMembers: 11 })).toThrow("maxMembers");
    expect(parseJoinRequest({ ...base, avatarUrl: "  " }).avatarUrl).toBeNull();
  });
});

describe("controls", () => {
  const setUp = () => {
    const party = newParty("ABCDEF", T0);
    const host = join(party, T0, identity("host"));
    const guest = join(party, T0, identity("guest"));
    return { party, host, guest };
  };

  it("restricts music, but not membership, under host-only control", () => {
    const { party, host, guest } = setUp();
    expect(applyControl(party, T0, host, "setHostOnlyControl", { enabled: true }).ok).toBe(true);
    expect(applyControl(party, T0, guest, "play", {})).toMatchObject({ error: "host_only" });
    expect(applyControl(party, T0, guest, "setAutoplay", { enabled: true })).toMatchObject({
      error: "host_only",
    });
    expect(applyControl(party, T0, host, "play", {}).ok).toBe(true);
  });

  it("leaves kick and the party size to the host", () => {
    const { party, host, guest } = setUp();
    expect(applyControl(party, T0, guest, "kick", { memberId: host.memberId })).toMatchObject({
      error: "host_only",
    });
    expect(applyControl(party, T0, guest, "setMaxMembers", { maxMembers: 3 })).toMatchObject({
      error: "host_only",
    });
    expect(applyControl(party, T0, host, "kick", { memberId: host.memberId })).toMatchObject({
      error: "invalid_member",
    });
    expect(applyControl(party, T0, host, "kick", { memberId: guest.memberId })).toEqual({
      ok: true,
      kickedMemberId: guest.memberId,
    });
    expect(party.members).toHaveLength(1);
  });

  it("explains what is wrong with a malformed control", () => {
    const { party, host } = setUp();
    const error = (action: string, frame = {}) => applyControl(party, T0, host, action, frame);
    expect(error("seek")).toMatchObject({ error: "missing_position" });
    expect(error("setTrack")).toMatchObject({ error: "invalid_track" });
    expect(error("queueMove")).toMatchObject({ error: "missing_indices" });
    expect(error("queueRemove")).toMatchObject({ error: "missing_track" });
    expect(error("next")).toMatchObject({ error: "end_of_queue" });
    expect(error("nonsense")).toMatchObject({ error: "unknown_action" });
  });

  it("trims what a track carries", () => {
    const { party, host } = setUp();
    const long = "x".repeat(2000);
    applyControl(party, T0, host, "setTrack", {
      track: { videoId: long, title: long, artist: long, thumbnailUrl: long, durationMs: -5 },
    });
    const stored = party.playback.track!;
    expect(stored.videoId).toHaveLength(128);
    expect(stored.title).toHaveLength(300);
    expect(stored.thumbnailUrl).toHaveLength(1000);
    expect(stored.durationMs).toBeUndefined();
  });
});

describe("guards", () => {
  it("gets more careful as the day's allowance goes", () => {
    expect(levelFor(0)).toBe("normal");
    expect(levelFor(0.49)).toBe("normal");
    expect(levelFor(0.5)).toBe("saver");
    expect(levelFor(0.7)).toBe("noNewParties");
    expect(levelFor(0.85)).toBe("noNewMembers");
    expect(levelFor(0.95)).toBe("stop");
    expect(levelFor(1.5)).toBe("stop");
  });

  it("limits an address within its window", () => {
    const limiter = new IpRateLimiter(60_000, 2, 100);
    expect(limiter.allow("a", T0)).toBe(true);
    expect(limiter.allow("a", T0)).toBe(true);
    expect(limiter.allow("a", T0)).toBe(false);
    expect(limiter.allow("b", T0)).toBe(true);
    expect(limiter.allow("a", T0 + 60_000)).toBe(true);
  });

  it("frees expired addresses when full, and refuses new ones until then", () => {
    const limiter = new IpRateLimiter(1000, 5, 2);
    expect(limiter.allow("a", T0)).toBe(true);
    expect(limiter.allow("b", T0)).toBe(true);
    expect(limiter.allow("c", T0)).toBe(false);
    expect(limiter.allow("c", T0 + 1000)).toBe(true);
  });

  it("keeps the alarm schedule inside the free plan for a full day of parties", () => {
    // Worst case: every allowed party playing around the clock, on the normal then the saver rate.
    const alarmsPerParty = (24 * 60 * 60 * 1000) / CONFIG.heartbeatMs;
    const saverAlarmsPerParty = (24 * 60 * 60 * 1000) / CONFIG.saverHeartbeatMs;
    expect(alarmsPerParty).toBe(8640);
    expect(saverAlarmsPerParty * CONFIG.maxParties).toBeLessThan(50_000);
  });
});
