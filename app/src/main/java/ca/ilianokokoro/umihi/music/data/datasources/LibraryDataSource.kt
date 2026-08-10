package ca.ilianokokoro.umihi.music.data.datasources

import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiConstants
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.models.ArtistAlbum
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.UmihiSettings

class LibraryDataSource {
    /** The account's "Liked Music" auto-playlist ("LM"), playable as a queue. */
    suspend fun retrieveLikedSongs(settings: UmihiSettings): List<Song> {
        return YoutubeDataExtractor.extractSongList(
            YoutubeApiClient.browse(
                YoutubeApiConstants.Browse.LIKED_SONGS_BROWSE_ID,
                settings,
            ), settings
        )
    }

    /** The albums the account added to its library. */
    suspend fun retrieveSavedAlbums(settings: UmihiSettings): List<SearchItem.Album> {
        return YoutubeDataExtractor.extractLibraryAlbums(
            YoutubeApiClient.browse(
                YoutubeApiConstants.Browse.LIKED_ALBUMS_BROWSE_ID,
                settings,
            )
        )
    }

    /**
     * The artists the account follows. The dedicated corpus feed is tried first; some accounts
     * only expose them through the library landing page, used as a fallback.
     */
    suspend fun retrieveFollowedArtists(settings: UmihiSettings): List<SearchItem.Artist> {
        val artists = YoutubeDataExtractor.extractLibraryArtists(
            YoutubeApiClient.browse(
                YoutubeApiConstants.Browse.LIBRARY_ARTISTS_BROWSE_ID,
                settings,
            )
        )
        if (artists.isNotEmpty()) {
            return artists
        }

        return YoutubeDataExtractor.extractLibraryArtists(
            YoutubeApiClient.browse(
                YoutubeApiConstants.Browse.LIBRARY_LANDING_BROWSE_ID,
                settings,
            )
        )
    }

    /**
     * The releases (albums, singles & EPs) shown on an artist's page ("UC…" browseId), across all
     * of its carousel sections.
     */
    suspend fun retrieveArtistReleases(
        artistId: String,
        settings: UmihiSettings,
    ): List<ArtistAlbum> {
        return YoutubeDataExtractor.extractArtistSections(
            YoutubeApiClient.browse(artistId, settings)
        ).flatMap { it.albums }.distinctBy { it.browseId }
    }
}
