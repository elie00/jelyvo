package ca.ilianokokoro.umihi.music.data.datasources

import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.models.AlbumPage
import ca.ilianokokoro.umihi.music.models.UmihiSettings

class AlbumDataSource {
    suspend fun getAlbum(
        albumId: String,
        albumName: String,
        settings: UmihiSettings,
    ): AlbumPage {
        val response = YoutubeApiClient.browse(albumId, settings)
        return AlbumPage(
            name = albumName,
            songs = YoutubeDataExtractor.extractAlbumSongs(response, settings),
            cover = YoutubeDataExtractor.extractLargestThumbnail(response).orEmpty(),
        )
    }
}
