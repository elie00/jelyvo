package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.cachedApiFlow
import ca.ilianokokoro.umihi.music.core.localizedCacheKey
import ca.ilianokokoro.umihi.music.data.datasources.AlbumDataSource
import ca.ilianokokoro.umihi.music.models.AlbumPage
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.flow.Flow

class AlbumRepository {
    private val albumDataSource = AlbumDataSource()

    fun getAlbum(
        albumId: String,
        albumName: String,
        settings: UmihiSettings,
    ): Flow<ApiResult<AlbumPage>> =
        cachedApiFlow(
            key = localizedCacheKey("album:${settings.dataSyncId.orEmpty()}:$albumId"),
            diskSerializer = AlbumPage.serializer(),
        ) {
            albumDataSource.getAlbum(albumId, albumName, settings)
        }
}
