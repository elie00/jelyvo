package ca.ilianokokoro.umihi.music.ui.screens.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.data.datasources.local.LocalAudioDataSource
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.LibraryRepository
import ca.ilianokokoro.umihi.music.data.repositories.LocalPlaylistRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlaylistRepository
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.Privacy
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val libraryRepository = LibraryRepository()
    private val playlistRepository = PlaylistRepository(application)
    private val localPlaylistRepository = LocalPlaylistRepository(application)
    private val datastoreRepository = DatastoreRepository(application)
    private val localAudioDataSource = LocalAudioDataSource(application)

    private val _uiState = MutableStateFlow(LibraryState())
    val uiState = _uiState.asStateFlow()

    init {
        load()
        observeLocalPlaylists()
    }

    private fun observeLocalPlaylists() {
        viewModelScope.launch {
            localPlaylistRepository.observeAll().collect { playlists ->
                _uiState.update { it.copy(localPlaylists = playlists) }
            }
        }
    }

    /** Imports a playlist JSON file (SAF Uri) into a new local playlist. */
    fun importPlaylist(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                localPlaylistRepository.importFromUri(getApplication(), uri)
                R.string.playlist_imported
            } catch (ex: Exception) {
                printe(message = "Failed to import playlist", exception = ex)
                R.string.playlist_import_failed
            }
            _uiState.update { it.copy(importMessage = message) }
        }
    }

    fun consumeImportMessage() {
        _uiState.update { it.copy(importMessage = null) }
    }

    fun load() {
        viewModelScope.launch {
            loadSuspend(showFullLoading = true)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                loadSuspend(showFullLoading = false, useCache = false)
            } catch (ex: Exception) {
                printe(message = ex.toString(), exception = ex)
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** Reloads only the playlists, e.g. after another screen deleted or renamed one. */
    fun refreshPlaylists() {
        viewModelScope.launch {
            try {
                val settings = datastoreRepository.getSettings()
                if (settings.cookies.isEmpty()) {
                    return@launch
                }
                loadPlaylistsSuspend(settings)
            } catch (ex: Exception) {
                printe(message = ex.toString(), exception = ex)
            }
        }
    }

    /** Loads on-device music (MediaStore). Call once the audio permission has been granted. */
    fun loadLocalSongs() {
        viewModelScope.launch {
            try {
                val songs = localAudioDataSource.getSongs()
                _uiState.update { it.copy(localSongs = songs, localSongsLoaded = true) }
            } catch (ex: Exception) {
                printe(message = "Failed to load local songs", exception = ex)
                _uiState.update { it.copy(localSongs = emptyList(), localSongsLoaded = true) }
            }
        }
    }

    fun removePlaylistsFromList(playlistIds: Set<String>) {
        _uiState.update { currentState ->
            currentState.copy(
                playlists = currentState.playlists.filterNot { playlist ->
                    playlist.id in playlistIds
                }
            )
        }
    }

    fun createPlaylist(title: String, description: String, privacy: Privacy) {
        viewModelScope.launch {
            try {
                val settings = datastoreRepository.getSettings()

                if (settings.cookies.isEmpty()) {
                    _uiState.update {
                        it.copy(screenState = ScreenState.LoggedOut)
                    }
                    return@launch
                }

                playlistRepository.create(title, description, privacy, settings)
                    .collect { apiResult ->
                        if (apiResult !is ApiResult.Success || apiResult.data == null) {
                            return@collect
                        }

                        _uiState.update { currentState ->
                            currentState.copy(
                                playlists = currentState.playlists
                                    .toMutableList()
                                    .apply {
                                        // Right after the synthetic "Downloaded" playlist.
                                        add(
                                            index = 1.coerceAtMost(size),
                                            element = apiResult.data
                                        )
                                    }
                            )
                        }
                    }
            } catch (ex: Exception) {
                printe(message = ex.toString(), exception = ex)
            }
        }
    }

    private suspend fun loadSuspend(showFullLoading: Boolean, useCache: Boolean = true) {
        val settings = datastoreRepository.getSettings()

        if (settings.cookies.isEmpty()) {
            _uiState.update { it.copy(screenState = ScreenState.LoggedOut) }
            return
        }

        coroutineScope {
            launch {
                try {
                    loadPlaylistsSuspend(settings)
                } catch (ex: Exception) {
                    printe(message = "Failed to load playlists", exception = ex)
                }
            }

            libraryRepository.getLibrary(settings, useCache).collect { result ->
                _uiState.update { currentState ->
                    when (result) {
                        is ApiResult.Loading -> if (showFullLoading) {
                            currentState.copy(screenState = ScreenState.Loading)
                        } else {
                            currentState
                        }

                        is ApiResult.Success -> currentState.copy(
                            screenState = ScreenState.Success(result.data)
                        )

                        is ApiResult.Error -> {
                            printe(
                                message = "Failed to load library",
                                exception = result.exception
                            )
                            currentState.copy(screenState = ScreenState.Error(result.exception))
                        }
                    }
                }
            }
        }
    }

    private suspend fun loadPlaylistsSuspend(settings: UmihiSettings) {
        val apiResult = playlistRepository.retrieveAll(settings)
            .first { result ->
                result is ApiResult.Success || result is ApiResult.Error
            }

        val playlists = when (apiResult) {
            is ApiResult.Success -> apiResult.data
            is ApiResult.Error -> {
                printe(
                    message = "Failed to load playlists",
                    exception = apiResult.exception
                )
                emptyList()
            }

            ApiResult.Loading -> return
        }

        _uiState.update { currentState ->
            currentState.copy(playlists = applyPlaylistFilters(playlists, settings))
        }
    }

    private fun applyPlaylistFilters(
        playlists: List<PlaylistInfo>,
        settings: UmihiSettings,
    ): List<PlaylistInfo> {
        val mutablePlaylists = playlists.toMutableList()
        val downloadedPlaylist = PlaylistInfo(
            id = Constants.Downloads.DOWNLOADED_PLAYLIST_ID,
            title = "Downloaded",
        )

        if (!settings.showPodcastPlaylist) {
            mutablePlaylists.removeIf { it.id == Constants.YoutubeApi.PODCAST_PLAYLIST_ID }
        }

        mutablePlaylists.add(0, downloadedPlaylist)
        return mutablePlaylists
    }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LibraryViewModel(application)
            }
        }
    }
}
