package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.cachedApiFlow
import ca.ilianokokoro.umihi.music.data.datasources.HomeDataSource
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.flow.Flow

class HomeRepository {
    private val dataSource = HomeDataSource()

    fun retrieve(
        settings: UmihiSettings,
        useCache: Boolean = true,
    ): Flow<ApiResult<List<HomeFeedSection>>> =
        cachedApiFlow(key = "home:${settings.dataSyncId.orEmpty()}", useCache = useCache) {
            dataSource.retrieve(settings)
        }
}
