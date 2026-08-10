package ca.ilianokokoro.umihi.music.desktop.playback

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * First macOS playback engine: JavaFX Media on the JVM.
 *
 * Chosen to get sound out without native interop. It only handles YouTube's muxed progressive
 * rendition: the audio-only ones are fragmented MP4, which JavaFX misparses badly enough to report
 * twice the real duration and stop after the first fragment. The resolver is configured
 * accordingly, and the stream then plays straight from its URL with a correct timeline.
 *
 * Gapless, crossfade and Now Playing are deliberately absent until an AVFoundation bridge is
 * evaluated.
 */
class JavaFxAudioEngine(
    private val resolver: StreamResolver,
) {
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queue = MutableStateFlow(PlaybackQueue())
    val queue: StateFlow<PlaybackQueue> = _queue.asStateFlow()

    private var player: MediaPlayer? = null

    /** Resolves [track]'s stream and starts it, replacing whatever was playing. */
    suspend fun playNow(track: Track) {
        _state.value = _state.value.copy(
            status = PlaybackStatus.BUFFERING,
            currentTrack = track,
            currentIndex = 0,
            isPlaying = false,
        )
        _queue.value = PlaybackQueue(
            entries = listOf(QueueEntry(entryId = track.id, track = track)),
            currentIndex = 0,
        )

        val url = resolver.resolve(track).url
        onFxThread {
            player?.dispose()
            val created = MediaPlayer(Media(url))
            created.setOnReady {
                _state.value = _state.value.copy(status = PlaybackStatus.READY)
            }
            created.setOnPlaying {
                _state.value = _state.value.copy(isPlaying = true, status = PlaybackStatus.READY)
            }
            created.setOnPaused { _state.value = _state.value.copy(isPlaying = false) }
            created.setOnEndOfMedia {
                _state.value = _state.value.copy(isPlaying = false, status = PlaybackStatus.ENDED)
            }
            created.setOnError {
                _state.value = _state.value.copy(
                    isPlaying = false,
                    status = PlaybackStatus.IDLE,
                )
                System.err.println("JavaFX media error: ${created.error}")
            }
            player = created
            created.play()
        }
    }

    fun play() = onFxThread { player?.play() }

    fun pause() = onFxThread { player?.pause() }

    fun seekTo(positionMs: Long) = onFxThread { player?.seek(Duration.millis(positionMs.toDouble())) }

    fun seekBy(deltaMs: Long) = onFxThread {
        val current = player ?: return@onFxThread
        val target = relativeSeekTarget(
            positionMs = current.currentTime.toMillis().toLong(),
            durationMs = durationMs(current),
            deltaMs = deltaMs,
        )
        current.seek(Duration.millis(target.toDouble()))
    }

    fun progress(): PlaybackProgress? {
        val current = player ?: return null
        val duration = durationMs(current)
        if (duration <= 0L) return null
        return PlaybackProgress(
            positionMs = current.currentTime.toMillis().toLong(),
            durationMs = duration,
        )
    }

    /** JavaFX knows the length once the media is ready; until then the search metadata stands in. */
    private fun durationMs(current: MediaPlayer): Long =
        current.totalDuration?.toMillis()?.toLong()?.takeIf { it > 0L }
            ?: _state.value.currentTrack?.durationMs
            ?: 0L

    fun setRepeatMode(mode: RepeatMode) {
        _state.value = _state.value.copy(repeatMode = mode)
        onFxThread {
            player?.cycleCount = if (mode == RepeatMode.ONE) MediaPlayer.INDEFINITE else 1
        }
    }

    fun release() = onFxThread { player?.dispose() }

    private companion object {
        private val started = AtomicBoolean(false)

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
