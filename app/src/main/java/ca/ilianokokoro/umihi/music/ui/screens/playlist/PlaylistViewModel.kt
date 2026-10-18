package ca.ilianokokoro.umihi.music.ui.screens.playlist


import android.app.Application
import android.widget.Toast
import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.domain.CatalogResult
import ca.ilianokokoro.umihi.music.core.domain.MusicCatalog
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printd
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.managers.LikeManager
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.data.catalog.YoutubeMusicCatalog
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.DownloadRepository
import ca.ilianokokoro.umihi.music.data.repositories.LibraryRepository
import ca.ilianokokoro.umihi.music.data.repositories.LocalPlaylistRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlaylistRepository
import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.adapters.toPlaylist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.SongSort
import ca.ilianokokoro.umihi.music.ui.navigation.viewmodels.SharedViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

class PlaylistViewModel(
    private val playlistInfo: PlaylistInfo,
    private val sharedViewModel: SharedViewModel,
    application: Application
) :
    AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        PlaylistState(
            screenState = ScreenState.Loading(playlistInfo)
        )
    )
    val uiState = _uiState.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun showSearch() {
        _uiState.update { it.copy(showingSearch = true) }
    }

    fun hideSearch() {
        _uiState.update { it.copy(showingSearch = false, searchQuery = "") }
    }

    private val playlistRepository = PlaylistRepository(application)
    private val catalog: MusicCatalog = YoutubeMusicCatalog(
        playlistRepository = playlistRepository,
        settingsProvider = { datastoreRepository.getSettings() },
    )
    private val localPlaylistRepository = AppDatabase.getInstance(application).playlistRepository()
    private val localUserPlaylistRepository = LocalPlaylistRepository(application)
    private val datastoreRepository = DatastoreRepository(application)
    private val downloadRepository = DownloadRepository(application)

    init {
        observeSongDownloads()
        observeLikeChanges()
        viewModelScope.launch {
            getPlaylistInfoAsync()
            observerDownloadJob()
        }
    }

    /**
     * The automatic liked-songs playlist is mutable through every track menu and the player.
     * Reflect those mutations in the current list instead of making the user leave and reopen it.
     */
    private fun observeLikeChanges() {
        if (!playlistInfo.isLikedSongsPlaylist) {
            return
        }
        viewModelScope.launch {
            LikeManager.changes.collect {
                _uiState.update { currentState ->
                    val success = currentState.screenState as? ScreenState.Success
                        ?: return@update currentState
                    currentState.copy(
                        screenState = success.copy(
                            playlist = success.playlist.copy(
                                songs = LikeManager.mergeLikedSongs(success.playlist.songs)
                            )
                        )
                    )
                }
            }
        }
    }

    private fun observeSongDownloads() {
        viewModelScope.launch {
            localPlaylistRepository.observePlaylistById(playlistInfo.id).collect { localPlaylist ->
                if (localPlaylist != null) {
                    _uiState.update { currentState ->
                        val screenState = currentState.screenState
                        if (screenState is ScreenState.Success) {
                            currentState.copy(
                                screenState = screenState.copy(
                                    playlist = updatePlaylistFrom(
                                        screenState.playlist,
                                        localPlaylist
                                    )
                                )
                            )
                        } else {
                            currentState
                        }
                    }
                }
            }

        }
    }

    suspend fun observerDownloadJob() {
        val playlist = getPlaylist() ?: return
        val existingJobFlow = downloadRepository.getExistingJobFlow(playlist)

        existingJobFlow.collect { workInfos ->
            val workInfo = workInfos.firstOrNull() ?: return@collect

            _uiState.update {
                it.copy(
                    isDownloading =
                        workInfo.state == WorkInfo.State.ENQUEUED ||
                                workInfo.state == WorkInfo.State.RUNNING ||
                                workInfo.state == WorkInfo.State.BLOCKED
                )
            }

            when (workInfo.state) {
                WorkInfo.State.SUCCEEDED -> {
                    printd("Download finished for ${playlist.info.title}")
                }

                WorkInfo.State.FAILED,
                WorkInfo.State.CANCELLED -> {
                    printd("Download failed or cancelled for ${playlist.info.title}")
                }

                else -> {}
            }
        }
    }

    fun refreshPlaylistInfo() {
        viewModelScope.launch {
            _uiState.update {
                _uiState.value.copy(
                    isRefreshing = true
                )
            }
            getPlaylistInfoAsync(useCache = false)
            _uiState.update {
                _uiState.value.copy(
                    isRefreshing = false
                )
            }
        }

    }

    fun getPlaylistInfo() {
        viewModelScope.launch {
            getPlaylistInfoAsync(useCache = false)
        }
    }

    fun onSortChange(sort: SongSort) {
        _uiState.update { it.copy(sort = sort) }
    }

    fun playPlaylist(startingSong: Song? = null) {
        val playlist = getPlaylist() ?: return
        // Queue what the list shows, not the whole playlist: filtering or sorting and then hitting
        // play must not bring the hidden rows back.
        val songs = _uiState.value.visibleSongs
        if (songs.isEmpty()) {
            return
        }
        viewModelScope.launch {
            PlayerManager.playPlaylist(
                playlist.copy(songs = songs),
                startingSong?.let { songs.indexOf(it) }?.coerceAtLeast(0) ?: 0
            )
        }
    }

    fun shufflePlaylist() {
        val playlist = getPlaylist() ?: return
        val songs = _uiState.value.visibleSongs
        if (songs.isEmpty()) {
            return
        }
        viewModelScope.launch {
            PlayerManager.shufflePlaylist(playlist.copy(songs = songs))
        }
    }

    fun downloadPlaylist() {
        val playlist = getPlaylist() ?: return
        viewModelScope.launch {
            if (playlist.downloaded) {
                return@launch
            }

            val settings = datastoreRepository.getSettings()
            downloadRepository.downloadPlaylist(playlist, settings.downloadOnMetered)
        }
    }

    private fun showToast(@StringRes messageRes: Int, vararg formatArgs: Any) {
        val app = getApplication<Application>()
        Toast.makeText(app, app.getString(messageRes, *formatArgs), Toast.LENGTH_SHORT).show()
    }

    fun deletePlaylist(onBack: () -> Unit) {
        viewModelScope.launch {
            try {
                val settings = datastoreRepository.getSettings()
                if (settings.cookies.isEmpty()) {
                    throw Exception("Failed to get to login cookies")
                }

                playlistRepository.delete(playlistInfo, settings)
                    .collect { apiResult ->
                        when (apiResult) {
                            // A failed deletion must not cost the user the playlist they were
                            // looking at: keep the loaded screen and just report the failure.
                            is ApiResult.Error -> showToast(R.string.delete_playlist_failed_toast)
                            ApiResult.Loading -> Unit
                            is ApiResult.Success -> {
                                onBack()
                                sharedViewModel.markPlaylistDeleted(playlistInfo)
                            }
                        }
                    }

            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (ex: Exception) {
                printe(message = ex.toString(), exception = ex)
                showToast(R.string.delete_playlist_failed_toast)
            }
        }
    }

    fun deleteLocalPlaylist() {
        val playlist = getPlaylist() ?: return
        viewModelScope.launch {
            downloadRepository.deletePlaylist(playlist)
            getPlaylistInfoAsync()
        }
    }

    fun cancelDownload() {
        if (!uiState.value.isDownloading) {
            return
        }
        val playlist = getPlaylist() ?: return
        viewModelScope.launch {
            downloadRepository.cancelPlaylistDownload(playlist)
        }
    }


    fun downloadSong(song: Song) {
        val playlist = getPlaylist() ?: return
        if (song.downloaded) {
            return
        }
        viewModelScope.launch {
            val settings = datastoreRepository.getSettings()
            downloadRepository.downloadSong(playlist, song, settings.downloadOnMetered)
        }
    }

    /** Exports the currently loaded playlist (name + songs) to the user-chosen JSON file. */
    fun exportPlaylist(context: Context, uri: Uri) {
        val playlist = getPlaylist() ?: return
        viewModelScope.launch {
            try {
                localUserPlaylistRepository.exportToUri(
                    context = context,
                    uri = uri,
                    name = playlist.info.title,
                    songs = playlist.songs.map { LocalPlaylistSong.fromSong(it) },
                )
                showToast(R.string.playlist_exported_toast)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (ex: Exception) {
                printe(message = "Failed to export playlist", exception = ex)
                showToast(R.string.playlist_export_failed_toast)
            }
        }
    }

    private suspend fun getPlaylistInfoAsync(useCache: Boolean = true) {
        try {
            val settings = datastoreRepository.getSettings()

            // "J'aime" is already part of the account library snapshot. Reuse it before the
            // dedicated playlist request so opening this screen never waits on the network.
            if (playlistInfo.isLikedSongsPlaylist) {
                LibraryRepository.cachedLibrary(settings)
                    ?.likedSongs
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { cachedSongs ->
                        _uiState.update { current ->
                            current.copy(
                                screenState = ScreenState.Success(
                                    Playlist(
                                        info = playlistInfo,
                                        songs = LikeManager.mergeLikedSongs(cachedSongs),
                                    )
                                )
                            )
                        }
                    }
            }

            // The downloaded pseudo-playlist is device-local; only real account playlists go
            // through the remote catalog seam.
            val playlistFlow: Flow<ApiResult<Playlist>> =
                if (playlistInfo.isDownloadedPlaylist) {
                    playlistRepository.retrieveOne(Playlist(playlistInfo), settings, useCache)
                } else {
                    catalog.playlist(
                        id = playlistInfo.id,
                        title = playlistInfo.title,
                        artworkUrl = playlistInfo.coverHref,
                        allowCached = useCache,
                    ).map { result ->
                        when (result) {
                            is CatalogResult.Loading -> ApiResult.Loading
                            is CatalogResult.Success -> ApiResult.Success(result.data.toPlaylist())
                            is CatalogResult.Error -> ApiResult.Error(result.exception)
                        }
                    }
                }
            playlistFlow
                .collect { apiResult ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            screenState = when (apiResult) {
                                is ApiResult.Error ->
                                    currentState.screenState.takeIf { it is ScreenState.Success }
                                        ?: ScreenState.Error(apiResult.exception)

                                ApiResult.Loading ->
                                    currentState.screenState.takeIf { it is ScreenState.Success }
                                        ?: ScreenState.Loading(playlistInfo)
                                is ApiResult.Success -> {
                                    ScreenState.Success(
                                        playlist = if (playlistInfo.isLikedSongsPlaylist) {
                                            apiResult.data.copy(
                                                songs = LikeManager.mergeLikedSongs(
                                                    apiResult.data.songs
                                                )
                                            )
                                        } else {
                                            apiResult.data
                                        }
                                    )
                                }
                            }
                        )
                    }
                }

        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ex: Exception) {
            printe(message = ex.toString(), exception = ex)
            _uiState.update {
                _uiState.value.copy(
                    screenState = ScreenState.Error(ex)
                )
            }
        }

    }

    private fun updatePlaylistFrom(oldPlaylist: Playlist, updatedPlaylist: Playlist?): Playlist {
        if (updatedPlaylist == null) {
            return oldPlaylist
        }
        val localMap = updatedPlaylist.songs.associateBy { it.youtubeId }
        val mergedSongs = oldPlaylist.songs.map { remoteSong ->
            localMap[remoteSong.youtubeId]?.copy(uid = Uuid.random().toString())
                ?: remoteSong
        }
        return oldPlaylist.copy(songs = mergedSongs)
    }

    private fun getPlaylist(): Playlist? {
        val screenState = _uiState.value.screenState
        if (screenState !is ScreenState.Success) {
            return null
        }
        return screenState.playlist
    }

    companion object {
        fun Factory(
            playlistInfo: PlaylistInfo,
            sharedViewModel: SharedViewModel,
            application: Application
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PlaylistViewModel(playlistInfo, sharedViewModel, application)
                }
            }
    }
}
