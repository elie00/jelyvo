package ca.ilianokokoro.umihi.music.desktop.ui

import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchFilter

/** Where the window is pointed. Album and artist carry the URL NewPipe handed back as their id. */
sealed interface Screen {
    data object Search : Screen
    data object Account : Screen
    data class Album(val id: String, val title: String) : Screen
    data class Artist(val id: String, val name: String) : Screen
}

/** The search tabs, in the order YouTube Music itself lists them. */
val SEARCH_FILTERS = listOf(
    CatalogSearchFilter.SONGS to "Titres",
    CatalogSearchFilter.ALBUMS to "Albums",
    CatalogSearchFilter.ARTISTS to "Artistes",
)
