/*
 * Adapted from BitChord's party server (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
import { CONFIG } from "./config";

export class PartyError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

export interface Track {
  videoId: string;
  title: string;
  artist: string;
  thumbnailUrl?: string;
  durationMs?: number;
  fromAutoplay: boolean;
}

export function trackFromWire(raw: unknown): Track | null {
  if (typeof raw !== "object" || raw === null) return null;
  const value = raw as Record<string, unknown>;
  if (typeof value.videoId !== "string" || value.videoId === "") return null;
  const text = (field: unknown, max: number) =>
    typeof field === "string" ? field.slice(0, max) : "";
  const track: Track = {
    videoId: value.videoId.slice(0, 128),
    title: text(value.title, 300),
    artist: text(value.artist, 300),
    fromAutoplay: value.fromAutoplay === true,
  };
  const thumbnail = text(value.thumbnailUrl, 1000);
  if (thumbnail) track.thumbnailUrl = thumbnail;
  if (typeof value.durationMs === "number" && value.durationMs > 0) {
    track.durationMs = Math.trunc(value.durationMs);
  }
  return track;
}

/** Plain data, so a party can be stored as it is. */
export interface PlaybackData {
  track: Track | null;
  queue: Track[];
  queueIndex: number;
  isPlaying: boolean;
  positionMs: number;
  anchorMs: number;
  seq: number;
  queueSeq: number;
  updatedBy: string | null;
  updatedAtMs: number;
  startedBy: string | null;
  startedByName: string | null;
  autoplayEnabled: boolean;
}

export function newPlayback(now: number): PlaybackData {
  return {
    track: null,
    queue: [],
    queueIndex: -1,
    isPlaying: false,
    positionMs: 0,
    anchorMs: now,
    seq: 0,
    queueSeq: 0,
    updatedBy: null,
    updatedAtMs: now,
    startedBy: null,
    startedByName: null,
    autoplayEnabled: false,
  };
}

export function positionAt(p: PlaybackData, serverMs: number): number {
  if (!p.isPlaying) return p.positionMs;
  const position = p.positionMs + Math.max(0, serverMs - p.anchorMs);
  const duration = p.track?.durationMs;
  return duration && duration > 0 && position > duration ? duration : position;
}

function touch(p: PlaybackData, now: number, memberId: string | null): void {
  p.seq++;
  p.updatedBy = memberId;
  p.updatedAtMs = now;
}

function touchQueue(p: PlaybackData, now: number, memberId: string | null): void {
  p.queueSeq++;
  p.updatedBy = memberId;
  p.updatedAtMs = now;
}

export function play(p: PlaybackData, now: number, memberId: string | null, position?: number): void {
  p.positionMs = Math.max(0, position ?? positionAt(p, now));
  p.isPlaying = true;
  p.anchorMs = now + CONFIG.playLeadMs;
  touch(p, now, memberId);
}

export function pause(p: PlaybackData, now: number, memberId: string | null, position?: number): void {
  p.positionMs = Math.max(0, position ?? positionAt(p, now));
  p.isPlaying = false;
  p.anchorMs = now;
  touch(p, now, memberId);
}

export function seek(p: PlaybackData, now: number, memberId: string | null, position: number): void {
  p.positionMs = Math.max(0, position);
  p.anchorMs = now + (p.isPlaying ? CONFIG.playLeadMs : 0);
  touch(p, now, memberId);
}

export function setTrack(
  p: PlaybackData,
  now: number,
  memberId: string | null,
  track: Track | null,
  position: number,
  isPlaying: boolean,
  queueIndex: number | null,
  memberName: string | null,
): void {
  p.track = track;
  p.positionMs = Math.max(0, position);
  p.isPlaying = isPlaying && track !== null;
  p.anchorMs = now + (p.isPlaying ? CONFIG.playLeadMs : 0);

  if (
    queueIndex !== null &&
    queueIndex >= 0 &&
    queueIndex < p.queue.length &&
    track !== null &&
    p.queue[queueIndex]!.videoId === track.videoId
  ) {
    p.queueIndex = queueIndex;
  } else if (track !== null) {
    p.queueIndex = nearestIndexOf(p.queue, track.videoId, p.queueIndex);
  } else if (queueIndex !== null) {
    p.queueIndex = queueIndex;
  }

  p.startedBy = memberId;
  p.startedByName = memberName;
  touch(p, now, memberId);
}

