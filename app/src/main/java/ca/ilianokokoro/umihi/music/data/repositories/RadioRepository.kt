package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.data.datasources.RadioDataSource
import ca.ilianokokoro.umihi.music.extensions.toException
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class RadioRepository {
    private val radioDataSource = RadioDataSource()

    fun getRelated(seed: Song): Flow<ApiResult<List<Song>>> {
        return flow {
            emit(ApiResult.Loading)
            emit(ApiResult.Success(radioDataSource.getRelatedSongs(seed)))
        }.catch { e ->
            emit(ApiResult.Error(e.toException()))
        }.flowOn(Dispatchers.IO)
    }
}
