package ca.ilianokokoro.umihi.music.data.datasources

import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiConstants
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import ca.ilianokokoro.umihi.music.models.MoodCategorySection
import ca.ilianokokoro.umihi.music.models.UmihiSettings

class MoodsDataSource {
    /** The "Moods & genres" hub: sections of category chips. */
    suspend fun retrieveCategories(settings: UmihiSettings): List<MoodCategorySection> =
        YoutubeDataExtractor.extractMoodsAndGenres(
            YoutubeApiClient.browse(
                YoutubeApiConstants.Browse.MOODS_AND_GENRES_BROWSE_ID,
                settings
            )
        )

    /** One category's page (playlists/stations), opened via its params browse. */
    suspend fun retrieveCategory(
        browseId: String,
        params: String,
        settings: UmihiSettings,
    ): List<HomeFeedSection> =
        YoutubeDataExtractor.extractBrowseSections(
            YoutubeApiClient.browse(browseId, params, settings)
        )
}
