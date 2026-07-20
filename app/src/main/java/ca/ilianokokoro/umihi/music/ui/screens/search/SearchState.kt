package ca.ilianokokoro.umihi.music.ui.screens.search

import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.SearchItem


data class SearchState(
    val search: String = String(),
    val screenState: ScreenState = ScreenState.Success(),
    val continuation: String? = null,
    val isLoadingMore: Boolean = false,
    val filter: SearchFilter = SearchFilter.All,
    val suggestions: List<String> = emptyList(),
    val showSuggestions: Boolean = false,
)


sealed class ScreenState {
    data class Success(
        val results: List<SearchItem> = listOf()
    ) : ScreenState()

    data object Loading : ScreenState()
    data class Error(val exception: Exception) : ScreenState()
}
