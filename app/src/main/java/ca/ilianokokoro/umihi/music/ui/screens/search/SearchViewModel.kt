package ca.ilianokokoro.umihi.music.ui.screens.search


import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.ResponseCache
import ca.ilianokokoro.umihi.music.core.domain.CatalogResult
import ca.ilianokokoro.umihi.music.core.domain.MusicCatalog
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.data.catalog.YoutubeMusicCatalog
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.datasources.local.LocalAudioDataSource
import ca.ilianokokoro.umihi.music.data.repositories.AlbumRepository
import ca.ilianokokoro.umihi.music.data.repositories.ArtistRepository
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.HistoryRepository
import ca.ilianokokoro.umihi.music.data.repositories.LibraryRepository
import ca.ilianokokoro.umihi.music.data.repositories.LocalPlaylistRepository
import ca.ilianokokoro.umihi.music.data.repositories.MoodsRepository
import ca.ilianokokoro.umihi.music.extensions.containsNormalized
import ca.ilianokokoro.umihi.music.models.Library
import ca.ilianokokoro.umihi.music.models.LocalPlaylistSearchSource
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.SearchResult
import ca.ilianokokoro.umihi.music.models.adapters.toCatalogSearchFilter
import ca.ilianokokoro.umihi.music.models.adapters.toSearchResult
import ca.ilianokokoro.umihi.music.models.searchLocalLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SearchViewModel(
    application: Application,
    private val musicCatalog: MusicCatalog = YoutubeMusicCatalog(),
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SearchState())
    val uiState = _uiState.asStateFlow()

    private val albumRepository = AlbumRepository()
    private val artistRepository = ArtistRepository()
    private val moodsRepository = MoodsRepository()
    private val datastoreRepository = DatastoreRepository(application)
    private val historyRepository = HistoryRepository(application)
    private val localPlaylistRepository = LocalPlaylistRepository(application)
    private val localAudioDataSource = LocalAudioDataSource(application)
    private val localSongDataSource = AppDatabase.getInstance(application).songRepository()
    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null
    private var loadMoreJob: Job? = null

    /**
     * Per query+tab result cache so switching tabs back to an already-fetched scope reuses the
     * page (items + continuation) instead of re-hitting the network.
     */
    private val resultCache = mutableMapOf<String, SearchResult>()

    /**
     * Raw searchEndpoint params of a clicked spell-correction link, sent instead of the filter's
     * scope params on the next search; cleared as soon as the user edits the query or switches tab.
     */
    private var paramsOverride: String? = null

    private fun cacheKey(query: String, filter: SearchFilter) =
        "${filter.name}|${paramsOverride.orEmpty()}|$query"

    init {
        viewModelScope.launch {
            datastoreRepository.recentSearches.collect { recents ->
                _uiState.update { it.copy(recentSearches = recents) }
            }
        }
        loadMoodSections()
    }

    /**
     * Loads the "Moods & genres" chips shown on the idle screen. Errors are only logged: the
     * section simply stays hidden when the fetch fails.
     */
    private fun loadMoodSections() {
        viewModelScope.launch {
            try {
                val settings = datastoreRepository.getSettings()
                moodsRepository.retrieveCategories(settings).collect { result ->
                    if (result is ApiResult.Success) {
                        _uiState.update { it.copy(moodSections = result.data) }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                printe(message = "Failed to load moods & genres", exception = e)
            }
        }
    }

    private fun cachePage(query: String, filter: SearchFilter, page: SearchResult) {
        if (resultCache.size >= 30) {
            resultCache.clear()
        }
        resultCache[cacheKey(query, filter)] = page
    }

    fun search() {
        suggestionsJob?.cancel()
        loadMoreJob?.cancel()
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val query = uiState.value.search
            if (query.isBlank()) {
                _uiState.update {
                    _uiState.value.copy(
                        screenState =
                            ScreenState.Success(results = listOf()),
                        showSuggestions = false,
                        historyMatches = emptyList(),
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(showSuggestions = false, suggestions = emptyList()) }
            datastoreRepository.addRecentSearch(query)
            val filter = uiState.value.filter

            // The "My library" tab is fully local: no network call, no result cache, no paging.
            if (filter == SearchFilter.Library) {
                searchLibraryLocally(query)
                return@launch
            }
            val catalogFilter = filter.toCatalogSearchFilter() ?: return@launch

            refreshHistoryMatches(query)

            // Reuse an already-fetched page for this query+tab (tab switches come back here).
            resultCache[cacheKey(query, filter)]?.let { cached ->
                _uiState.update {
                    it.copy(
                        screenState = ScreenState.Success(
                            results = cached.items,
                            topResult = cached.topResult,
                            spellCorrection = cached.spellCorrection,
                        ),
                        continuation = cached.continuation,
                    )
                }
                return@launch
            }

            musicCatalog.search(query, catalogFilter, paramsOverride).collect { catalogResult ->
                _uiState.update {
                    when (catalogResult) {
                        CatalogResult.Loading -> it.copy(
                            screenState = ScreenState.Loading,
                            continuation = null
                        )

                        is CatalogResult.Error -> it.copy(
                            screenState = ScreenState.Error(catalogResult.exception),
                            continuation = null
                        )

                        is CatalogResult.Success -> {
                            val page = catalogResult.data.toSearchResult()
                            cachePage(query, filter, page)
                            it.copy(
                                screenState = ScreenState.Success(
                                    results = page.items,
                                    topResult = page.topResult,
                                    spellCorrection = page.spellCorrection,
                                ),
                                continuation = page.continuation
                            )
                        }
                    }
                }
            }
        }

    }

    /**
     * The "My library" tab search: gathers the local sources (cached liked songs, local playlists,
     * on-device audio, downloads) and groups the matches per origin through [searchLocalLibrary].
     * Strictly offline — the liked songs are read from the library ResponseCache entry (whatever
     * its age) and simply absent when the library page was never loaded.
     */
    private suspend fun searchLibraryLocally(query: String) {
        _uiState.update {
            it.copy(
                screenState = ScreenState.Loading,
                continuation = null,
                historyMatches = emptyList(),
            )
        }

        val sections = withContext(Dispatchers.Default) {
            val settings = datastoreRepository.getSettings()
            val likedSongs = ResponseCache.get<Library>(
                key = LibraryRepository.cacheKey(settings),
                ttlMs = Long.MAX_VALUE,
            )?.likedSongs ?: emptyList()

            val localPlaylists = runCatching { localPlaylistRepository.observeAll().first() }
                .getOrDefault(emptyList())
                .map { playlist ->
                    val songs = localPlaylistRepository.songsOf(playlist).map { it.toSong() }
                    LocalPlaylistSearchSource(
                        id = playlist.id,
                        name = playlist.name,
                        coverHref = songs.firstOrNull { it.thumbnailHref.isNotBlank() }
                            ?.thumbnailHref.orEmpty(),
                        songs = songs,
                    )
                }

            // Missing audio permission (or an empty MediaStore) degrades to an absent section.
            val localAudioSongs = runCatching { localAudioDataSource.getSongs() }
                .getOrDefault(emptyList())

            val downloadedSongs = runCatching { localSongDataSource.getDownloadedSongs() }
                .getOrDefault(emptyList())

            searchLocalLibrary(
                query = query,
                likedSongs = likedSongs,
                localPlaylists = localPlaylists,
                localAudioSongs = localAudioSongs,
                downloadedSongs = downloadedSongs,
            )
        }

        _uiState.update {
            it.copy(
                screenState = ScreenState.Success(librarySections = sections),
                continuation = null,
            )
        }
    }

    /**
     * Refreshes the "Listened before" section: scans the most recent play-history tracks and keeps
     * the few whose title or artist matches [query] (accent- and case-insensitive). Best-effort —
     * a failure just leaves the section empty.
     */
    private suspend fun refreshHistoryMatches(query: String) {
        val matches = runCatching {
            historyRepository.recentSongs(HISTORY_SCAN_LIMIT).first()
        }.getOrDefault(emptyList())
            .filter { song ->
                song.title.containsNormalized(query) || song.artist.containsNormalized(query)
            }
            .take(MAX_HISTORY_MATCHES)
        _uiState.update { it.copy(historyMatches = matches) }
    }

    /** Plays a local playlist tapped in the "My library" results (name match rows). */
    fun playLocalPlaylist(id: String) {
        viewModelScope.launch {
            val playlist = localPlaylistRepository.observeById(id).firstOrNull() ?: return@launch
            val songs = localPlaylistRepository.songsOf(playlist).map { it.toSong() }
            if (songs.isEmpty()) {
                return@launch
            }
            PlayerManager.playPlaylist(
                Playlist(
                    info = PlaylistInfo(id = playlist.id, title = playlist.name),
                    songs = songs,
                )
            )
        }
    }

    fun loadMore() {
        val state = uiState.value
        val token = state.continuation ?: return
        val success = state.screenState as? ScreenState.Success ?: return
        val current = success.results
        if (state.isLoadingMore) {
            return
        }

        _uiState.update { it.copy(isLoadingMore = true) }
        loadMoreJob = viewModelScope.launch {
            try {
                val catalogFilter = state.filter.toCatalogSearchFilter() ?: return@launch
                val more = musicCatalog.searchMore(token, catalogFilter).toSearchResult()
                val combined = (current + more.items).distinctBy { it.id }
                cachePage(
                    query = state.search,
                    filter = state.filter,
                    page = SearchResult(
                        items = combined,
                        continuation = more.continuation,
                        topResult = success.topResult,
                        spellCorrection = success.spellCorrection,
                    ),
                )
                _uiState.update {
                    it.copy(
                        screenState = ScreenState.Success(
                            results = combined,
                            topResult = success.topResult,
                            spellCorrection = success.spellCorrection,
                        ),
                        continuation = more.continuation,
                        isLoadingMore = false,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                // The continuation token is kept, so scrolling back down retries; without a
                // message the spinner just vanishes and the list looks silently truncated.
                printe(message = "search loadMore failed", exception = e)
                _uiState.update { it.copy(isLoadingMore = false) }
                val app = getApplication<Application>()
                Toast.makeText(
                    app,
                    app.getString(R.string.search_load_more_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun onSearchFieldChange(newValue: String) {
        paramsOverride = null
        _uiState.update {
            it.copy(search = newValue, showSuggestions = newValue.isNotBlank())
        }
        suggestionsJob?.cancel()
        // No remote suggestions on the "My library" tab: it must stay fully offline.
        if (newValue.length < 2 || _uiState.value.filter == SearchFilter.Library) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestionsJob = viewModelScope.launch {
            delay(250)
            val suggestions = runCatching { musicCatalog.suggestions(newValue) }
                .getOrDefault(emptyList())
            if (_uiState.value.search == newValue) {
                _uiState.update { it.copy(suggestions = suggestions) }
            }
        }
    }

    fun selectSuggestion(suggestion: String) {
        paramsOverride = null
        _uiState.update { it.copy(search = suggestion, showSuggestions = false) }
        search()
    }

    /**
     * Reruns the search through a spell-correction link ("Did you mean…"/"Search instead for…"):
     * [query] becomes the search text and [params], when present, replace the filter's scope
     * params so the exact linked search (corrected, or original-verbatim) is executed.
     */
    fun searchCorrection(query: String, params: String?) {
        paramsOverride = params
        _uiState.update { it.copy(search = query, showSuggestions = false) }
        search()
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            datastoreRepository.removeRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            datastoreRepository.clearRecentSearches()
        }
    }

    /**
     * Plays the top-result card entity: a track plays directly, an album or artist first loads its
     * page and then plays its songs as a queue.
     */
    fun playTopResult(item: SearchItem) {
        when (item) {
            is SearchItem.Track -> PlayerManager.playSong(item.song)

            is SearchItem.Album -> viewModelScope.launch {
                val settings = datastoreRepository.getSettings()
                val page = (albumRepository.getAlbum(item.id, item.title, settings)
                    .firstOrNull { it is ApiResult.Success } as? ApiResult.Success)
                    ?.data ?: return@launch
                if (page.songs.isEmpty()) {
                    return@launch
                }
                PlayerManager.playPlaylist(
                    Playlist(
                        info = PlaylistInfo(
                            id = "album:${item.id}",
                            title = page.name,
                            coverHref = page.cover.ifBlank { item.thumbnail },
                        ),
                        songs = page.songs,
                    )
                )
            }

            is SearchItem.Artist -> viewModelScope.launch {
                val settings = datastoreRepository.getSettings()
                val page = (artistRepository.getArtist(item.id, item.name, settings)
                    .firstOrNull { it is ApiResult.Success } as? ApiResult.Success)
                    ?.data ?: return@launch
                if (page.songs.isEmpty()) {
                    return@launch
                }
                PlayerManager.playPlaylist(
                    Playlist(
                        info = PlaylistInfo(
                            id = "artist:${item.id}",
                            title = item.name,
                            coverHref = item.thumbnail,
                        ),
                        songs = page.songs,
                    )
                )
            }
        }
    }

    fun selectFilter(filter: SearchFilter) {
        if (_uiState.value.filter == filter) return
        paramsOverride = null
        loadMoreJob?.cancel()
        _uiState.update {
            it.copy(
                filter = filter,
                continuation = null,
                screenState = ScreenState.Success(),
            )
        }
        if (_uiState.value.search.isNotBlank()) search()
    }

    companion object {
        /** How many recent play-history tracks are scanned for the "Listened before" section. */
        private const val HISTORY_SCAN_LIMIT = 200

        /** Maximum rows in the "Listened before" section, to keep the remote results in view. */
        private const val MAX_HISTORY_MATCHES = 5

        fun Factory(
            application: Application,
            musicCatalog: MusicCatalog = YoutubeMusicCatalog(),
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(application, musicCatalog)
            }
        }
    }
}
