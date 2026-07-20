package ca.ilianokokoro.umihi.music.ui.screens.search


import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.data.repositories.SongRepository
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(SearchState())
    val uiState = _uiState.asStateFlow()

    val songRepository = SongRepository()
    private var searchJob: Job? = null
    private var suggestionsJob: Job? = null
    private var loadMoreJob: Job? = null

    /**
     * Per query+tab result cache so switching tabs back to an already-fetched scope reuses the
     * page (items + continuation) instead of re-hitting the network.
     */
    private val resultCache = mutableMapOf<String, SearchResult>()

    private fun cacheKey(query: String, filter: SearchFilter) = "${filter.name}|$query"

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
            val filter = uiState.value.filter

            // Reuse an already-fetched page for this query+tab (tab switches come back here).
            resultCache[cacheKey(query, filter)]?.let { cached ->
                _uiState.update {
                    it.copy(
                        screenState = ScreenState.Success(results = cached.items),
                        continuation = cached.continuation,
                    )
                }
                return@launch
            }

            songRepository.search(query, filter).collect { apiResult ->
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
                                screenState = ScreenState.Success(results = apiResult.data.items),
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
        val current = (state.screenState as? ScreenState.Success)?.results ?: return
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
                    page = SearchResult(items = combined, continuation = more.continuation),
                )
                _uiState.update {
                    it.copy(
                        screenState = ScreenState.Success(results = combined),
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
        _uiState.update { it.copy(search = suggestion, showSuggestions = false) }
        search()
    }

    fun selectFilter(filter: SearchFilter) {
        if (_uiState.value.filter == filter) return
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
