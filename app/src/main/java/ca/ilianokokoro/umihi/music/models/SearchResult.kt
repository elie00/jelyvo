package ca.ilianokokoro.umihi.music.models

/**
 * A page of search results plus the continuation token used to load the next page (null when there
 * are no more results).
 */
data class SearchResult(
    val songs: List<Song>,
    val continuation: String?,
)