export function setQueue(
  p: PlaybackData,
  now: number,
  memberId: string | null,
  queue: Track[],
  queueIndexIn: number,
): void {
  let queueIndex = queueIndexIn;
  // The sender's own index wins when it names the playing track. Searching from the top instead
  // finds the copy already in history whenever that track has played before.
  if (p.track !== null) {
    if (queueIndex < 0 || queueIndex >= queue.length || queue[queueIndex]!.videoId !== p.track.videoId) {
      queueIndex = nearestIndexOf(queue, p.track.videoId, queueIndex);
    }
  }

  if (queueIndex >= 0 && queueIndex < queue.length) {
    p.queue = queue.slice(0, queueIndex + 1 + CONFIG.maxUpcomingQueue);
    p.queueIndex = queueIndex;
  } else {
    p.queue = queue.slice(0, CONFIG.maxQueueLength);
    p.queueIndex = queueIndex >= 0 && queueIndex < p.queue.length ? queueIndex : -1;
  }
  touchQueue(p, now, memberId);
}

/**
 * Finds videoId closest to around: the slot after it first (the queue moving on), then the one
 * before it (going back), then the nearest copy. A queue can hold one track twice, once played and
 * once to come, and the first copy from the top is the wrong one as often as not.
 */
export function nearestIndexOf(queue: Track[], videoId: string, around: number): number {
  if (queue[around + 1]?.videoId === videoId) return around + 1;
  if (queue[around - 1]?.videoId === videoId) return around - 1;
  let best = -1;
  let bestDistance = 0;
  queue.forEach((item, index) => {
    if (item.videoId !== videoId) return;
    let distance = index - around;
    // Behind the needle costs a little more than ahead of it.
    if (distance < 0) distance = -distance * 2 + 1;
    if (best === -1 || distance < bestDistance) {
      best = index;
      bestDistance = distance;
    }
  });
  return best;
}

/**
 * Drops AutoPlay suggestions already waiting after the current track, or repeated in the batch: two
 * devices can both believe they supply AutoPlay for a moment. Tracks queued by hand are never
 * filtered, because queueing a song twice is allowed.
 */
function withoutQueuedAutoplay(queue: Track[], queueIndex: number, tracks: Track[]): Track[] {
  const waiting = new Set(queue.slice(Math.max(0, queueIndex + 1)).map((track) => track.videoId));
  return tracks.filter((track) => {
    if (!track.fromAutoplay) return true;
    if (waiting.has(track.videoId)) return false;
    waiting.add(track.videoId);
    return true;
  });
}

export type AddResult = { ok: true } | { ok: false; reason: "queue_full" | "no_tracks" };

export function addUpcoming(
  p: PlaybackData,
  now: number,
  memberId: string | null,
  tracks: Track[],
  playNext: boolean,
): AddResult {
  const upcoming =
    p.queueIndex >= 0 ? Math.max(0, p.queue.length - 1 - p.queueIndex) : p.queue.length;
  const slotsLeft = CONFIG.maxUpcomingQueue - upcoming;
  if (slotsLeft <= 0) return { ok: false, reason: "queue_full" };

  let toAdd = withoutQueuedAutoplay(p.queue, p.queueIndex, tracks);
  // Nothing to do is not a refusal, so the sender's echo wait still completes.
  if (toAdd.length === 0 && tracks.length > 0) return { ok: true };
  toAdd = toAdd.slice(0, slotsLeft);
  if (toAdd.length === 0) return { ok: false, reason: "no_tracks" };

  if (playNext && p.queueIndex >= 0 && p.queueIndex < p.queue.length) {
    p.queue.splice(p.queueIndex + 1, 0, ...toAdd);
  } else {
    p.queue.push(...toAdd);
  }
  touchQueue(p, now, memberId);
  return { ok: true };
}

