package ca.ilianokokoro.umihi.music.core.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import ca.ilianokokoro.umihi.music.core.domain.PlaybackEngine
import ca.ilianokokoro.umihi.music.core.domain.PlaybackProgress
import ca.ilianokokoro.umihi.music.core.domain.PlaybackQueue
import ca.ilianokokoro.umihi.music.core.domain.PlaybackState
import ca.ilianokokoro.umihi.music.core.domain.PlaybackStatus
import ca.ilianokokoro.umihi.music.core.domain.QueueEntry
import ca.ilianokokoro.umihi.music.core.domain.RepeatMode
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.domain.relativeSeekTarget
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.extensions.toSong
import ca.ilianokokoro.umihi.music.models.adapters.toSong
import ca.ilianokokoro.umihi.music.models.adapters.toTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android [PlaybackEngine] over the Media3 session controller.
 *
 * Skip semantics intentionally go through [PlayerManager] so radio extension and collection-queue
 * boundaries stay identical whether a screen uses the engine or the legacy manager during the
 * migration.
 */
object Media3PlaybackEngine : PlaybackEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _queue = MutableStateFlow(PlaybackQueue())
    override val queue: StateFlow<PlaybackQueue> = _queue.asStateFlow()

    private var attachedController: MediaController? = null
    private var attachedListener: Player.Listener? = null

    init {
        scope.launch {
            PlayerManager.controllerState.collect(::attach)
        }
    }

    private fun attach(controller: MediaController?) {
        attachedListener?.let { attachedController?.removeListener(it) }
        attachedController = controller
        attachedListener = null

        if (controller == null) {
            _state.value = PlaybackState()
            _queue.value = PlaybackQueue()
            return
        }

        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                refresh(controller)
            }
        }
        controller.addListener(listener)
        attachedListener = listener
        refresh(controller)
    }

    private fun refresh(controller: MediaController) {
        val songs = List(controller.mediaItemCount) { controller.getMediaItemAt(it).toSong() }
        val index = controller.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET } ?: -1

        _queue.value = PlaybackQueue(
            entries = songs.map { QueueEntry(entryId = it.uid, track = it.toTrack()) },
            currentIndex = index,
        )
        _state.value = PlaybackState(
            status = when (controller.playbackState) {
                Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
                Player.STATE_READY -> PlaybackStatus.READY
                Player.STATE_ENDED -> PlaybackStatus.ENDED
                else -> PlaybackStatus.IDLE
            },
            isPlaying = controller.isPlaying,
            currentTrack = controller.currentMediaItem?.toSong()?.toTrack(),
            currentIndex = index,
            repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
            shuffleEnabled = controller.shuffleModeEnabled,
        )
    }

    override suspend fun load(queue: PlaybackQueue, startIndex: Int) {
        withContext(Dispatchers.Main.immediate) {
            PlayerManager.playQueue(
                mediaItems = queue.entries.map { it.track.toSong().mediaItem },
                startIndex = startIndex,
            )
        }
    }

    override fun enqueue(track: Track) {
        PlayerManager.addToQueue(track.toSong())
    }

    override fun play() {
        PlayerManager.currentController?.play()
    }

    override fun pause() {
        PlayerManager.currentController?.pause()
    }

    override fun seekTo(positionMs: Long) {
        PlayerManager.currentController?.seekTo(positionMs)
    }

    override fun seekBy(deltaMs: Long) {
        val controller = PlayerManager.currentController ?: return
        controller.seekTo(
            relativeSeekTarget(
                positionMs = controller.currentPosition,
                durationMs = controller.duration,
                deltaMs = deltaMs,
            )
        )
    }

    override fun seekToIndex(index: Int) {
        PlayerManager.seekToIndex(index)
    }

    override fun skipNext() {
        PlayerManager.skipToNext()
    }

    override fun skipPrevious() {
        PlayerManager.skipToPrevious()
    }

    override fun setRepeatMode(mode: RepeatMode) {
        PlayerManager.currentController?.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        PlayerManager.currentController?.shuffleModeEnabled = enabled
    }

    override suspend fun progress(): PlaybackProgress? {
        return withContext(Dispatchers.Main.immediate) {
            val controller = PlayerManager.currentController ?: return@withContext null
            val durationMs = controller.duration
            if (durationMs <= 0L) {
                return@withContext null
            }
            PlaybackProgress(
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = durationMs,
            )
        }
    }
}
