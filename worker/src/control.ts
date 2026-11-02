/*
 * Adapted from BitChord's party server (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
import { CONFIG } from "./config";
import {
  addUpcoming,
  clearUpcoming,
  type Member,
  mayControl,
  moveUpcoming,
  type PartyData,
  PartyError,
  pause,
  play,
  removeMember,
  removeUpcoming,
  seek,
  setAutoplay,
  setHostOnlyControl,
  setMaxMembers,
  setQueue,
  setTrack,
  step,
  type Track,
  trackFromWire,
} from "./party";

/** The actions a party's host-only setting restricts. Membership actions are host-only anyway. */
const CONTROL_ACTIONS = new Set([
  "play",
  "pause",
  "seek",
  "setTrack",
  "setQueue",
  "queueAdd",
  "queueRemove",
  "queueClear",
  "queueMove",
  "next",
  "previous",
  "setAutoplay",
]);

export type ControlResult =
  | { ok: true; kickedMemberId?: string }
  | { ok: false; error: string; message: string };

const refuse = (error: string, message: string): ControlResult => ({ ok: false, error, message });

function tracksFrom(raw: unknown): Track[] {
  if (!Array.isArray(raw)) return [];
  return raw.map(trackFromWire).filter((track): track is Track => track !== null);
}

const integer = (value: unknown): number | undefined =>
  typeof value === "number" && Number.isFinite(value) ? Math.trunc(value) : undefined;

/**
 * Applies one control frame. The host-only check lives here rather than in the clients: an app
 * hiding its own next button is a courtesy, and this is what actually stops a listener's device.
 */
export function applyControl(
  party: PartyData,
  now: number,
  member: Member,
  action: string,
  frame: Record<string, unknown>,
): ControlResult {
  if (CONTROL_ACTIONS.has(action) && !mayControl(party, member)) {
    return refuse("host_only", "Only the host can control the music in this party.");
  }
  const p = party.playback;
  const id = member.memberId;
  const position = integer(frame.positionMs);

  try {
    switch (action) {
      case "play":
        play(p, now, id, position);
        return { ok: true };
      case "pause":
        pause(p, now, id, position);
        return { ok: true };
      case "seek":
        if (position === undefined) return refuse("missing_position", "seek requires positionMs");
        seek(p, now, id, position);
        return { ok: true };
      case "setTrack": {
        const track = trackFromWire(frame.track);
        if (track === null) return refuse("invalid_track", "Track must include videoId");
        setTrack(
          p,
          now,
          id,
          track,
          position ?? 0,
          typeof frame.isPlaying === "boolean" ? frame.isPlaying : true,
          integer(frame.queueIndex) ?? null,
          member.displayName,
        );
        return { ok: true };
      }
      case "setQueue":
        setQueue(p, now, id, tracksFrom(frame.queue), integer(frame.queueIndex) ?? -1);
        return { ok: true };
      case "queueAdd": {
        const single = trackFromWire(frame.track);
        const result = addUpcoming(
          p,
          now,
          id,
          single ? [single] : tracksFrom(frame.tracks),
          frame.playNext === true,
        );
        if (result.ok) return { ok: true };
        return result.reason === "queue_full"
          ? refuse("queue_full", `Queue is full (maximum ${CONFIG.maxUpcomingQueue} upcoming songs).`)
          : refuse(result.reason, "Could not add track to queue.");
      }
      case "queueRemove": {
        let videoId = typeof frame.videoId === "string" ? frame.videoId : "";
        if (videoId === "") {
          const index = integer(frame.index);
          if (index !== undefined) videoId = p.queue[index]?.videoId ?? "";
        }
        if (videoId === "") return refuse("missing_track", "queueRemove requires videoId or index");
        return removeUpcoming(p, now, id, videoId)
          ? { ok: true }
          : refuse("not_found", "Track is not in the upcoming queue");
      }
      case "queueClear":
        return clearUpcoming(p, now, id)
          ? { ok: true }
          : refuse("no_upcoming", "No upcoming tracks to clear");
      case "queueMove": {
        const from = integer(frame.fromIndex);
        const to = integer(frame.toIndex);
        if (from === undefined || to === undefined) {
          return refuse("missing_indices", "queueMove requires fromIndex and toIndex");
        }
        const videoId = typeof frame.videoId === "string" ? frame.videoId : "";
        return moveUpcoming(p, now, id, from, to, videoId)
          ? { ok: true }
          : refuse("invalid_move", "Invalid queue move indices");
      }
      case "next":
        return step(p, now, id, 1, member.displayName)
          ? { ok: true }
          : refuse("end_of_queue", "Already at the end of the queue.");
      case "previous":
        return step(p, now, id, -1, member.displayName)
          ? { ok: true }
          : refuse("start_of_queue", "Already at the beginning of the queue.");
      case "setMaxMembers": {
        const size = integer(frame.maxMembers);
        if (size === undefined) return refuse("invalid_capacity", "Choose a party size between 2 and 10.");
        setMaxMembers(party, now, member, size);
        return { ok: true };
      }
      case "setAutoplay":
        if (typeof frame.enabled !== "boolean") {
          return refuse("invalid_autoplay", "AutoPlay must be enabled or disabled.");
        }
        setAutoplay(p, now, id, frame.enabled);
        return { ok: true };
      case "setHostOnlyControl":
        if (typeof frame.enabled !== "boolean") {
          return refuse("invalid_control_policy", "Host-only control must be enabled or disabled.");
        }
        setHostOnlyControl(party, now, member, frame.enabled);
        return { ok: true };
      case "kick": {
        if (!member.isHost) return refuse("host_only", "Only the host can remove listeners.");
        const target = typeof frame.memberId === "string" ? frame.memberId : "";
        if (target === "" || target === id) return refuse("invalid_member", "Choose another listener to remove.");
        if (removeMember(party, now, target) === null) {
          return refuse("not_found", "That listener is no longer in this party.");
        }
        return { ok: true, kickedMemberId: target };
      }
      default:
        return refuse("unknown_action", `Unknown control action '${action}'`);
    }
  } catch (error) {
    if (error instanceof PartyError) return refuse(error.code, error.message);
    throw error;
  }
}

