package ca.ilianokokoro.umihi.music.core.domain

import kotlinx.coroutines.flow.Flow

/** Platform-neutral search slice of Jelyvo's remote music catalog. */
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
}

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
