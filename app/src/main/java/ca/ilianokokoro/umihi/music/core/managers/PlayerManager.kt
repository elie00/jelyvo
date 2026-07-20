package ca.ilianokokoro.umihi.music.core.managers

import android.content.ComponentName
import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.data.repositories.RadioRepository
import ca.ilianokokoro.umihi.music.extensions.toSong
import ca.ilianokokoro.umihi.music.models.PlaybackAudioInfo
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.services.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * A-B repeat segment. [pointBMs] is null while only A has been placed; the loop is active
 * (playback jumps from B back to A) once both points are set.
 */
data class AbLoop(
    val pointAMs: Long,
    val pointBMs: Long? = null,
)

object PlayerManager {
    @Volatile
    private var controllerFuture: ListenableFuture<MediaController>? = null

    @Volatile
    private var controller: MediaController? = null

    private val _controllerState = MutableStateFlow<MediaController?>(null)
    val controllerState: StateFlow<MediaController?> = _controllerState.asStateFlow()

    val currentController: MediaController?
        get() = controller?.takeIf { it.isConnected }

    private val isConnected: Boolean
        get() = controller?.isConnected == true


    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val radioRepository = RadioRepository()

    private var sleepTimerJob: Job? = null
    private var sleepTimerEndOfSongListener: Player.Listener? = null

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Long?> = _sleepTimerRemainingSeconds.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _abLoop = MutableStateFlow<AbLoop?>(null)
    val abLoop: StateFlow<AbLoop?> = _abLoop.asStateFlow()

    private val _audioInfo = MutableStateFlow(PlaybackAudioInfo())
    val audioInfo = _audioInfo.asStateFlow()

    /**
     * True when the current queue is a self-contained collection (album, artist page, playlist):
     * playback must stop at its end instead of being extended with radio/autoplay suggestions.
     * Cleared whenever the queue is rebuilt around a single song or an explicit radio.
     */
    @Volatile
    var isCollectionQueue: Boolean = false
        private set

    fun updatePlaybackInfo(info: PlaybackAudioInfo) {
        _audioInfo.value = info
    }

    @Synchronized
    fun connectController(context: Context) {
        if (isConnected) {
            _controllerState.value = controller
            return
        }

        if (controllerFuture != null) {
            return
        }

        clearDeadController()

        val appContext = context.applicationContext

        val sessionToken = SessionToken(
            appContext,
            ComponentName(appContext, PlaybackService::class.java)
        )

        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                try {
                    val built = future.get()

                    synchronized(this@PlayerManager) {
                        controller = built
                        _controllerState.value = built
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    clearDeadController()
                } finally {
                    synchronized(this@PlayerManager) {
                        controllerFuture = null
                    }
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    @Synchronized
    fun disconnectController() {
        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }

        controllerFuture = null

        controller?.release()
        clearDeadController()
    }

    val playbackState: Int
        get() = currentController?.playbackState ?: Player.STATE_IDLE

    val isPlaying: Boolean
        get() = currentController?.isPlaying == true

    val repeatMode: Int
        get() = currentController?.repeatMode ?: Player.REPEAT_MODE_OFF

    val shuffleModeEnabled: Boolean
        get() = currentController?.shuffleModeEnabled == true

    fun seekToIndex(index: Int, positionMs: Long = C.TIME_UNSET) {
        currentController?.seekTo(index, positionMs)
    }

    fun skipToNext() {
        val controller = currentController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNext()
            return
        }
        // A collection queue (album, artist, playlist) ends where the collection ends.
        if (isCollectionQueue) {
            return
        }
        // No next track (e.g. a single-song queue): build a radio around the current song.
        val seed = controller.currentMediaItem?.toSong() ?: return
        scope.launch {
            val related = radioRepository.getRelated(seed)
                .firstOrNull { it is ApiResult.Success } as? ApiResult.Success
                ?: return@launch
            if (related.data.isEmpty()) {
                return@launch
            }
            withContext(Dispatchers.Main) {
                val c = currentController ?: return@withContext
                if (!c.hasNextMediaItem() && c.currentMediaItem?.mediaId == seed.youtubeId) {
                    c.addMediaItems(related.data.map { it.apply { isSuggestion = true }.mediaItem })
                    c.seekToNext()
                }
            }
        }
    }

    fun skipToPrevious() {
        currentController?.seekToPrevious()
    }

    fun cycleRepeatMode() {
        val nextRepeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
            else -> Player.REPEAT_MODE_OFF
        }

        setRepeatMode(nextRepeatMode)
    }

