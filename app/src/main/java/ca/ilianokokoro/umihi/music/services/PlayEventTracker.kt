package ca.ilianokokoro.umihi.music.services

import ca.ilianokokoro.umihi.music.core.playback.ListenStopwatch
import ca.ilianokokoro.umihi.music.core.playback.PlayEndReason
import ca.ilianokokoro.umihi.music.core.playback.classifyPlayEnd
import ca.ilianokokoro.umihi.music.core.playback.classifyPlayEndOnServiceDeath
import ca.ilianokokoro.umihi.music.core.playback.dayOfWeekIso
import ca.ilianokokoro.umihi.music.core.playback.hourOfDay
import ca.ilianokokoro.umihi.music.core.playback.playCompletionRatio
import ca.ilianokokoro.umihi.music.models.PlayEvent
import java.time.ZoneId

/** sourceContext stored for a radio queue, which has no collection name of its own. */
const val PLAY_EVENT_SOURCE_RADIO = "radio"

/**
 * Builds one [PlayEvent] per play by watching the player callbacks the service forwards to it.
 * The effective listening time comes from a [ListenStopwatch] that only runs while `isPlaying`
 * is true, so pauses and seeks never inflate it. The tracker holds no coroutine and touches no
 * storage: every method returns the finalized event (or null) and the caller persists it, which
 * keeps this class synchronous and unit-testable. The clock is always injected.
 */
class PlayEventTracker(
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private data class ActivePlay(
        val youtubeId: String,
        val startedAtMs: Long,
        val stopwatch: ListenStopwatch,
        val trackDurationMs: Long,
        val audioOutput: String?,
        val sourceContext: String?,
    )

    @Volatile
    private var active: ActivePlay? = null

    /**
     * A new media item became current: finalizes the previous play (if any) and opens a session
     * for the new one. Returns the finalized event of the *previous* track, or null.
     */
    fun onMediaItemTransition(
        youtubeId: String?,
        isPlaying: Boolean,
        nowMs: Long,
        audioOutput: String?,
        sourceContext: String?,
    ): PlayEvent? {
        val finished = finalize(nowMs) { listened, duration -> classifyPlayEnd(listened, duration) }
        active = if (youtubeId.isNullOrBlank()) {
            null
        } else {
            ActivePlay(
                youtubeId = youtubeId,
                startedAtMs = nowMs,
                stopwatch = ListenStopwatch().let { if (isPlaying) it.started(nowMs) else it },
                trackDurationMs = 0L,
                audioOutput = audioOutput,
                sourceContext = sourceContext,
            )
        }
        return finished
    }

    /** Starts/stops the effective-listening counter with the player's isPlaying state. */
    fun onIsPlayingChanged(isPlaying: Boolean, nowMs: Long) {
        val current = active ?: return
        active = current.copy(
            stopwatch = if (isPlaying) {
                current.stopwatch.started(nowMs)
            } else {
                current.stopwatch.stopped(nowMs)
            }
        )
    }

    /** The player resolved the current item's duration (usually at STATE_READY). */
    fun onDurationKnown(durationMs: Long) {
        if (durationMs <= 0L) return
        val current = active ?: return
        active = current.copy(trackDurationMs = durationMs)
    }

    /** The queue played out (STATE_ENDED): finalizes the current play. */
    fun onPlaybackEnded(nowMs: Long): PlayEvent? {
        val finished = finalize(nowMs) { listened, duration -> classifyPlayEnd(listened, duration) }
        active = null
        return finished
    }

    /** The service is dying: finalizes with the APP_KILLED classification. */
    fun onServiceDestroyed(nowMs: Long): PlayEvent? {
        val finished = finalize(nowMs, ::classifyPlayEndOnServiceDeath)
        active = null
        return finished
    }

    private inline fun finalize(
        nowMs: Long,
        classify: (listenedMs: Long, durationMs: Long) -> PlayEndReason,
    ): PlayEvent? {
        val current = active ?: return null
        val listenedMs = current.stopwatch.totalMs(nowMs)
        // Never-started plays (instant skips during errors or queue rebuilds) carry no listening
        // signal and would drown the real ones, so they are not recorded at all.
        if (listenedMs <= 0L) return null
        return PlayEvent(
            youtubeId = current.youtubeId,
            playedAt = current.startedAtMs,
            durationListenedMs = listenedMs,
            trackDurationMs = current.trackDurationMs,
            completionRatio = playCompletionRatio(listenedMs, current.trackDurationMs),
            endReason = classify(listenedMs, current.trackDurationMs).name,
            hourOfDay = hourOfDay(current.startedAtMs, zone),
            dayOfWeek = dayOfWeekIso(current.startedAtMs, zone),
            audioOutput = current.audioOutput,
            sourceContext = current.sourceContext,
        )
    }
}
