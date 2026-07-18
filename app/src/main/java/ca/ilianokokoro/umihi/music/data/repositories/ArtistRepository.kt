package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.data.datasources.ArtistDataSource
import ca.ilianokokoro.umihi.music.extensions.toException
import ca.ilianokokoro.umihi.music.models.ArtistPage
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class ArtistRepository {
    private val artistDataSource = ArtistDataSource()

    fun getArtistForSong(song: Song): Flow<ApiResult<ArtistPage>> {
        return flow {
            emit(ApiResult.Loading)
            emit(ApiResult.Success(artistDataSource.getArtistForSong(song)))
        }.catch { e ->
            emit(ApiResult.Error(e.toException()))
        }.flowOn(Dispatchers.IO)
    }
}
