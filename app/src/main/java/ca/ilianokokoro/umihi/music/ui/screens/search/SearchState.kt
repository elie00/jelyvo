package ca.ilianokokoro.umihi.music.ui.screens.search

import ca.ilianokokoro.umihi.music.models.LocalLibrarySection
import ca.ilianokokoro.umihi.music.models.MoodCategorySection
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.SpellCorrection
import androidx.compose.runtime.Immutable


@Immutable
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
    /**
     * Play-history tracks matching the current query ("Listened before" section shown above the
     * remote results). Refreshed on every remote search; empty on the Library tab.
     */
    val historyMatches: List<Song> = emptyList(),
)


@Immutable
sealed class ScreenState {
    data class Success(
        val results: List<SearchItem> = listOf(),
        /** The "All" tab's top-result card entity; null when the response carried none. */
        val topResult: SearchItem? = null,
        /** The response's spell correction, rendered as a results-list header; usually null. */
        val spellCorrection: SpellCorrection? = null,
        /**
         * The "My library" tab's grouped local matches; empty on every remote tab. When set,
         * [results] is always empty (the tab never hits the network).
         */
        val librarySections: List<LocalLibrarySection> = emptyList(),
    ) : ScreenState()

    data object Loading : ScreenState()
    data class Error(val exception: Exception) : ScreenState()
}
