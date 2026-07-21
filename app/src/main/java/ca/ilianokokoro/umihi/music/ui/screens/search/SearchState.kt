package ca.ilianokokoro.umihi.music.ui.screens.search

import ca.ilianokokoro.umihi.music.models.MoodCategorySection
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.SpellCorrection


data class SearchState(
    val search: String = String(),
    val screenState: ScreenState = ScreenState.Success(),
    val continuation: String? = null,
    val isLoadingMore: Boolean = false,
    val filter: SearchFilter = SearchFilter.All,
    val suggestions: List<String> = emptyList(),
    val showSuggestions: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    /** The "Moods & genres" chip sections shown on the idle screen; empty when the fetch failed. */
    val moodSections: List<MoodCategorySection> = emptyList(),
)


sealed class ScreenState {
    data class Success(
        val results: List<SearchItem> = listOf(),
        /** The "All" tab's top-result card entity; null when the response carried none. */
        val topResult: SearchItem? = null,
        /** The response's spell correction, rendered as a results-list header; usually null. */
        val spellCorrection: SpellCorrection? = null,
    ) : ScreenState()

    data object Loading : ScreenState()
    data class Error(val exception: Exception) : ScreenState()
}
