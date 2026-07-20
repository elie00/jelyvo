package ca.ilianokokoro.umihi.music.ui.screens.home


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.HistoryRepository
import ca.ilianokokoro.umihi.music.data.repositories.HomeRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlaylistRepository
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import ca.ilianokokoro.umihi.music.models.Privacy
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(HomeState())
    val uiState = _uiState.asStateFlow()

    private val playlistRepository = PlaylistRepository(application)
    private val datastoreRepository = DatastoreRepository(application)
    private val historyRepository = HistoryRepository(application)
    private val homeRepository = HomeRepository()


    init {
        getPlaylists()
        observeRecentlyPlayed()
    }

    private fun observeRecentlyPlayed() {
        viewModelScope.launch {
            historyRepository.recentSongs().collect { songs ->
                _uiState.update { it.copy(recentlyPlayed = songs) }
            }
        }
    }

    fun getPlaylists() {
        viewModelScope.launch {
            getPlaylistsSuspend()
        }
    }

    fun refreshPlaylists() {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isRefreshing = true)
            }

            try {
                refreshPlaylistsOnce()
            } catch (ex: Exception) {
                printe(message = ex.toString(), exception = ex)
            } finally {
                _uiState.update { currentState ->
                    currentState.copy(isRefreshing = false)
                }
            }
        }
    }

    private suspend fun refreshPlaylistsOnce() {
        val settings = datastoreRepository.getSettings()

        if (settings.cookies.isEmpty()) {
            _uiState.update { currentState ->
                currentState.copy(screenState = ScreenState.LoggedOut)
            }
            return
        }

        val apiResult = playlistRepository.retrieveAll(settings)
            .first { result ->
                result is ApiResult.Success || result is ApiResult.Error
            }

        val playlists = when (apiResult) {
            is ApiResult.Success -> apiResult.data.toMutableList()
            is ApiResult.Error -> emptyList()
            ApiResult.Loading -> return
        }

        applyPlaylistFiltersAndUpdateState(
            playlists = playlists,
            settings = settings,
            homeSections = retrieveHomeSections(settings),
        )
    }

    suspend fun getPlaylistsSuspend() {
        try {
            val settings = datastoreRepository.getSettings()

            if (settings.cookies.isEmpty()) {
                _uiState.update { currentState ->
                    currentState.copy(screenState = ScreenState.LoggedOut)
                }
                return
            }

            playlistRepository.retrieveAll(settings).collect { apiResult ->
                when (apiResult) {
                    ApiResult.Loading -> {
                        _uiState.update { currentState ->
                            currentState.copy(screenState = ScreenState.Loading)
                        }
                    }

                    is ApiResult.Success -> {
                        val playlists = apiResult.data.toMutableList()
                        applyPlaylistFiltersAndUpdateState(
                            playlists,
                            settings,
                            retrieveHomeSections(settings),
                        )
                    }

                    is ApiResult.Error -> {
                        printe(
                            message = "Failed to load playlists",
                            exception = apiResult.exception
                        )
                        _uiState.update { currentState ->
                            currentState.copy(screenState = ScreenState.LoggedOut)
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            printe(message = ex.toString(), exception = ex)
        }
    }

    private fun applyPlaylistFiltersAndUpdateState(
        playlists: List<PlaylistInfo>,
        settings: UmihiSettings,
        homeSections: List<HomeFeedSection>,
    ) {
        val mutablePlaylists = playlists.toMutableList()
        val downloadedPlaylist = PlaylistInfo(
            id = Constants.Downloads.DOWNLOADED_PLAYLIST_ID,
            title = "Downloaded",
        )

        if (!settings.showPodcastPlaylist) {
            mutablePlaylists.removeIf { it.id == Constants.YoutubeApi.PODCAST_PLAYLIST_ID }
        }

        mutablePlaylists.add(0, downloadedPlaylist)

        _uiState.update { currentState ->
            currentState.copy(
                screenState = ScreenState.LoggedIn(mutablePlaylists, homeSections)
            )
        }
    }

    private suspend fun retrieveHomeSections(
        settings: UmihiSettings
    ): List<HomeFeedSection> =
        when (val result = homeRepository.retrieve(settings).first { it !is ApiResult.Loading }) {
            is ApiResult.Success -> result.data
            else -> emptyList()
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

                        val currentState = _uiState.value.screenState
                        if (currentState !is ScreenState.LoggedIn) {
                            return@collect
                        }

                        val updatedPlaylists = currentState.playlistInfos
                            .toMutableList()
                            .apply {
                                add(index = 2.coerceAtMost(size), element = apiResult.data)
                            }

                        _uiState.update {
                            it.copy(
                                screenState = ScreenState.LoggedIn(updatedPlaylists)
                            )
                        }
                    }
            } catch (ex: Exception) {
                printe(message = ex.toString(), exception = ex)
            }
        }
    }

    fun removePlaylistsFromList(playlistIds: Set<String>) {
        _uiState.update { currentState ->
            val loggedIn = currentState.screenState as? ScreenState.LoggedIn
                ?: return@update currentState

            currentState.copy(
                screenState = loggedIn.copy(
                    playlistInfos = loggedIn.playlistInfos.filterNot { playlist ->
                        playlist.id in playlistIds
                    }
                )
            )
        }
    }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(application)
            }
        }
    }
}
