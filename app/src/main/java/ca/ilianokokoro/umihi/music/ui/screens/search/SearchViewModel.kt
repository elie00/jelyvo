package ca.ilianokokoro.umihi.music.ui.screens.search


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.data.repositories.AlbumRepository
import ca.ilianokokoro.umihi.music.data.repositories.ArtistRepository
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.MoodsRepository
import ca.ilianokokoro.umihi.music.data.repositories.SongRepository
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SearchState())
    val uiState = _uiState.asStateFlow()

    val songRepository = SongRepository()
    private val albumRepository = AlbumRepository()
    private val artistRepository = ArtistRepository()
    private val moodsRepository = MoodsRepository()
    private val datastoreRepository = DatastoreRepository(application)
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
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(showSuggestions = false, suggestions = emptyList()) }
            datastoreRepository.addRecentSearch(query)
            val filter = uiState.value.filter

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

            songRepository.search(query, filter, paramsOverride).collect { apiResult ->
                _uiState.update {
                    when (apiResult) {
                        ApiResult.Loading -> it.copy(
                            screenState = ScreenState.Loading,
                            continuation = null
                        )

                        is ApiResult.Error -> it.copy(
                            screenState = ScreenState.Error(apiResult.exception),
                            continuation = null
                        )

                        is ApiResult.Success -> {
                            cachePage(query, filter, apiResult.data)
                            it.copy(
                                screenState = ScreenState.Success(
                                    results = apiResult.data.items,
                                    topResult = apiResult.data.topResult,
                                    spellCorrection = apiResult.data.spellCorrection,
                                ),
                                continuation = apiResult.data.continuation
                            )
                        }
                    }
                }
            }
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
                val more = songRepository.searchMore(token, state.filter)
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
            } catch (e: Exception) {
                printe(message = "search loadMore failed", exception = e)
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun onSearchFieldChange(newValue: String) {
        paramsOverride = null
        _uiState.update {
            it.copy(search = newValue, showSuggestions = newValue.isNotBlank())
        }
        suggestionsJob?.cancel()
        if (newValue.length < 2) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestionsJob = viewModelScope.launch {
            delay(250)
            val suggestions = runCatching { songRepository.suggestions(newValue) }
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
        fun Factory(application: Application): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                SearchViewModel(application)
            }
        }
    }
}
