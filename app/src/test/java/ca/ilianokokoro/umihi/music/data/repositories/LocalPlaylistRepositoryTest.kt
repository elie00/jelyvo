package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.domain.ArtistRef
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalPlaylistRepositoryTest {
    @Test
    fun `append preserves order and maps song metadata`() {
        val existing = listOf(
            LocalPlaylistSong(id = "first", title = "First", artist = "Artist"),
        )
        val dropped = Track(
            id = "second",
            title = "Second",
            artist = ArtistRef(name = "Another artist"),
            durationMs = 201_000L,
            artworkUrl = "https://example.test/cover.jpg",
        )

        val result = mergeTracksIntoLocalPlaylist(existing, listOf(dropped))

        assertEquals(1, result.addedCount)
        assertEquals(2, result.songs.size)
        assertEquals(existing.single(), result.songs.first())
        assertEquals(dropped.id, result.songs.last().id)
        assertEquals(dropped.title, result.songs.last().title)
        assertEquals(dropped.artist.name, result.songs.last().artist)
        assertEquals("3:21", result.songs.last().duration)
        assertEquals(dropped.artworkUrl, result.songs.last().thumbnailHref)
    }

    @Test
    fun `duplicates and blank ids are rejected while new order is preserved`() {
        val existing = listOf(LocalPlaylistSong(id = "same", title = "Original"))

        val result = mergeTracksIntoLocalPlaylist(
            existing = existing,
            tracks = listOf(
                Track(id = "same", title = "Existing duplicate"),
                Track(id = "", title = "Blank"),
                Track(id = "new", title = "First new"),
                Track(id = "new", title = "New duplicate"),
                Track(id = "last", title = "Last new"),
            ),
        )

        assertEquals(2, result.addedCount)
        assertEquals(listOf("same", "new", "last"), result.songs.map { it.id })
    }
}
