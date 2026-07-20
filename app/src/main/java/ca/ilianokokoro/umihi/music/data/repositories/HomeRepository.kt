package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.data.datasources.HomeDataSource
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class HomeRepository {
    private val dataSource = HomeDataSource()

    fun retrieve(settings: UmihiSettings): Flow<ApiResult<List<HomeFeedSection>>> = flow {
        emit(ApiResult.Loading)
        try {
            emit(ApiResult.Success(dataSource.retrieve(settings)))
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            emit(ApiResult.Error(exception))
        }
    }.flowOn(Dispatchers.IO)
}