export function activityFrame(member: Member, action: string, frame: Record<string, unknown>, now: number) {
  return { type: "activity", action, by: member.displayName, detail: activityDetail(action, frame), atMs: now };
}

function activityDetail(action: string, frame: Record<string, unknown>): string {
  const title = (raw: unknown): string => {
    const value = (raw as { title?: unknown } | null | undefined)?.title;
    return typeof value === "string" ? value : "";
  };
  switch (action) {
    case "setTrack": {
      const name = title(frame.track);
      return name ? `Changed the song to "${name}"` : action;
    }
    case "queueAdd": {
      const tracks = Array.isArray(frame.tracks) ? frame.tracks : [];
      const name = title(tracks[0]);
      if (!name) return action;
      return tracks.length === 1
        ? `Added "${name}" to the queue`
        : `Added "${name}" and ${tracks.length - 1} more to the queue`;
    }
    case "setQueue":
      return Array.isArray(frame.queue) ? `Replaced the queue with ${frame.queue.length} tracks` : action;
    case "queueRemove":
      return "Removed a track from the queue";
    case "queueClear":
      return "Cleared upcoming tracks";
    case "queueMove":
      return "Reordered the upcoming queue";
    case "next":
      return "Skipped to the next song";
    case "previous":
      return "Went back to the previous song";
    case "play":
      return "Started playback";
    case "pause":
      return "Paused playback";
    case "seek":
      return "Changed the playback position";
    case "kick":
      return "Removed a listener from the party";
    case "setMaxMembers":
      return "Changed the party size";
    case "setAutoplay":
      return "Changed AutoPlay";
    default:
      return action;
  }
}
