package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.data.datasources.AlbumDataSource
import ca.ilianokokoro.umihi.music.extensions.toException
import ca.ilianokokoro.umihi.music.models.AlbumPage
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class AlbumRepository {
    private val albumDataSource = AlbumDataSource()

    fun getAlbum(
        albumId: String,
        albumName: String,
        settings: UmihiSettings,
    ): Flow<ApiResult<AlbumPage>> {
        return flow {
            emit(ApiResult.Loading)
            val songs = albumDataSource.getAlbumSongs(albumId, settings)
            emit(ApiResult.Success(AlbumPage(name = albumName, songs = songs)))
        }.catch { e ->
            emit(ApiResult.Error(e.toException()))
        }.flowOn(Dispatchers.IO)
    }
}
