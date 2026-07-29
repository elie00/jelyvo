package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.cachedApiFlow
import ca.ilianokokoro.umihi.music.core.localizedCacheKey
import ca.ilianokokoro.umihi.music.data.datasources.MoodsDataSource
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import ca.ilianokokoro.umihi.music.models.MoodCategorySection
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer

class MoodsRepository {
    private val dataSource = MoodsDataSource()

    fun retrieveCategories(
        settings: UmihiSettings,
        useCache: Boolean = true,
    ): Flow<ApiResult<List<MoodCategorySection>>> =
        cachedApiFlow(
            key = localizedCacheKey("moods"),
            useCache = useCache,
            diskSerializer = ListSerializer(MoodCategorySection.serializer()),
        ) {
            dataSource.retrieveCategories(settings)
        }

    fun retrieveCategory(
        browseId: String,
        params: String,
        settings: UmihiSettings,
        useCache: Boolean = true,
    ): Flow<ApiResult<List<HomeFeedSection>>> =
        cachedApiFlow(
            key = localizedCacheKey("moods:$browseId:$params"),
            useCache = useCache,
            diskSerializer = ListSerializer(HomeFeedSection.serializer()),
        ) {
            dataSource.retrieveCategory(browseId, params, settings)
        }
}
