package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.cachedApiFlow
import ca.ilianokokoro.umihi.music.data.datasources.MoodsDataSource
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import ca.ilianokokoro.umihi.music.models.MoodCategorySection
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.flow.Flow

class MoodsRepository {
    private val dataSource = MoodsDataSource()

    fun retrieveCategories(
        settings: UmihiSettings,
        useCache: Boolean = true,
    ): Flow<ApiResult<List<MoodCategorySection>>> =
        cachedApiFlow(key = "moods", useCache = useCache) {
            dataSource.retrieveCategories(settings)
        }

    fun retrieveCategory(
        browseId: String,
        params: String,
        settings: UmihiSettings,
        useCache: Boolean = true,
    ): Flow<ApiResult<List<HomeFeedSection>>> =
        cachedApiFlow(key = "moods:$browseId:$params", useCache = useCache) {
            dataSource.retrieveCategory(browseId, params, settings)
        }
}
