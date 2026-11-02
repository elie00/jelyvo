/*
 * Wire format and server clock adapted from BitChord
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.core.jam

import kotlinx.serialization.Serializable
import kotlin.math.abs

/** A song as the party knows it: enough to find and show it, never how a device plays it. */
@Serializable
data class PartyTrack(
    val videoId: String,
    val title: String = "",
    val artist: String = "",
    val thumbnailUrl: String? = null,
    val durationMs: Long? = null,
    val fromAutoplay: Boolean = false,
)

@Serializable
data class PartyMember(
    val memberId: String,
    val displayName: String = "",
    val isHost: Boolean = false,
    val connected: Boolean = false,
)

/**
 * Where the party is. [positionMs] is the position at server time [anchorMs], not a current one,
 * which is the whole sync mechanism: a frame delayed by 300 ms carries an anchor 300 ms older and
 * still lands a device in exactly the right place.
 */
@Serializable
data class PartyPlayback(
    /** A state whose seq is lower than the one applied is stale and must be dropped. */
    val seq: Long = 0,
    val track: PartyTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val anchorMs: Long = 0,
) {
    /** The party's playhead at server time [serverNowMs]. */
    fun positionAt(serverNowMs: Long): Long =
        if (isPlaying) positionMs + (serverNowMs - anchorMs).coerceAtLeast(0L) else positionMs
}

@Serializable
data class PartySnapshot(
    val code: String = "",
    val playback: PartyPlayback = PartyPlayback(),
    val members: List<PartyMember> = emptyList(),
)

@Serializable
data class JoinReply(
    val code: String,
    val token: String,
    val you: PartyMember,
    val party: PartySnapshot = PartySnapshot(),
)

/** Every frame the server sends, flattened: each type fills only the fields it uses. */
@Serializable
data class ServerFrame(
    val type: String = "",
    val playback: PartyPlayback? = null,
    val members: List<PartyMember>? = null,
    val party: PartySnapshot? = null,
    val clientMs: Long? = null,
    val serverMs: Long? = null,
    val reason: String? = null,
)

/** Accepts what a person would paste, and only https: tokens must never travel in the clear. */
fun normalizeJamServerUrl(raw: String): String? {
    val url = raw.trim().trimEnd('/')
    if (!url.startsWith("https://", ignoreCase = true)) return null
    return url.takeIf { it.length > "https://".length && ' ' !in it }
}

/**
 * This device's offset from the server's clock, NTP-style. The sample with the smallest round trip
 * wins rather than the newest or the average: one request delayed by a radio wake-up is wrong by
 * hundreds of milliseconds, and averaging would fold that error in instead of discarding it.
 * [localNowMs] must be monotonic, since a stepped wall clock would move the playhead.
 */
class ServerClock(private val localNowMs: () -> Long) {
    private data class Sample(val offsetMs: Long, val roundTripMs: Long, val takenAtMs: Long)

    private val samples = ArrayDeque<Sample>()

    @Volatile
    var offsetMs: Long? = null
        private set

    @Synchronized
    fun record(sentAtLocalMs: Long, serverMs: Long, receivedAtLocalMs: Long) {
        val roundTrip = (receivedAtLocalMs - sentAtLocalMs).coerceAtLeast(0L)
        samples.addLast(Sample(serverMs - (sentAtLocalMs + roundTrip / 2), roundTrip, receivedAtLocalMs))
        while (samples.size > WINDOW) samples.removeFirst()
        // Old samples go first, or one lucky round trip would pin the offset while clocks drift.
        val cutoff = receivedAtLocalMs - SAMPLE_TTL_MS
        offsetMs = samples.filter { it.takenAtMs >= cutoff }
            .ifEmpty { samples.toList() }
            .minBy { it.roundTripMs }
            .offsetMs
    }

    /** The server's clock read from here, or null before the first pong. */
    fun serverNowMs(): Long? = offsetMs?.let { localNowMs() + it }

    fun nowMs(): Long = localNowMs()

    private companion object {
        const val WINDOW = 12
        const val SAMPLE_TTL_MS = 120_000L
    }
}

/** What this device is doing, as far as the party cares. */
data class LocalPlayback(
    val mediaId: String?,
    val positionMs: Long,
    val isPlaying: Boolean,
)

sealed interface JamAction {
    data class ChangeTrack(val track: PartyTrack, val positionMs: Long, val play: Boolean) : JamAction
    data class Seek(val positionMs: Long) : JamAction
    data class SetPlaying(val play: Boolean) : JamAction
}

object JamSync {
    /** Closer than this is in sync: chasing a smaller gap would make playback audibly stutter. */
    const val DRIFT_TOLERANCE_MS = 500L

    /** What this device must do to match [party]. Empty when it already does. */
    fun plan(party: PartyPlayback, serverNowMs: Long, local: LocalPlayback): List<JamAction> {
        val track = party.track ?: return emptyList()
        val target = party.positionAt(serverNowMs).let { position ->
            track.durationMs?.takeIf { it > 0L }?.let { position.coerceAtMost(it) } ?: position
        }
        if (track.videoId != local.mediaId) {
            return listOf(JamAction.ChangeTrack(track, target, party.isPlaying))
        }
        return buildList {
            if (abs(target - local.positionMs) > DRIFT_TOLERANCE_MS) add(JamAction.Seek(target))
            if (party.isPlaying != local.isPlaying) add(JamAction.SetPlaying(party.isPlaying))
        }
    }

    /** Whether a position this device just reached differs from the party by a real seek. */
    fun isLocalSeek(party: PartyPlayback, serverNowMs: Long, positionMs: Long): Boolean =
        abs(party.positionAt(serverNowMs) - positionMs) > DRIFT_TOLERANCE_MS
}