export function removeUpcoming(p: PlaybackData, now: number, memberId: string | null, videoId: string): boolean {
  const match = p.queue.findIndex((item) => item.videoId === videoId);
  if (match === -1 || match === p.queueIndex) return false;
  p.queue.splice(match, 1);
  if (p.queueIndex > match) p.queueIndex--;
  touchQueue(p, now, memberId);
  return true;
}

export function clearUpcoming(p: PlaybackData, now: number, memberId: string | null): boolean {
  const keep = p.queueIndex >= 0 ? p.queueIndex + 1 : 0;
  if (p.queue.length <= keep) return false;
  p.queue.length = keep;
  touchQueue(p, now, memberId);
  return true;
}

export function moveUpcoming(
  p: PlaybackData,
  now: number,
  memberId: string | null,
  fromIn: number,
  to: number,
  videoId: string,
): boolean {
  let from = fromIn;
  if (videoId !== "") {
    from = p.queue.findIndex((item) => item.videoId === videoId);
    if (from === -1) return false;
  }
  if (from < 0 || from >= p.queue.length || to < 0 || to >= p.queue.length) return false;
  if (from <= p.queueIndex || to <= p.queueIndex) return false;
  if (from === to) return true;
  const [item] = p.queue.splice(from, 1);
  p.queue.splice(to, 0, item!);
  touchQueue(p, now, memberId);
  return true;
}

export function step(
  p: PlaybackData,
  now: number,
  memberId: string | null,
  delta: number,
  memberName: string | null,
): boolean {
  const target = p.queueIndex + delta;
  if (target < 0 || target >= p.queue.length) return false;
  setTrack(p, now, memberId, p.queue[target]!, 0, true, target, memberName);
  return true;
}

export function setAutoplay(p: PlaybackData, now: number, memberId: string | null, enabled: boolean): void {
  if (p.autoplayEnabled === enabled) return;
  p.autoplayEnabled = enabled;
  p.updatedBy = memberId;
  p.updatedAtMs = now;
}

export function playbackToWire(p: PlaybackData, serverMs: number) {
  return {
    seq: p.seq,
    track: p.track,
    queueSeq: p.queueSeq,
    queueIndex: p.queueIndex,
    queueLength: p.queue.length,
    isPlaying: p.isPlaying,
    positionMs: p.positionMs,
    anchorMs: p.anchorMs,
    effectivePositionMs: positionAt(p, serverMs),
    updatedBy: p.updatedBy,
    startedBy: p.startedBy,
    startedByName: p.startedByName,
    autoplayEnabled: p.autoplayEnabled,
    updatedAtMs: p.updatedAtMs,
  };
}

export function queueToWire(p: PlaybackData) {
  return { seq: p.queueSeq, index: p.queueIndex, items: p.queue };
}

export interface Member {
  memberId: string;
  userId: string;
  deviceId: string;
  displayName: string;
  avatarUrl: string | null;
  token: string;
  isHost: boolean;
  connected: boolean;
  joinedAtMs: number;
  lastSeenMs: number;
}

export function memberToWire(m: Member) {
  return {
    memberId: m.memberId,
    userId: m.userId,
    displayName: m.displayName,
    avatarUrl: m.avatarUrl,
    isHost: m.isHost,
    connected: m.connected,
    joinedAtMs: m.joinedAtMs,
    lastSeenMs: m.lastSeenMs,
  };
}

export interface PartyData {
  code: string;
  members: Member[];
  playback: PlaybackData;
  maxMembers: number;
  /**
   * Restricts every playback and queue action to the host. On the party rather than the playback
   * state because that state rides every heartbeat; this travels on the members frame instead.
   */
  hostOnlyControl: boolean;
  createdAtMs: number;
  touchedAtMs: number;
  emptySinceMs: number | null;
}

