package ca.ilianokokoro.umihi.music.data.catalog

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.domain.CatalogResult
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchFilter
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchPage
import ca.ilianokokoro.umihi.music.core.domain.MusicCatalog
import ca.ilianokokoro.umihi.music.data.repositories.SongRepository
import ca.ilianokokoro.umihi.music.models.adapters.toCatalogSearchPage
import ca.ilianokokoro.umihi.music.models.adapters.toSearchFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Android/InnerTube adapter for the platform-neutral [MusicCatalog]. */
class YoutubeMusicCatalog(
    private val songRepository: SongRepository = SongRepository(),
) : MusicCatalog {
    override fun search(
        query: String,
        filter: CatalogSearchFilter,
        exactMatchToken: String?,
    ): Flow<CatalogResult<CatalogSearchPage>> =
        songRepository.search(query, filter.toSearchFilter(), exactMatchToken).map { result ->
            when (result) {
                ApiResult.Loading -> CatalogResult.Loading
                is ApiResult.Error -> CatalogResult.Error(result.exception)
                is ApiResult.Success -> CatalogResult.Success(result.data.toCatalogSearchPage())
            }
        }

    override suspend fun searchMore(
        continuationToken: String,
        filter: CatalogSearchFilter,
    ): CatalogSearchPage = songRepository
        .searchMore(continuationToken, filter.toSearchFilter())
        .toCatalogSearchPage()

    override suspend fun suggestions(query: String): List<String> =
        songRepository.suggestions(query)
}