    fun toggleShuffleMode() {
        currentController?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }


    fun playMediaItem(mediaItem: MediaItem) {
        currentController?.run {
            isCollectionQueue = false
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }


    fun playPlaylist(playlist: Playlist, index: Int = 0) {
        val controller = currentController ?: return
        val mediaItems = playlist.mediaItems

        if (mediaItems.isEmpty()) {
            return
        }

        isCollectionQueue = true
        controller.setMediaItems(
            mediaItems,
            index.coerceIn(0, mediaItems.lastIndex),
            C.TIME_UNSET
        )

        controller.prepare()
        controller.play()
    }

    fun shufflePlaylist(playlist: Playlist) {
        val shuffledPlaylist = playlist.copy(
            songs = playlist.songs.shuffled()
        )

        playPlaylist(shuffledPlaylist)
    }


    fun playQueue(
        mediaItems: List<MediaItem>,
        startIndex: Int = 0,
        startPositionMs: Long = 0L
    ) {
        if (mediaItems.isEmpty()) {
            return
        }

        currentController?.run {
            isCollectionQueue = true
            setMediaItems(
                mediaItems,
                startIndex.coerceIn(0, mediaItems.lastIndex),
                startPositionMs
            )
            prepare()
            play()
        }
    }

    fun playSong(song: Song) {
        val controller = currentController ?: return

        isCollectionQueue = false
        controller.setMediaItem(song.mediaItem)
        controller.prepare()
        controller.play()
    }

    /**
     * Resumes a partially listened title: plays [song] starting at [startPositionMs]. If the saved
     * position sits within the final 5% of the track it restarts from the beginning instead.
     */
    fun resumeSong(song: Song, startPositionMs: Long, durationMs: Long) {
        val controller = currentController ?: return

        val nearEnd = durationMs > 0 && startPositionMs >= durationMs - (durationMs / 20)
        val position = if (nearEnd) C.TIME_UNSET else startPositionMs.coerceAtLeast(0L)

        isCollectionQueue = false
        controller.setMediaItem(song.mediaItem, position)
        controller.prepare()
        controller.play()
    }

    /**
     * "Start radio" from a seed song: plays it immediately, then appends related tracks so the
     * user gets an endless mix built around that song. A failed fetch just leaves the seed playing.
     */
    fun startRadio(song: Song, context: Context? = null) {
        playSong(song)

        context?.let {
            Toast.makeText(
                it,
                it.getString(R.string.starting_radio),
                Toast.LENGTH_SHORT
            ).show()
        }

        scope.launch {
            val related = radioRepository.getRelated(song)
                .firstOrNull { it is ApiResult.Success } as? ApiResult.Success
                ?: return@launch
            val songs = related.data
            if (songs.isEmpty()) {
                return@launch
            }

            withContext(Dispatchers.Main) {
                val controller = currentController ?: return@withContext
                // Only extend if the seed is still what we started (user hasn't moved on).
                if (controller.currentMediaItem?.mediaId == song.youtubeId) {
                    controller.addMediaItems(songs.map { it.mediaItem })
                }
            }
        }
    }

    suspend fun getPlaybackPosition(): Pair<Float, Float>? {
        return withContext(Dispatchers.Main.immediate) {
            val controller = currentController ?: return@withContext null
            val positionMs = controller.currentPosition
            val durationMs = controller.duration
            if (positionMs <= 0 || durationMs <= 0) {
                return@withContext null
            }
            (positionMs / 1000f) to (durationMs / 1000f)
        }
    }

    fun addNext(song: Song, context: Context? = null) {
        val controller = currentController ?: return

        val insertIndex = if (controller.mediaItemCount == 0) {
            0
        } else {
            (controller.currentMediaItemIndex + 1)
                .coerceIn(0, controller.mediaItemCount)
        }

        controller.addMediaItem(insertIndex, song.mediaItem)

        context?.let {
            Toast.makeText(
                it,
                it.getString(R.string.play_next_toast),
                Toast.LENGTH_SHORT
            ).show()
        }

        playIfFirstQueueItem()
    }

    fun addToQueue(song: Song, context: Context? = null) {
        val controller = currentController ?: return

        controller.addMediaItem(controller.mediaItemCount, song.mediaItem)

        context?.let {
            Toast.makeText(
                it,
                it.getString(R.string.added_queue_toast),
                Toast.LENGTH_SHORT
            ).show()
        }

        playIfFirstQueueItem()
    }

    fun removeMediaItem(index: Int) {
        val controller = currentController ?: return

        if (index in 0 until controller.mediaItemCount) {
            controller.removeMediaItem(index)
        }
    }

    fun clearQueue() {
        currentController?.run {
            stop()
            clearMediaItems()
        }
    }

    @Synchronized
    fun getQueue(): List<Song> {
        val controller = currentController ?: return emptyList()
        val queue = mutableListOf<Song>()

        for (i in 0 until controller.mediaItemCount) {
            queue.add(controller.getMediaItemAt(i).toSong())
        }

        return queue
    }

    @Synchronized
    fun getCurrentSong(): Song? {
        return currentController?.currentMediaItem?.toSong()
    }

    @Synchronized
    fun getCurrentIndex(): Int {
        return currentController?.currentMediaItemIndex ?: C.INDEX_UNSET
    }

    @OptIn(UnstableApi::class)
    fun setAudioOffloadEnabled(value: Boolean) {
        val controller = currentController ?: return

        val mode = if (value) {
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
        } else {
            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        }

        controller.trackSelectionParameters =
            controller.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(
                    controller.trackSelectionParameters.audioOffloadPreferences
                        .buildUpon()
                        .setAudioOffloadMode(mode)
                        .build()
                )
                .build()
    }

    private fun setRepeatMode(repeatMode: Int) {
        currentController?.repeatMode = repeatMode
    }


    fun startSleepTimer(minutes: Int) {
        startSleepTimerRemaining(minutes * 60L)
    }

    fun startSleepTimerEndOfSong() {
        cancelSleepTimer()

        val controller = currentController ?: return

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                if (
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                ) {
                    controller.pause()
                    cancelSleepTimer()
                }
            }
        }

