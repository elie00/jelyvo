package ca.ilianokokoro.umihi.music.core.scrobbling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Connects the player callbacks to a [ScrobbleTracker] and owns the one timer that fires when the
 * current play earns its listen. Player callbacks arrive on the main thread and the timer on a
 * background one, so every touch of the tracker holds [lock].
 */
class ScrobbleCoordinator(
    private val scope: CoroutineScope,
    private val delivery: ScrobbleDelivery,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val tracker = ScrobbleTracker()
    private var timer: Job? = null

    /** Sends anything left over from an earlier run. */
    fun start() {
        scope.launch { delivery.flush() }
    }

    fun onTrackChanged(track: ScrobbleTrack?, isPlaying: Boolean) = synchronized(lock) {
        tracker.onTrackChanged(track, isPlaying, now())
        sync()
    }

    fun onIsPlayingChanged(isPlaying: Boolean) = synchronized(lock) {
        tracker.onIsPlayingChanged(isPlaying, now())
        sync()
    }

    fun onDurationKnown(durationMs: Long) = synchronized(lock) {
        tracker.onDurationKnown(durationMs)
        sync()
    }

    /** Must be called with [lock] held. */
    private fun sync() {
        val nowMs = now()
        tracker.takeNowPlaying()?.let { track -> scope.launch { delivery.announce(track) } }
        tracker.takeDueListen(nowMs)?.let { track -> scope.launch { delivery.record(track) } }

        timer?.cancel()
        timer = tracker.millisUntilDue(nowMs)?.let { waitMs ->
            scope.launch {
                delay(waitMs)
                synchronized(lock) { sync() }
            }
        }
    }
}