export function newParty(code: string, now: number, maxMembers: number = CONFIG.defaultMaxMembers): PartyData {
  return {
    code,
    members: [],
    playback: newPlayback(now),
    maxMembers:
      maxMembers < CONFIG.minMembers || maxMembers > CONFIG.maxMembers
        ? CONFIG.defaultMaxMembers
        : maxMembers,
    hostOnlyControl: false,
    createdAtMs: now,
    touchedAtMs: now,
    emptySinceMs: now,
  };
}

function hex(bytes: number): string {
  return [...crypto.getRandomValues(new Uint8Array(bytes))]
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

export interface Identity {
  userId: string;
  deviceId: string;
  displayName: string;
  avatarUrl: string | null;
}

/** A device coming back reclaims its slot, so a full party does not fill with its own ghosts. */
export function join(party: PartyData, now: number, identity: Identity): Member {
  const existing = party.members.find((member) => member.deviceId === identity.deviceId);
  if (existing) {
    existing.displayName = identity.displayName;
    existing.avatarUrl = identity.avatarUrl;
    existing.userId = identity.userId;
    existing.lastSeenMs = now;
    existing.token = hex(24);
    party.touchedAtMs = now;
    return existing;
  }
  if (party.members.length >= party.maxMembers) {
    throw new PartyError(409, "party_full", `This party is full (${party.maxMembers} devices).`);
  }
  const member: Member = {
    memberId: hex(8),
    userId: identity.userId,
    deviceId: identity.deviceId,
    displayName: identity.displayName,
    avatarUrl: identity.avatarUrl,
    token: hex(24),
    isHost: party.members.length === 0,
    connected: false,
    joinedAtMs: now,
    lastSeenMs: now,
  };
  party.members.push(member);
  party.touchedAtMs = now;
  return member;
}

export function setMaxMembers(party: PartyData, now: number, member: Member, maxMembers: number): void {
  if (!member.isHost) throw new PartyError(403, "host_only", "Only the host can change the party size.");
  if (maxMembers < CONFIG.minMembers || maxMembers > CONFIG.maxMembers) {
    throw new PartyError(422, "invalid_capacity", "Party size must be between 2 and 10.");
  }
  if (maxMembers < party.members.length) {
    throw new PartyError(409, "party_too_small", "Party size cannot be smaller than the current member count.");
  }
  party.maxMembers = maxMembers;
  party.touchedAtMs = now;
}

export function setHostOnlyControl(party: PartyData, now: number, member: Member, enabled: boolean): void {
  if (!member.isHost) {
    throw new PartyError(403, "host_only", "Only the host can change who controls the music.");
  }
  party.hostOnlyControl = enabled;
  party.touchedAtMs = now;
}

export function mayControl(party: PartyData, member: Member): boolean {
  return !party.hostOnlyControl || member.isHost;
}

export function removeMember(party: PartyData, now: number, memberId: string): Member | null {
  const index = party.members.findIndex((member) => member.memberId === memberId);
  if (index === -1) return null;
  const [removed] = party.members.splice(index, 1);
  // Oldest member first, so who inherits the party does not depend on anything arbitrary.
  if (removed!.isHost && party.members.length > 0) {
    party.members.reduce((a, b) => (a.joinedAtMs <= b.joinedAtMs ? a : b)).isHost = true;
  }
  party.touchedAtMs = now;
  refreshEmptiness(party, now);
  return removed!;
}

export function markConnected(party: PartyData, now: number, member: Member, connected: boolean): void {
  member.connected = connected;
  member.lastSeenMs = now;
  party.touchedAtMs = now;
  refreshEmptiness(party, now);
}

function refreshEmptiness(party: PartyData, now: number): void {
  if (party.members.some((member) => member.connected)) party.emptySinceMs = null;
  else if (party.emptySinceMs === null) party.emptySinceMs = now;
}

/** A dropped socket is not a departure: the slot survives the grace period. */
export function expiredMembers(party: PartyData, now: number): Member[] {
  return party.members.filter(
    (member) => !member.connected && now - member.lastSeenMs > CONFIG.disconnectGraceMs,
  );
}

export function isExpired(party: PartyData, now: number): boolean {
  if (now - party.createdAtMs > CONFIG.partyMaxAgeMs) return true;
  return party.emptySinceMs !== null && now - party.emptySinceMs > CONFIG.emptyPartyTtlMs;
}

function membersInOrder(party: PartyData): Member[] {
  return [...party.members].sort((a, b) => a.joinedAtMs - b.joinedAtMs);
}

export function partyToWire(party: PartyData, now: number) {
  return {
    code: party.code,
    createdAtMs: party.createdAtMs,
    maxMembers: party.maxMembers,
    hostOnlyControl: party.hostOnlyControl,
    members: membersInOrder(party).map(memberToWire),
    playback: playbackToWire(party.playback, now),
    queue: queueToWire(party.playback),
    serverMs: now,
  };
}

/** Who is in a party, for someone holding a code. Nothing here lets a caller act on the party. */
export function partyPreview(party: PartyData) {
  const ordered = membersInOrder(party);
  return {
    code: party.code,
    hostName: ordered.find((member) => member.isHost)?.displayName ?? "",
    memberCount: ordered.length,
    maxMembers: party.maxMembers,
    isFull: ordered.length >= party.maxMembers,
    members: ordered.map((member) => ({
      displayName: member.displayName,
      avatarUrl: member.avatarUrl,
      isHost: member.isHost,
    })),
  };
}

export function stateFrame(party: PartyData, now: number) {
  return { type: "state", playback: playbackToWire(party.playback, now), serverMs: now };
}

export function queueFrame(party: PartyData, now: number) {
  return { type: "queue", queue: queueToWire(party.playback), serverMs: now };
}

export function membersFrame(party: PartyData, now: number) {
  return {
    type: "members",
    members: membersInOrder(party).map(memberToWire),
    maxMembers: party.maxMembers,
    hostOnlyControl: party.hostOnlyControl,
    serverMs: now,
  };
}

export interface JoinRequest extends Identity {
  maxMembers?: number;
  autoplayEnabled?: boolean;
}

/** Ensures the identity fields of a create or join body are present and safe. */
export function parseJoinRequest(raw: unknown): JoinRequest {
  const fail = (message: string) => new PartyError(422, "validation_error", message);
  if (typeof raw !== "object" || raw === null) throw fail("Body must be a JSON object.");
  const body = raw as Record<string, unknown>;
  const text = (field: unknown) => (typeof field === "string" ? field.trim() : "");

  const userId = text(body.userId);
  if (userId === "" || userId.length > 128) throw fail("userId must be between 1 and 128 characters");
  const deviceId = text(body.deviceId);
  if (deviceId === "" || deviceId.length > 128) throw fail("deviceId must be between 1 and 128 characters");
  const displayName = text(body.displayName);
  if (displayName === "" || displayName.length > 80) {
    throw fail("displayName must be between 1 and 80 characters");
  }

  let avatarUrl: string | null = text(body.avatarUrl) || null;
  if (avatarUrl !== null) {
    if (avatarUrl.length > 1000) throw fail("avatarUrl must not exceed 1000 characters");
    if (!/^https?:\/\//.test(avatarUrl)) throw fail("avatarUrl must be an http(s) URL");
  } else {
    avatarUrl = null;
  }

  const request: JoinRequest = { userId, deviceId, displayName, avatarUrl };
  if (body.maxMembers !== undefined && body.maxMembers !== null) {
    if (
      typeof body.maxMembers !== "number" ||
      !Number.isInteger(body.maxMembers) ||
      body.maxMembers < CONFIG.minMembers ||
      body.maxMembers > CONFIG.maxMembers
    ) {
      throw fail("maxMembers must be between 2 and 10");
    }
    request.maxMembers = body.maxMembers;
  }
  if (typeof body.autoplayEnabled === "boolean") request.autoplayEnabled = body.autoplayEnabled;
  return request;
}
