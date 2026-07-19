package ca.ilianokokoro.umihi.music.data.datasources

import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.UmihiSettings

class AlbumDataSource {
    suspend fun getAlbumSongs(albumId: String, settings: UmihiSettings): List<Song> {
        return YoutubeDataExtractor.extractAlbumSongs(
            YoutubeApiClient.browse(albumId, settings),
            settings
        )
    }
}
