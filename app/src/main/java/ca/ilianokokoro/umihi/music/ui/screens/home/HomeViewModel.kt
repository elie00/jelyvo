package ca.ilianokokoro.umihi.music.ui.screens.home


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.HistoryRepository
import ca.ilianokokoro.umihi.music.data.repositories.HomeRepository
import ca.ilianokokoro.umihi.music.data.repositories.LibraryRepository
import ca.ilianokokoro.umihi.music.models.HomeFeedItem
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Home tab is about discovery and resumption: the recommendation feed, recently played songs
 * and the latest releases from the followed artists. The account's collection (playlists, liked
 * songs, albums, artists) lives on the Library tab.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(HomeState())
    val uiState = _uiState.asStateFlow()

    private val datastoreRepository = DatastoreRepository(application)
    private val historyRepository = HistoryRepository(application)
    private val homeRepository = HomeRepository()
    private val libraryRepository = LibraryRepository()


    init {
        getHomeFeed()
        observeRecentlyPlayed()
    }

    private fun observeRecentlyPlayed() {
        viewModelScope.launch {
            historyRepository.recentSongs().collect { songs ->
                _uiState.update { it.copy(recentlyPlayed = songs) }
            }
        }
    }

    fun getHomeFeed() {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(screenState = ScreenState.Loading)
            }
            getHomeFeedSuspend()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isRefreshing = true)
            }

            try {
                getHomeFeedSuspend(useCache = false)
            } catch (ex: Exception) {
                printe(message = ex.toString(), exception = ex)
            } finally {
                _uiState.update { currentState ->
                    currentState.copy(isRefreshing = false)
                }
            }
        }
    }

    private suspend fun getHomeFeedSuspend(useCache: Boolean = true) {
        try {
            val settings = datastoreRepository.getSettings()

            if (settings.cookies.isEmpty()) {
                _uiState.update { currentState ->
                    currentState.copy(screenState = ScreenState.LoggedOut)
                }
                return
            }

            // Both sections load concurrently; the new-releases fan-out is the slower one.
            val (homeResult, newReleases) = coroutineScope {
                val sections = async { retrieveHomeSections(settings, useCache) }
                val releases = async { retrieveNewReleases(settings, useCache) }
                sections.await() to releases.await()
            }

            when (homeResult) {
                is ApiResult.Success -> _uiState.update { currentState ->
                    currentState.copy(
                        screenState = ScreenState.LoggedIn(
                            homeSections = homeResult.data,
                            newReleases = newReleases,
                        )
                    )
                }

                is ApiResult.Error -> {
                    printe(
                        message = "Failed to load home feed",
                        exception = homeResult.exception
                    )
                    if (newReleases.isEmpty()) {
                        _uiState.update { currentState ->
                            currentState.copy(
                                screenState = ScreenState.Error(homeResult.exception)
                            )
                        }
                    } else {
                        // Degrade to the sections we could load instead of a full error screen.
                        _uiState.update { currentState ->
                            currentState.copy(
                                screenState = ScreenState.LoggedIn(newReleases = newReleases)
                            )
                        }
                    }
                }

                ApiResult.Loading -> Unit
            }
        } catch (ex: Exception) {
            printe(message = ex.toString(), exception = ex)
        }
    }

    private suspend fun retrieveHomeSections(
        settings: UmihiSettings,
        useCache: Boolean,
    ): ApiResult<List<HomeFeedSection>> =
        homeRepository.retrieve(settings, useCache).first { it !is ApiResult.Loading }

    private suspend fun retrieveNewReleases(
        settings: UmihiSettings,
        useCache: Boolean,
    ): List<HomeFeedItem.Album> =
        when (val result =
            libraryRepository.getNewReleases(settings, useCache)
                .first { it !is ApiResult.Loading }) {
            is ApiResult.Success -> result.data
            else -> emptyList()
        }

    companion object {
        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(application)
            }
        }
    }
}
