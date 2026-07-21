package ca.ilianokokoro.umihi.music.models

/**
 * A page of search results plus the continuation token used to load the next page (null when there
 * are no more results). [topResult] is the unscoped ("All") search's top-result card entity, kept
 * out of [items] so the UI can render it as a dedicated card; null on scoped tabs, continuation
 * pages and responses without a card.
 */
data class SearchResult(
    val items: List<SearchItem>,
    val continuation: String?,
    val topResult: SearchItem? = null,
    /** The response's spell correction ("Showing results for…"/"Did you mean…"); usually null. */
    val spellCorrection: SpellCorrection? = null,
) {
    val songs: List<Song>
        get() = items.mapNotNull { (it as? SearchItem.Track)?.song }
}

/**
 * A spell correction attached to a search response. When [autoCorrected] is true the results shown
 * already are for [correctedQuery] ("Showing results for…") and [originalQuery] can be re-searched
 * verbatim through [originalParams]; when false the results are for the query as typed and
 * [correctedQuery] is only suggested ("Did you mean…").
 */
data class SpellCorrection(
    val correctedQuery: String,
    /** searchEndpoint params of the corrected query's link, forcing that exact search. */
    val correctedParams: String?,
    val originalQuery: String?,
    /** searchEndpoint params of the original query's link, bypassing auto-correction. */
    val originalParams: String?,
    val autoCorrected: Boolean,
)

enum class SearchFilter(val params: String) {
    /** No scope params: YouTube Music's "Top results" view mixing songs, albums and artists. */
    All(""),
    Songs("EgWKAQIIAWoKEAkQBRAKEAMQBA=="),
    Videos("EgWKAQIQAWoKEAkQChAFEAMQBA=="),
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
