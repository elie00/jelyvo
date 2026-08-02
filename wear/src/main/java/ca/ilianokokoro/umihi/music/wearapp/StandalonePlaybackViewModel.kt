package ca.ilianokokoro.umihi.music.wearapp

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StandaloneUiState(
    val controllerReady: Boolean = false,
    val query: String = "",
    val searching: Boolean = false,
    val searchPerformed: Boolean = false,
    val results: List<WatchSong> = emptyList(),
    val favorites: List<WatchSong> = emptyList(),
    val recents: List<WatchSong> = emptyList(),
    val currentSong: WatchSong? = null,
    val currentIndex: Int = -1,
    val queueSize: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val error: String? = null,
) {
    val currentIsFavorite: Boolean
        get() = currentSong?.videoId in favorites.map(WatchSong::videoId)
}

class StandalonePlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val youtubeClient = WatchYoutubeClient()
    private val library = WatchLibraryStore(application)
    private val _state = MutableStateFlow(
        StandaloneUiState(favorites = library.favorites(), recents = library.recents()),
    )
    val state: StateFlow<StandaloneUiState> = _state.asStateFlow()
    private var controller: MediaController? = null
    private val controllerFuture: ListenableFuture<MediaController>

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = updatePlayerState(player)

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _state.update { it.copy(error = error.cause?.message ?: error.message) }
        }
    }

    init {
        val token = SessionToken(application, ComponentName(application, StandalonePlaybackService::class.java))
        controllerFuture = MediaController.Builder(application, token).buildAsync()
        Futures.addCallback(
            controllerFuture,
            object : FutureCallback<MediaController> {
                override fun onSuccess(result: MediaController) {
                    controller = result
                    result.addListener(listener)
                    updatePlayerState(result)
                }

                override fun onFailure(t: Throwable) {
                    _state.update { it.copy(error = t.message ?: "Lecteur indisponible") }
                }
            },
            MoreExecutors.directExecutor(),
        )
        viewModelScope.launch {
            while (true) {
                controller?.let(::updatePlayerState)
                delay(500)
            }
        }
    }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value, error = null) }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isBlank() || _state.value.searching) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(searching = true, searchPerformed = true, error = null) }
            runCatching { youtubeClient.searchSongs(query) }
                .onSuccess { songs ->
                    _state.update { it.copy(searching = false, results = songs) }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(searching = false, results = emptyList(), error = failure.message)
                    }
                }
        }
    }

    fun play(song: WatchSong, source: List<WatchSong>) {
        val player = controller ?: return
        val queue = source.ifEmpty { listOf(song) }.distinctBy(WatchSong::videoId)
        val index = queue.indexOfFirst { it.videoId == song.videoId }.coerceAtLeast(0)
        player.setMediaItems(queue.map(WatchSong::toMediaItem), index, 0L)
        player.prepare()
        player.play()
        library.addRecent(song)
        _state.update { it.copy(recents = library.recents(), error = null) }
    }

    fun togglePlayback() = controller?.let { if (it.isPlaying) it.pause() else it.play() }
    fun previous() = controller?.seekToPreviousMediaItem()
    fun next() = controller?.seekToNextMediaItem()
    fun seekBack() = controller?.let { it.seekTo((it.currentPosition - 10_000).coerceAtLeast(0)) }
    fun seekForward() = controller?.let {
        val target = it.currentPosition + 10_000
        it.seekTo(if (it.duration > 0) target.coerceAtMost(it.duration) else target)
    }
    fun toggleShuffle() = controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    fun cycleRepeat() = controller?.let {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleFavorite() {
        val song = _state.value.currentSong ?: return
        library.toggleFavorite(song)
        _state.update { it.copy(favorites = library.favorites()) }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun updatePlayerState(player: Player) {
        val item = player.currentMediaItem
        _state.update { state ->
            state.copy(
                controllerReady = true,
                currentSong = item?.toWatchSong(),
                currentIndex = player.currentMediaItemIndex,
                queueSize = player.mediaItemCount,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { it > 0 } ?: 0,
                repeatMode = player.repeatMode,
                shuffleEnabled = player.shuffleModeEnabled,
            )
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(controllerFuture)
    }
}

private fun MediaItem.toWatchSong(): WatchSong = WatchSong(
    videoId = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.artist?.toString().orEmpty(),
    artworkUrl = mediaMetadata.artworkUri?.toString().orEmpty(),
)
