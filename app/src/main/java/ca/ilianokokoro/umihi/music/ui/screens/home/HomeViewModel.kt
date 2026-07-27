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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
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
        observeAccountSession()
        observeRecentlyPlayed()
        observeResumable()
        observeShortcuts()
        observeForYouMixes()
    }

    /**
     * The HomeViewModel outlives the Settings and authentication screens. Observe the persisted
     * session so returning from login/logout immediately replaces the obsolete Home state.
     */
    private fun observeAccountSession() {
        viewModelScope.launch {
            datastoreRepository.settings.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        profileName = settings.profileName,
                        profileAvatarPath = settings.profileAvatarPath
                    )
                }
            }
        }
        viewModelScope.launch {
            datastoreRepository.settings
                .distinctUntilChangedBy { settings ->
                    settings.cookies.raw to settings.dataSyncId
                }
                .collectLatest { settings ->
                    if (settings.cookies.isEmpty()) {
                        _uiState.update { it.copy(screenState = ScreenState.LoggedOut) }
                    } else {
                        _uiState.update { it.copy(screenState = ScreenState.Loading) }
                        getHomeFeedSuspend(settings = settings)
                    }
                }
        }
    }

    private fun observeRecentlyPlayed() {
        viewModelScope.launch {
            historyRepository.recentSongs().collect { songs ->
                _uiState.update { it.copy(recentlyPlayed = songs) }
            }
        }
    }

    private fun observeResumable() {
        viewModelScope.launch {
            historyRepository.resumableSongs().collect { items ->
                _uiState.update { it.copy(resumeItems = items) }
            }
        }
    }

    /** Shortcut tiles: most played tracks of the last month, padded with the latest plays. */
    private fun observeShortcuts() {
        val since = System.currentTimeMillis() - SHORTCUTS_WINDOW_MS
        viewModelScope.launch {
            combine(
                historyRepository.topTracks(since, limit = HOME_SHORTCUTS_MAX),
                historyRepository.recentSongs(limit = HOME_SHORTCUTS_MAX),
            ) { tops, recents -> buildHomeShortcuts(tops, recents) }
                .collect { shortcuts ->
                    _uiState.update { it.copy(shortcuts = shortcuts) }
                }
        }
    }

    /** "For you" mixes derived from the local play history; see [buildForYouMixes]. */
    private fun observeForYouMixes() {
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            combine(
                historyRepository.topTracks(now - REPLAY_WINDOW_MS, limit = HOME_MIX_MAX_SONGS),
                historyRepository.topTracks(since = 0L, limit = ALL_TIME_SAMPLE),
            ) { recentTops, allTimeTops -> buildForYouMixes(recentTops, allTimeTops, now) }
                .collect { mixes ->
                    _uiState.update { it.copy(forYouMixes = mixes) }
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

    private suspend fun getHomeFeedSuspend(
        settings: UmihiSettings? = null,
        useCache: Boolean = true,
    ) {
        try {
            val currentSettings = settings ?: datastoreRepository.getSettings()

            if (currentSettings.cookies.isEmpty()) {
                _uiState.update { currentState ->
                    currentState.copy(screenState = ScreenState.LoggedOut)
                }
                return
            }

            coroutineScope {
                // New releases fan out over several artists and are commonly slower. Start them
                // concurrently, but paint the main feed as soon as it is ready.
                val releases = async {
                    retrieveNewReleases(currentSettings, useCache)
                }
                val homeResult = retrieveHomeSections(currentSettings, useCache)

                when (homeResult) {
                    is ApiResult.Success -> {
                        _uiState.update { currentState ->
                            val existingReleases =
                                (currentState.screenState as? ScreenState.LoggedIn)
                                    ?.newReleases
                                    .orEmpty()
                            currentState.copy(
                                screenState = ScreenState.LoggedIn(
                                    homeSections = homeResult.data,
                                    newReleases = existingReleases,
                                )
                            )
                        }

                        val newReleases = releases.await()
                        _uiState.update { currentState ->
                            val loggedIn =
                                currentState.screenState as? ScreenState.LoggedIn
                                    ?: return@update currentState
                            currentState.copy(
                                screenState = loggedIn.copy(newReleases = newReleases)
                            )
                        }
                    }

                    is ApiResult.Error -> {
                        printe(
                            message = "Failed to load home feed",
                            exception = homeResult.exception
                        )
                        val newReleases = releases.await()
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
                                    screenState = ScreenState.LoggedIn(
                                        newReleases = newReleases
                                    )
                                )
                            }
                        }
                    }

                    ApiResult.Loading -> Unit
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            printe(message = ex.toString(), exception = ex)
            _uiState.update { currentState ->
                if (currentState.screenState is ScreenState.Loading) {
                    currentState.copy(screenState = ScreenState.Error(ex))
                } else {
                    currentState
                }
            }
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
        /** Shortcut tiles look at the last 30 days of plays. */
        private const val SHORTCUTS_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

        /** The "Replay" mix covers the last 90 days. */
        private const val REPLAY_WINDOW_MS = 90L * 24 * 60 * 60 * 1000

        /** All-time sample size fed to the rediscoveries filter. */
        private const val ALL_TIME_SAMPLE = 200

        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(application)
            }
        }
    }
}
