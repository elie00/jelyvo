package ca.ilianokokoro.umihi.music.models.adapters

import ca.ilianokokoro.umihi.music.models.AlbumPage
import ca.ilianokokoro.umihi.music.models.Artist
import ca.ilianokokoro.umihi.music.models.ArtistAlbum
import ca.ilianokokoro.umihi.music.models.ArtistPage
import ca.ilianokokoro.umihi.music.models.ArtistSection
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionCatalogAdapterTest {
    @Test
    fun `round trips an album page through the catalog boundary`() {
        val original = AlbumPage(
            name = "Album",
            songs = listOf(
                Song(youtubeId = "t1", title = "One", artist = "Artist").also {
                    it.trackNumber = 1
                },
                Song(youtubeId = "t2", title = "Two", artist = "Artist").also {
                    it.trackNumber = 2
                },
            ),
            cover = "https://cover",
            artist = "Artist",
            artistId = "UCartist",
            subtitle = "Album • 2023",
            secondSubtitle = "2 songs • 7 minutes",
            description = "Editorial notes",
            sections = listOf(
                ArtistSection(
                    title = "Other versions",
                    albums = listOf(
                        ArtistAlbum(
                            browseId = "MPRE1",
                            title = "Deluxe",
                            thumbnail = "https://deluxe",
                            subtitle = "Album • 2024",
                        )
                    ),
                    artists = listOf(
                        Artist(name = "Related", thumbnailHref = "https://face", channelUrl = "UCrelated")
                    ),
                    moreBrowseId = "MORE",
                    moreParams = "PARAMS",
                )
            ),
            playlistId = "OLAK5uy",
            isSavedToLibrary = true,
        )

        val restored = original.toCatalogAlbumPage().toAlbumPage()

        assertEquals(original.name, restored.name)
        assertEquals(original.cover, restored.cover)
        assertEquals(original.artist, restored.artist)
        assertEquals(original.artistId, restored.artistId)
        assertEquals(original.subtitle, restored.subtitle)
        assertEquals(original.secondSubtitle, restored.secondSubtitle)
        assertEquals(original.description, restored.description)
        assertEquals(original.playlistId, restored.playlistId)
        assertEquals(original.isSavedToLibrary, restored.isSavedToLibrary)
        assertEquals(listOf("t1", "t2"), restored.songs.map(Song::youtubeId))
        assertEquals(listOf(1, 2), restored.songs.map(Song::trackNumber))

        val section = restored.sections.single()
        assertEquals("Other versions", section.title)
        assertEquals("MPRE1", section.albums.single().browseId)
        assertEquals("Album • 2024", section.albums.single().subtitle)
        assertEquals("UCrelated", section.artists.single().channelUrl)
        assertEquals("MORE", section.moreBrowseId)
        assertEquals("PARAMS", section.moreParams)
    }

    @Test
    fun `round trips an artist page with its popularity ranking`() {
        val original = ArtistPage(
            artist = Artist(
                name = "Artist",
                thumbnailHref = "https://face",
                channelUrl = "UCartist",
                description = "Bio",
                subscriberCount = "12M subscribers",
            ),
            songs = listOf(Song(youtubeId = "all1", title = "Deep cut", artist = "Artist")),
            topSongs = listOf(
                Song(youtubeId = "top1", title = "Hit", artist = "Artist").also {
                    it.playCount = "369 M plays"
                },
            ),
            sections = listOf(ArtistSection(title = "Albums")),
        )

        val restored = original.toCatalogArtistPage().toArtistPage()

        assertEquals(original.artist, restored.artist)
        assertEquals(listOf("all1"), restored.songs.map(Song::youtubeId))
        assertEquals(listOf("top1"), restored.topSongs.map(Song::youtubeId))
        assertEquals("369 M plays", restored.topSongs.single().playCount)
        assertEquals("Albums", restored.sections.single().title)
    }

    @Test
    fun `round trips a remote playlist through the catalog boundary`() {
        val original = Playlist(
            info = PlaylistInfo(id = "PL123", title = "Route", coverHref = "https://cover"),
            songs = listOf(
                Song(youtubeId = "s1", title = "One", artist = "Artist", duration = "3:05"),
                Song(youtubeId = "s2", title = "Two", artist = "Artist", duration = "2:45"),
            ),
        )

        val restored = original.toCatalogPlaylistPage().toPlaylist()

        assertEquals(original.info.id, restored.info.id)
        assertEquals(original.info.title, restored.info.title)
        assertEquals(original.info.coverHref, restored.info.coverHref)
        assertEquals(listOf("s1", "s2"), restored.songs.map(Song::youtubeId))
        assertEquals(listOf("3:05", "2:45"), restored.songs.map(Song::duration))
    }

    @Test
    fun `blank shelf more id maps to an absent token`() {
        val shelf = ArtistSection(title = "Videos").toCatalogShelf()
        assertNull(shelf.moreToken)
        assertEquals("", shelf.toArtistSection().moreBrowseId)
    }
}
