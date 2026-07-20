package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.cachedApiFlow
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
        cachedApiFlow(key = "album:$albumId") {
            albumDataSource.getAlbum(albumId, albumName, settings)
        }
}
