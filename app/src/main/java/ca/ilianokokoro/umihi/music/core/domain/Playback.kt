package ca.ilianokokoro.umihi.music.core.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-neutral playback contract.
 *
 * Screens observe [state]/[queue] and issue transport commands without knowing about Media3.
 * Adapters (`Media3PlaybackEngine` on Android, later macOS/Web engines) own stream resolution,
 * prefetching, error recovery and system-metadata publication behind this interface.
 */
interface PlaybackEngine {
    val state: StateFlow<PlaybackState>
    val queue: StateFlow<PlaybackQueue>

    suspend fun load(queue: PlaybackQueue, startIndex: Int)
    fun enqueue(track: Track)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekToIndex(index: Int)
    fun skipNext()
    fun skipPrevious()
    fun setRepeatMode(mode: RepeatMode)
    fun setShuffleEnabled(enabled: Boolean)

    /** Snapshot of the moving playhead; null while nothing is loaded or duration is unknown. */
    suspend fun progress(): PlaybackProgress?
}

enum class PlaybackStatus { IDLE, BUFFERING, READY, ENDED }

enum class RepeatMode { OFF, ALL, ONE }

data class PlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val isPlaying: Boolean = false,
    val currentTrack: Track? = null,
    val currentIndex: Int = -1,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
)

/**
 * One slot of the playback queue. [entryId] is the stable queue-slot identity: the same track can
 * occupy several slots, so queue identity deliberately lives outside [Track].
 */
data class QueueEntry(
    val entryId: String,
    val track: Track,
)

data class PlaybackQueue(
    val entries: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
) {
    val current: QueueEntry? get() = entries.getOrNull(currentIndex)

    /** Entries after the current one, keeping their queue indices for seeking. */
    fun upNext(): List<IndexedValue<QueueEntry>> =
        if (currentIndex in entries.indices) entries.withIndex().drop(currentIndex + 1)
        else emptyList()
}

data class PlaybackProgress(
    val positionMs: Long,
    val durationMs: Long,
) {
    val fraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}
