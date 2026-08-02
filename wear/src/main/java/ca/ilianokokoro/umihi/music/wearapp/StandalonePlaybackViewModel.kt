package ca.ilianokokoro.umihi.music.wearapp

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.work.WorkInfo
import androidx.work.WorkManager
import ca.ilianokokoro.umihi.music.wear.WearPlaybackSnapshot
import ca.ilianokokoro.umihi.music.wear.WearQueueItem
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
    val homeSongs: List<WatchSong> = emptyList(),
    val homeLoading: Boolean = true,
    val favorites: List<WatchSong> = emptyList(),
    val recents: List<WatchSong> = emptyList(),
    val downloads: List<WatchSong> = emptyList(),
    val downloadingIds: Set<String> = emptySet(),
    val queue: List<WatchSong> = emptyList(),
    val currentSong: WatchSong? = null,
    val currentIndex: Int = -1,
    val queueSize: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val smartDownloadLimit: Int = 0,
    val wifiOnly: Boolean = true,
    val restrictedMode: Boolean = false,
    val error: String? = null,
) {
    val currentIsFavorite: Boolean
        get() = currentSong?.videoId in favorites.map(WatchSong::videoId)
    val currentIsDownloaded: Boolean
        get() = currentSong?.videoId in downloads.map(WatchSong::videoId)
}

class StandalonePlaybackViewModel(application: Application) : AndroidViewModel(application) {
    private val youtubeClient = WatchYoutubeClient()
    private val library = WatchLibraryStore(application)
    private val workManager = WorkManager.getInstance(application)
    private val tileCache = WearPlaybackCache(application)
    private val _state = MutableStateFlow(
        StandaloneUiState(
            favorites = library.favorites(),
            recents = library.recents(),
            downloads = library.downloads(),
            smartDownloadLimit = library.smartDownloadLimit,
            wifiOnly = library.wifiOnly,
            restrictedMode = library.restrictedMode,
        ),
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
        refreshHome()
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
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val activeDownloads = runCatching {
                    workManager.getWorkInfosByTag(WatchDownloadWorker.TAG).get()
                        .filter { !it.state.isFinished }
                        .mapNotNull { info ->
                            info.tags.firstOrNull { it.startsWith("${WatchDownloadWorker.TAG}:") }
                                ?.substringAfter(':')
                        }
                        .toSet()
                }.getOrDefault(emptySet())
                _state.update {
                    it.copy(downloads = library.downloads(), downloadingIds = activeDownloads)
                }
                delay(1_000)
            }
        }
    }

    fun refreshHome() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(homeLoading = true) }
            runCatching { youtubeClient.homeSongs(_state.value.favorites.firstOrNull()?.artist) }
                .onSuccess { songs ->
                    _state.update { state ->
                        state.copy(
                            homeSongs = songs.filterNot { state.restrictedMode && it.isExplicit },
                            homeLoading = false,
                        )
                    }
                }
                .onFailure { _state.update { it.copy(homeLoading = false) } }
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
                    _state.update { state ->
                        state.copy(
                            searching = false,
                            results = songs.filterNot { state.restrictedMode && it.isExplicit },
                        )
                    }
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

    fun playQueueIndex(index: Int) = controller?.let { player ->
        if (index in 0 until player.mediaItemCount) {
            player.seekToDefaultPosition(index)
            player.play()
        }
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

    fun toggleDownload() {
        val song = _state.value.currentSong ?: return
        toggleDownload(song)
    }

    fun toggleDownload(song: WatchSong) {
        if (song.videoId in library.downloads().map(WatchSong::videoId)) {
            library.removeDownload(song.videoId)
            _state.update { it.copy(downloads = library.downloads()) }
        } else {
            WatchDownloadWorker.enqueue(getApplication(), song, library.wifiOnly)
            _state.update { it.copy(downloadingIds = it.downloadingIds + song.videoId) }
        }
    }

    fun clearDownloads() {
        library.clearDownloads()
        _state.update { it.copy(downloads = emptyList()) }
    }

    fun setSmartDownloadLimit(limit: Int) {
        library.smartDownloadLimit = limit
        _state.update { it.copy(smartDownloadLimit = limit) }
        if (limit > 0) {
            val candidates = (_state.value.favorites + _state.value.recents + _state.value.homeSongs)
                .distinctBy(WatchSong::videoId)
                .filterNot { it.videoId in library.downloads().map(WatchSong::videoId) }
                .take((limit - library.downloads().size).coerceAtLeast(0))
            candidates.forEach { WatchDownloadWorker.enqueue(getApplication(), it, library.wifiOnly) }
        }
    }

    fun toggleWifiOnly() {
        library.wifiOnly = !library.wifiOnly
        _state.update { it.copy(wifiOnly = library.wifiOnly) }
    }

    fun toggleRestrictedMode() {
        library.restrictedMode = !library.restrictedMode
        _state.update { state ->
            state.copy(
                restrictedMode = library.restrictedMode,
                results = state.results.filterNot { library.restrictedMode && it.isExplicit },
                homeSongs = state.homeSongs.filterNot { library.restrictedMode && it.isExplicit },
            )
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun updatePlayerState(player: Player) {
        val item = player.currentMediaItem
        val currentSong = item?.toWatchSong()
        val queue = List(player.mediaItemCount) { index -> player.getMediaItemAt(index).toWatchSong() }
        _state.update { state ->
            state.copy(
                controllerReady = true,
                currentSong = currentSong,
                currentIndex = player.currentMediaItemIndex,
                queueSize = player.mediaItemCount,
                queue = queue,
                isPlaying = player.isPlaying,
                positionMs = player.currentPosition.coerceAtLeast(0),
                durationMs = player.duration.takeIf { it > 0 } ?: 0,
                repeatMode = player.repeatMode,
                shuffleEnabled = player.shuffleModeEnabled,
            )
        }
        currentSong?.let { song ->
            tileCache.save(
                WearPlaybackSnapshot(
                    mediaId = song.videoId,
                    title = song.title,
                    artist = song.artist,
                    artworkUri = song.artworkUrl,
                    positionMs = player.currentPosition.coerceAtLeast(0),
                    durationMs = player.duration.takeIf { it > 0 } ?: 0,
                    isPlaying = player.isPlaying,
                    hasPrevious = player.hasPreviousMediaItem(),
                    hasNext = player.hasNextMediaItem(),
                    queue = queue.map { WearQueueItem(it.videoId, it.title, it.artist, it.artworkUrl) },
                    queueIndex = player.currentMediaItemIndex,
                    updatedAtMs = System.currentTimeMillis(),
                ),
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
