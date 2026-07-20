package ca.ilianokokoro.umihi.music.data.datasources

import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import ca.ilianokokoro.umihi.music.models.UmihiSettings

class HomeDataSource {
    suspend fun retrieve(settings: UmihiSettings): List<HomeFeedSection> =
        YoutubeDataExtractor.extractHomeFeed(
            YoutubeApiClient.browse(HOME_BROWSE_ID, settings)
        )

    private companion object {
        const val HOME_BROWSE_ID = "FEmusic_home"
    }
}
