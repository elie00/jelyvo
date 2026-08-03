package ca.ilianokokoro.umihi.music.data.catalog

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.domain.CatalogAlbumPage
import ca.ilianokokoro.umihi.music.core.domain.CatalogArtistPage
import ca.ilianokokoro.umihi.music.core.domain.CatalogResult
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchFilter
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchPage
import ca.ilianokokoro.umihi.music.core.domain.MusicCatalog
import ca.ilianokokoro.umihi.music.data.repositories.AlbumRepository
import ca.ilianokokoro.umihi.music.data.repositories.ArtistRepository
import ca.ilianokokoro.umihi.music.data.repositories.SongRepository
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import ca.ilianokokoro.umihi.music.models.adapters.toCatalogAlbumPage
import ca.ilianokokoro.umihi.music.models.adapters.toCatalogArtistPage
import ca.ilianokokoro.umihi.music.models.adapters.toCatalogSearchPage
import ca.ilianokokoro.umihi.music.models.adapters.toSearchFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Android/InnerTube adapter for the platform-neutral [MusicCatalog].
 *
 * [settingsProvider] supplies the account cookies that browse endpoints need; search works
 * without one, so it is only required by callers using [album].
 */
class YoutubeMusicCatalog(
    private val songRepository: SongRepository = SongRepository(),
    private val albumRepository: AlbumRepository = AlbumRepository(),
    private val artistRepository: ArtistRepository = ArtistRepository(),
    private val settingsProvider: (suspend () -> UmihiSettings)? = null,
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

    override fun album(id: String, title: String): Flow<CatalogResult<CatalogAlbumPage>> = flow {
        val settings = requireSettings("album")
        albumRepository.getAlbum(id, title, settings).collect { result ->
            emit(
                when (result) {
                    ApiResult.Loading -> CatalogResult.Loading
                    is ApiResult.Error -> CatalogResult.Error(result.exception)
                    is ApiResult.Success ->
                        CatalogResult.Success(result.data.toCatalogAlbumPage())
                }
            )
        }
    }

    override fun artist(id: String, name: String): Flow<CatalogResult<CatalogArtistPage>> = flow {
        val settings = requireSettings("artist")
        artistRepository.getArtist(id, name, settings).collect { result ->
            emit(
                when (result) {
                    ApiResult.Loading -> CatalogResult.Loading
                    is ApiResult.Error -> CatalogResult.Error(result.exception)
                    is ApiResult.Success ->
                        CatalogResult.Success(result.data.toCatalogArtistPage())
                }
            )
        }
    }

    private suspend fun requireSettings(endpoint: String): UmihiSettings {
        val provider = checkNotNull(settingsProvider) {
            "MusicCatalog.$endpoint requires a settings provider"
        }
        return provider()
    }
}
