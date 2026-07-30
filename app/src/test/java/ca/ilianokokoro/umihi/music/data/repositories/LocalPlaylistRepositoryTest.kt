package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong
import ca.ilianokokoro.umihi.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalPlaylistRepositoryTest {
    @Test
    fun `append preserves order and maps song metadata`() {
        val existing = listOf(
            LocalPlaylistSong(id = "first", title = "First", artist = "Artist"),
        )
        val dropped = Song(
            youtubeId = "second",
            title = "Second",
            artist = "Another artist",
            duration = "3:21",
            thumbnailHref = "https://example.test/cover.jpg",
        )

        val result = appendSongIfMissing(existing, dropped)

        requireNotNull(result)
        assertEquals(2, result.size)
        assertEquals(existing.single(), result.first())
        assertEquals(dropped.youtubeId, result.last().id)
        assertEquals(dropped.title, result.last().title)
        assertEquals(dropped.artist, result.last().artist)
        assertEquals(dropped.duration, result.last().duration)
        assertEquals(dropped.thumbnailHref, result.last().thumbnailHref)
    }

    @Test
    fun `duplicate media id is rejected`() {
        val existing = listOf(LocalPlaylistSong(id = "same", title = "Original"))

        val result = appendSongIfMissing(
            existing = existing,
            song = Song(youtubeId = "same", title = "Duplicate"),
        )

        assertNull(result)
    }
}