        controller.addListener(listener)
        sleepTimerEndOfSongListener = listener

        sleepTimerJob = scope.launch {
            while (isActive) {
                val remainingSeconds =
                    withContext(Dispatchers.Main.immediate) {
                        val durationMs = controller.duration
                        val positionMs = controller.currentPosition

                        if (
                            durationMs > 0
                        ) {
                            ((durationMs - positionMs) / 1000)
                                .coerceAtLeast(0)
                        } else {
                            -1L
                        }
                    }

                _sleepTimerRemainingSeconds.value = remainingSeconds

                delay(
                    if (remainingSeconds >= 0) {
                        250.milliseconds
                    } else {
                        1.seconds
                    }
                )
            }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        currentController?.setPlaybackSpeed(speed)
        _playbackSpeed.value = speed
    }

    /**
     * Cycles the A-B repeat segment: 1st call places A at the current position, 2nd places B,
     * 3rd clears it. If B would land before A the two points are swapped; an identical position
     * is ignored so the loop always keeps a positive length.
     */
    fun toggleAbLoop() {
        val controller = currentController ?: return
        val positionMs = controller.currentPosition.coerceAtLeast(0)
        val current = _abLoop.value

        _abLoop.value = when {
            current == null -> AbLoop(pointAMs = positionMs)
            current.pointBMs == null -> when {
                positionMs > current.pointAMs -> current.copy(pointBMs = positionMs)
                positionMs < current.pointAMs -> AbLoop(
                    pointAMs = positionMs,
                    pointBMs = current.pointAMs
                )

                else -> current
            }

            else -> null
        }
    }

    fun clearAbLoop() {
        _abLoop.value = null
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null

        sleepTimerEndOfSongListener?.let { listener ->
            currentController?.removeListener(listener)
        }
        sleepTimerEndOfSongListener = null
        _sleepTimerRemainingSeconds.value = null
    }

    private fun startSleepTimerRemaining(remainingSeconds: Long) {
        cancelSleepTimer()

        val endTimeMillis = System.currentTimeMillis() + (remainingSeconds * 1000)

        sleepTimerJob = scope.launch {
            while (isActive) {
                val millisRemaining =
                    (endTimeMillis - System.currentTimeMillis())
                        .coerceAtLeast(0)

                val remaining =
                    (millisRemaining + 999) / 1000

                _sleepTimerRemainingSeconds.value = remaining

                if (remaining <= 0) {
                    break
                }

                delay(250.milliseconds)
            }

            _sleepTimerRemainingSeconds.value = null

            withContext(Dispatchers.Main) {
                currentController?.pause()
            }
        }
    }


    private fun playIfFirstQueueItem() {
        val controller = currentController ?: return

        if (controller.mediaItemCount == 1) {
            controller.prepare()
            controller.play()
        }
    }

    @Synchronized
    private fun clearDeadController() {
        controller = null
        _controllerState.value = null
    }
}