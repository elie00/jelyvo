package ca.ilianokokoro.umihi.music.models

/**
 * A page of search results plus the continuation token used to load the next page (null when there
 * are no more results).
 */
data class SearchResult(
    val items: List<SearchItem>,
    val continuation: String?,
) {
    val songs: List<Song>
        get() = items.mapNotNull { (it as? SearchItem.Track)?.song }
}

enum class SearchFilter(val params: String) {
    /** No scope params: YouTube Music's "Top results" view mixing songs, albums and artists. */
    All(""),
    Songs("EgWKAQIIAWoKEAkQBRAKEAMQBA=="),
    Albums("EgWKAQIYAWoKEAkQChAFEAMQBA=="),
    Artists("EgWKAQIgAWoKEAkQChAFEAMQBA=="),
}

sealed interface SearchItem {
    val id: String

    data class Track(val song: Song) : SearchItem {
        override val id: String get() = song.youtubeId
    }

    data class Album(
        override val id: String,
        val title: String,
        val subtitle: String,
        val thumbnail: String,
    ) : SearchItem

    data class Artist(
        override val id: String,
        val name: String,
        val thumbnail: String,
    ) : SearchItem
}
