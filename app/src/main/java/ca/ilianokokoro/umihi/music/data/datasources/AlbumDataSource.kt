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
        val header = YoutubeDataExtractor.extractAlbumHeaderInfo(response)
        return AlbumPage(
            // Prefer the title YouTube reports; the nav-passed name can be stale/truncated.
            name = header?.title?.takeIf { it.isNotBlank() } ?: albumName,
            songs = YoutubeDataExtractor.extractAlbumSongs(response, settings),
            cover = (YoutubeDataExtractor.extractAlbumCover(response)
                ?: YoutubeDataExtractor.extractLargestThumbnail(response)).orEmpty(),
            artist = header?.artist.orEmpty(),
            artistId = header?.artistId,
            subtitle = header?.subtitle.orEmpty(),
            secondSubtitle = header?.secondSubtitle.orEmpty(),
            description = header?.description.orEmpty(),
            sections = YoutubeDataExtractor.extractAlbumSections(response),
            playlistId = YoutubeDataExtractor.extractAlbumPlaylistId(response),
            isSavedToLibrary = YoutubeDataExtractor.extractAlbumLibraryStatus(response),
        )
    }
}
