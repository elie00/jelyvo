package ca.ilianokokoro.umihi.music.desktop.playback

import ca.ilianokokoro.umihi.music.core.domain.PlaybackEngine
import ca.ilianokokoro.umihi.music.core.domain.PlaybackProgress
import ca.ilianokokoro.umihi.music.core.domain.PlaybackQueue
import ca.ilianokokoro.umihi.music.core.domain.PlaybackState
import ca.ilianokokoro.umihi.music.core.domain.PlaybackStatus
import ca.ilianokokoro.umihi.music.core.domain.QueueEntry
import ca.ilianokokoro.umihi.music.core.domain.RepeatMode
import ca.ilianokokoro.umihi.music.core.domain.StreamResolver
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.domain.relativeSeekTarget
import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * macOS playback over JavaFX Media.
 *
 * It only handles YouTube's muxed progressive rendition: the audio-only ones are fragmented MP4,
 * which JavaFX misparses badly enough to report twice the real duration and stop after the first
 * fragment. The resolver is configured accordingly, and the stream then plays straight from its
 * URL with a correct timeline.
 *
 * Crossfade, gapless and Now Playing are deliberately absent until an AVFoundation bridge is
 * evaluated; advancing through the queue reopens a player per track.
 */
class JavaFxAudioEngine(
    private val resolver: StreamResolver,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : PlaybackEngine {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queue = MutableStateFlow(PlaybackQueue())
    override val queue: StateFlow<PlaybackQueue> = _queue.asStateFlow()

    private var player: MediaPlayer? = null

    /** Guards against a stale track finishing after the user moved on and auto-advancing past it. */
    private var generation = 0

    override suspend fun load(queue: PlaybackQueue, startIndex: Int) {
        _queue.value = queue.copy(currentIndex = startIndex)
        openCurrent()
    }

    override fun enqueue(track: Track) {
        val current = _queue.value
        _queue.value = current.copy(
            entries = current.entries + QueueEntry(entryId = entryId(track, current.entries.size), track = track),
        )
    }

    override fun play() = onFxThread { player?.play() }

    override fun pause() = onFxThread { player?.pause() }

    override fun seekTo(positionMs: Long) =
        onFxThread { player?.seek(Duration.millis(positionMs.toDouble())) }

    override fun seekBy(deltaMs: Long) = onFxThread {
        val current = player ?: return@onFxThread
        val target = relativeSeekTarget(
            positionMs = current.currentTime.toMillis().toLong(),
            durationMs = durationMs(current),
            deltaMs = deltaMs,
        )
        current.seek(Duration.millis(target.toDouble()))
    }

    override fun seekToIndex(index: Int) {
        if (index !in _queue.value.entries.indices) return
        _queue.value = _queue.value.copy(currentIndex = index)
        scope.launch { openCurrent() }
    }

    override fun skipNext() {
        val next = _queue.value.currentIndex + 1
        if (next in _queue.value.entries.indices) seekToIndex(next)
    }

    /** Restarts the track first, as players conventionally do, and only then steps back. */
    override fun skipPrevious() {
        val position = player?.currentTime?.toMillis()?.toLong() ?: 0L
        if (position > RESTART_THRESHOLD_MS) {
            seekTo(0)
            return
        }
        val previous = _queue.value.currentIndex - 1
        if (previous in _queue.value.entries.indices) seekToIndex(previous)
    }

    override fun setRepeatMode(mode: RepeatMode) {
        _state.value = _state.value.copy(repeatMode = mode)
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(shuffleEnabled = enabled)
    }

    override suspend fun progress(): PlaybackProgress? = snapshotProgress()

    /** Non-suspending reader for the UI's polling loop; JavaFX exposes no flow of its playhead. */
    fun snapshotProgress(): PlaybackProgress? {
        val current = player ?: return null
        val duration = durationMs(current)
        if (duration <= 0L) return null
        return PlaybackProgress(
            positionMs = current.currentTime.toMillis().toLong(),
            durationMs = duration,
        )
    }

    fun release() = onFxThread { player?.dispose() }

    private suspend fun openCurrent() {
        val entry = _queue.value.current ?: return
        val opening = ++generation

        _state.value = _state.value.copy(
            status = PlaybackStatus.BUFFERING,
            currentTrack = entry.track,
            currentIndex = _queue.value.currentIndex,
            isPlaying = false,
        )

        val url = runCatching { resolver.resolve(entry.track).url }
            .getOrElse {
                System.err.println("Stream resolution failed for ${entry.track.id}: $it")
                _state.value = _state.value.copy(status = PlaybackStatus.IDLE)
                return
            }

        if (opening != generation) return

        onFxThread {
            // Detach before disposing: a retired player still fires onPaused/onEndOfMedia, and
            // those handlers would otherwise write over the state of the track replacing it.
            player?.let { retired ->
                retired.onReady = null
                retired.onPlaying = null
                retired.onPaused = null
                retired.onEndOfMedia = null
                retired.onError = null
                retired.stop()
                retired.dispose()
            }
            val created = MediaPlayer(Media(url))
            created.setOnReady { _state.value = _state.value.copy(status = PlaybackStatus.READY) }
            created.setOnPlaying {
                _state.value = _state.value.copy(isPlaying = true, status = PlaybackStatus.READY)
            }
            created.setOnPaused { _state.value = _state.value.copy(isPlaying = false) }
            created.setOnEndOfMedia { onTrackEnded(opening) }
            created.setOnError {
                _state.value = _state.value.copy(isPlaying = false, status = PlaybackStatus.IDLE)
                System.err.println("JavaFX media error: ${created.error}")
            }
            player = created
            created.play()
        }
    }

    private fun onTrackEnded(opening: Int) {
        if (opening != generation) return
        _state.value = _state.value.copy(isPlaying = false, status = PlaybackStatus.ENDED)

        when (_state.value.repeatMode) {
            RepeatMode.ONE -> seekToIndex(_queue.value.currentIndex)
            RepeatMode.ALL -> {
                val next = _queue.value.currentIndex + 1
                seekToIndex(if (next in _queue.value.entries.indices) next else 0)
            }

            RepeatMode.OFF -> skipNext()
        }
    }

    private fun durationMs(current: MediaPlayer): Long =
        current.totalDuration?.toMillis()?.toLong()?.takeIf { it > 0L }
            ?: _state.value.currentTrack?.durationMs
            ?: 0L

    private companion object {
        /** Below this, "previous" means the previous track rather than the start of this one. */
        const val RESTART_THRESHOLD_MS = 3_000L

        private val started = AtomicBoolean(false)

        /** Queue-slot identity: the same track can sit in several slots, so the index takes part. */
        fun entryId(track: Track, index: Int): String = "${track.id}#$index"

        /**
         * JavaFX owns its own toolkit thread and every player call must land on it. The toolkit is
         * started once, with implicit exit off so closing a media player never tears it down.
         */
        fun onFxThread(block: () -> Unit) {
            if (started.compareAndSet(false, true)) {
                Platform.startup {}
                Platform.setImplicitExit(false)
            }
            Platform.runLater(block)
        }
    }
}
