package ca.ilianokokoro.umihi.music.core.domain

import kotlinx.coroutines.flow.Flow

/** Platform-neutral search and browse slice of Jelyvo's remote music catalog. */
interface MusicCatalog {
    fun search(
        query: String,
        filter: CatalogSearchFilter = CatalogSearchFilter.SONGS,
        exactMatchToken: String? = null,
    ): Flow<CatalogResult<CatalogSearchPage>>

    suspend fun searchMore(
        continuationToken: String,
        filter: CatalogSearchFilter = CatalogSearchFilter.SONGS,
    ): CatalogSearchPage

    suspend fun suggestions(query: String): List<String>

    fun album(id: String, title: String): Flow<CatalogResult<CatalogAlbumPage>>

    fun artist(id: String, name: String): Flow<CatalogResult<CatalogArtistPage>>

    fun playlist(
        id: String,
        title: String = "",
        artworkUrl: String = "",
        allowCached: Boolean = true,
    ): Flow<CatalogResult<CatalogPlaylistPage>>
}

/** Remote playlist page: account-side identity plus its ordered tracks. */
data class CatalogPlaylistPage(
    val id: String,
    val title: String,
    val artworkUrl: String,
    val tracks: List<Track>,
)

/** Artist browse page: profile header, popularity-ranked tracks and discography shelves. */
data class CatalogArtistPage(
    val name: String,
    val artworkUrl: String,
    val channelId: String,
    val description: String?,
    /** Localized follower/subscriber label as the provider formats it, e.g. "12M subscribers". */
    val followerCountLabel: String?,
    /** Channel id the follow endpoints target, and whether the account already follows it. */
    val subscriptionChannelId: String?,
    val subscribed: Boolean?,
    /** The provider's most-streamed ranking, with [Track.playCountLabel] populated. */
    val topTracks: List<Track>,
    /** Full track listing reachable from the page, ordered by the provider's discography. */
    val allTracks: List<Track>,
    val shelves: List<CatalogShelf>,
)

/** Album browse page: header metadata, ordered tracks and the shelves rendered below them. */
data class CatalogAlbumPage(
    val title: String,
    val tracks: List<Track>,
    val artworkUrl: String,
    val artistName: String,
    val artistId: String?,
    /** Album type + release year as the provider formats it, e.g. "Album • 2023". */
    val subtitle: String,
    /** Track count + total duration as the provider formats it, e.g. "12 songs • 41 minutes". */
    val secondSubtitle: String,
    val description: String,
    val shelves: List<CatalogShelf>,
    /** Provider-side collection id targeted by the library save endpoint, when exposed. */
    val audioPlaylistId: String?,
    /** Null = the response did not expose the library state. */
    val savedToLibrary: Boolean?,
)

/** A browse-page carousel ("Other versions", "Singles", "Fans might also like"…). */
data class CatalogShelf(
    val title: String,
    val albums: List<CatalogSearchItem.Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val artists: List<CatalogSearchItem.Artist> = emptyList(),
    /** Full-listing page behind the shelf header's "More" button, when the provider ships one. */
    val moreToken: CatalogShelfToken? = null,
)

data class CatalogShelfToken(
    val browseId: String,
    val params: String,
)

enum class CatalogSearchFilter { ALL, SONGS, VIDEOS, ALBUMS, ARTISTS }

sealed interface CatalogResult<out T> {
    data object Loading : CatalogResult<Nothing>
    data class Success<T>(val data: T) : CatalogResult<T>
    data class Error(val exception: Exception) : CatalogResult<Nothing>
}

data class CatalogSearchPage(
    val items: List<CatalogSearchItem>,
    val continuation: String?,
    val topResult: CatalogSearchItem? = null,
    val spellCorrection: CatalogSpellCorrection? = null,
)

sealed interface CatalogSearchItem {
    val id: String

    data class TrackItem(val track: Track) : CatalogSearchItem {
        override val id: String get() = track.id
    }

    data class Album(
        override val id: String,
        val title: String,
        val subtitle: String,
        val artworkUrl: String,
    ) : CatalogSearchItem

    data class Artist(
        override val id: String,
        val name: String,
        val artworkUrl: String,
    ) : CatalogSearchItem
}

data class CatalogSpellCorrection(
    val correctedQuery: String,
    val correctedToken: String?,
    val originalQuery: String?,
    val originalToken: String?,
    val autoCorrected: Boolean,
)
