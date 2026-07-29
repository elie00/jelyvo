package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.cachedApiFlow
import ca.ilianokokoro.umihi.music.core.localizedCacheKey
import ca.ilianokokoro.umihi.music.data.datasources.ArtistDataSource
import ca.ilianokokoro.umihi.music.models.ArtistPage
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.flow.Flow

class ArtistRepository {
    private val artistDataSource = ArtistDataSource()

    fun getArtist(
        artistId: String,
        artistName: String,
        settings: UmihiSettings,
    ): Flow<ApiResult<ArtistPage>> =
        cachedApiFlow(
            key = localizedCacheKey("artist:${settings.dataSyncId.orEmpty()}:$artistId"),
            diskSerializer = ArtistPage.serializer(),
        ) {
            artistDataSource.getArtistPage(artistId, artistName, settings)
        }
}
