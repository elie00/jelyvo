package ca.ilianokokoro.umihi.music.core.scrobbling

import ca.ilianokokoro.umihi.music.core.playback.ListenStopwatch
import kotlinx.serialization.Serializable

/** One listen as the scrobbling services see it. */
@Serializable
data class ScrobbleTrack(
    val artist: String,
    val title: String,
    val album: String? = null,
    val durationMs: Long = 0L,
    /** Wall-clock instant the play started (epoch ms). */
    val startedAtMs: Long,
)

/** The rule shared by Last.fm and ListenBrainz: half the track, or four minutes if that is sooner. */
object ScrobbleRules {
    const val MIN_TRACK_DURATION_MS = 30_000L
    const val MAX_LISTEN_REQUIRED_MS = 240_000L

    /** Effective listening needed before a track counts, or null if it can never count. */
    fun requiredListenMs(durationMs: Long): Long? =
        if (durationMs <= MIN_TRACK_DURATION_MS) {
            null
        } else {
            minOf(durationMs / 2, MAX_LISTEN_REQUIRED_MS)
        }
}

/**
 * Decides when a play becomes "now playing" and when it becomes a listen. Listening time comes
 * from a [ListenStopwatch], so pauses never count, and seeking does not shorten the requirement.
 *
 * Holds no coroutine and touches no storage, like
 * [ca.ilianokokoro.umihi.music.services.PlayEventTracker]: the caller asks what is due and owns
 * the timer and the network. The clock is always injected.
 */
class ScrobbleTracker {
    private data class ActivePlay(
        val track: ScrobbleTrack,
        val stopwatch: ListenStopwatch,
        val nowPlayingTaken: Boolean = false,
        val listenTaken: Boolean = false,
    )

    private var active: ActivePlay? = null

    /** A new item became current. Pass null when the queue is empty or the item has no metadata. */
    fun onTrackChanged(track: ScrobbleTrack?, isPlaying: Boolean, nowMs: Long) {
        active = track?.let {
            ActivePlay(
                track = it,
                stopwatch = ListenStopwatch().let { watch ->
                    if (isPlaying) watch.started(nowMs) else watch
                },
            )
        }
    }

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
        active = current.copy(track = current.track.copy(durationMs = durationMs))
    }

    /** The track to announce as now playing, once per play and only after it really started. */
    fun takeNowPlaying(): ScrobbleTrack? {
        val current = active ?: return null
        if (current.nowPlayingTaken || current.stopwatch.runningSinceMs == null) return null
        active = current.copy(nowPlayingTaken = true)
        return current.track
    }

    /** The track that has just earned its listen, once per play. */
    fun takeDueListen(nowMs: Long): ScrobbleTrack? {
        val current = active ?: return null
        if (current.listenTaken) return null
        val required = ScrobbleRules.requiredListenMs(current.track.durationMs) ?: return null
        if (current.stopwatch.totalMs(nowMs) < required) return null
        active = current.copy(listenTaken = true)
        return current.track
    }

    /**
     * Milliseconds of playback until [takeDueListen] returns a track, or null when no timer should
     * run: nothing is playing, the duration is unknown, or the listen was already taken.
     */
    fun millisUntilDue(nowMs: Long): Long? {
        val current = active ?: return null
        if (current.listenTaken || current.stopwatch.runningSinceMs == null) return null
        val required = ScrobbleRules.requiredListenMs(current.track.durationMs) ?: return null
        return (required - current.stopwatch.totalMs(nowMs)).coerceAtLeast(0L)
    }
}
