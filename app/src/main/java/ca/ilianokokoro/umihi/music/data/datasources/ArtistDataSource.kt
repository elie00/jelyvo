package ca.ilianokokoro.umihi.music.data.datasources

import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.models.Artist
import ca.ilianokokoro.umihi.music.models.ArtistAlbum
import ca.ilianokokoro.umihi.music.models.ArtistPage
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads a YouTube Music artist page via the innertube browse endpoint using the artist's browseId
 * ("UC…") captured from search results. This returns the official artist page (songs, header),
 * unlike the auto-generated "… - Topic" channels.
 */
class ArtistDataSource {
    suspend fun getArtistReleases(
        browseId: String,
        params: String,
        settings: UmihiSettings,
    ): List<ArtistAlbum> = withContext(Dispatchers.IO) {
        val response = if (params.isBlank()) {
            YoutubeApiClient.browse(browseId, settings)
        } else {
            YoutubeApiClient.browse(browseId, params, settings)
        }
        YoutubeDataExtractor.extractArtistAlbums(response)
    }

    suspend fun getArtistPage(
        artistId: String,
        fallbackName: String,
        settings: UmihiSettings,
    ): ArtistPage = withContext(Dispatchers.IO) {
        val response = YoutubeApiClient.browse(artistId, settings)

        // The shelf as YouTube Music ranks it: most-streamed first, with play counts.
        val topSongs = YoutubeDataExtractor.extractArtistSongs(response)
        var songs = topSongs

        // Follow the Songs shelf's "Show all" endpoint to load the full song list, not just the top
        // few. This list is ordered differently and carries no play counts, which is why the top
        // shelf is kept alongside it rather than overwritten.
        YoutubeDataExtractor.extractMoreEndpoint(response)?.let { (moreId, params) ->
            runCatching {
                val moreSongs = YoutubeDataExtractor.extractArtistSongs(
                    YoutubeApiClient.browse(moreId, params, settings)
                )
                if (moreSongs.size > songs.size) {
                    songs = moreSongs
                }
            }
        }

        val name = YoutubeDataExtractor.extractArtistName(response)?.takeIf { it.isNotBlank() }
            ?: fallbackName
        val thumbnail = YoutubeDataExtractor.extractArtistThumbnail(response)
            ?: songs.firstOrNull()?.thumbnailHref.orEmpty()
        val sections = YoutubeDataExtractor.extractArtistSections(response)

        ArtistPage(
            artist = Artist(
                name = name,
                thumbnailHref = thumbnail,
                channelUrl = artistId,
                description = YoutubeDataExtractor.extractArtistDescription(response),
                subscriberCount = YoutubeDataExtractor.extractArtistSubscriberCount(response),
            ),
            songs = songs,
            topSongs = topSongs,
            sections = sections,
        )
    }
}
