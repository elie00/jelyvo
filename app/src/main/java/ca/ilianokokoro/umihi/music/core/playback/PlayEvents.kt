package ca.ilianokokoro.umihi.music.core.playback

import java.time.Instant
import java.time.ZoneId

/**
 * How a play ended. Stored as TEXT on the `play_events` row (enum name), so the values must stay
 * stable across releases.
 */
enum class PlayEndReason {
    /** The listener heard (nearly) the whole track. */
    COMPLETED,

    /** Abandoned almost immediately: a strong negative signal. */
    SKIPPED_EARLY,

    /** Abandoned past the early window but before the end: a weak negative signal. */
    SKIPPED_LATE,

    /** The service died mid-play; no skip intent can be inferred. */
    APP_KILLED,

    /** The track duration was never known, so no ratio could be computed. */
    UNKNOWN,
}

/** A skip under both this listened time and [PLAY_END_EARLY_SKIP_MAX_RATIO] counts as early. */
const val PLAY_END_EARLY_SKIP_MAX_MS = 30_000L

/** See [PLAY_END_EARLY_SKIP_MAX_MS]. */
const val PLAY_END_EARLY_SKIP_MAX_RATIO = 0.5f

/** From this completion ratio on, a play counts as completed. */
const val PLAY_END_COMPLETED_MIN_RATIO = 0.8f

/**
 * Classifies how a play ended from the effectively listened time (pauses and seeks excluded)
 * and the track duration. Thresholds: SKIPPED_EARLY under 30s *and* under 50% listened,
 * SKIPPED_LATE under 80%, COMPLETED otherwise. UNKNOWN when the duration was never resolved.
 */
fun classifyPlayEnd(listenedMs: Long, durationMs: Long): PlayEndReason {
    if (durationMs <= 0L) {
        return PlayEndReason.UNKNOWN
    }
    val ratio = listenedMs.toFloat() / durationMs
    return when {
        listenedMs < PLAY_END_EARLY_SKIP_MAX_MS && ratio < PLAY_END_EARLY_SKIP_MAX_RATIO ->
            PlayEndReason.SKIPPED_EARLY

        ratio < PLAY_END_COMPLETED_MIN_RATIO -> PlayEndReason.SKIPPED_LATE
        else -> PlayEndReason.COMPLETED
    }
}

/**
 * Classification for a play cut short by the service dying: a finished listen still counts as
 * COMPLETED, anything else becomes APP_KILLED because the abandon was not the listener's choice.
 */
fun classifyPlayEndOnServiceDeath(listenedMs: Long, durationMs: Long): PlayEndReason =
    if (classifyPlayEnd(listenedMs, durationMs) == PlayEndReason.COMPLETED) {
        PlayEndReason.COMPLETED
    } else {
        PlayEndReason.APP_KILLED
    }

/** Share of the track that was effectively listened to, clamped to [0, 1]; 0 when unknown. */
fun playCompletionRatio(listenedMs: Long, durationMs: Long): Float =
    if (durationMs <= 0L) 0f else (listenedMs.toFloat() / durationMs).coerceIn(0f, 1f)

/**
 * Accumulates *effective* playback time: the counter only runs while the player is actually
 * playing, so pauses never inflate it and a seek neither adds nor removes listened time.
 * Immutable value semantics keep it a pure, testable state machine; the clock is always injected.
 */
data class ListenStopwatch(
    val accumulatedMs: Long = 0L,
    /** Wall-clock instant playback last started, or null while paused. */
    val runningSinceMs: Long? = null,
) {
    fun started(nowMs: Long): ListenStopwatch =
        if (runningSinceMs != null) this else copy(runningSinceMs = nowMs)

    fun stopped(nowMs: Long): ListenStopwatch =
        if (runningSinceMs == null) {
            this
        } else {
            ListenStopwatch(
                accumulatedMs = accumulatedMs + (nowMs - runningSinceMs).coerceAtLeast(0L),
                runningSinceMs = null,
            )
        }

    fun totalMs(nowMs: Long): Long =
        accumulatedMs + (runningSinceMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L)
}

/** Local hour of day (0-23) of [epochMs] in [zone], injected for testability. */
fun hourOfDay(epochMs: Long, zone: ZoneId): Int =
    Instant.ofEpochMilli(epochMs).atZone(zone).hour

/** ISO day of week (1 = Monday … 7 = Sunday) of [epochMs] in [zone]. */
fun dayOfWeekIso(epochMs: Long, zone: ZoneId): Int =
    Instant.ofEpochMilli(epochMs).atZone(zone).dayOfWeek.value
