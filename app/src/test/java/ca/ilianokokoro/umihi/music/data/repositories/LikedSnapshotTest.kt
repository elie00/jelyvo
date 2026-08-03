package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LikedSnapshotTest {
    private val first = Song(youtubeId = "first", title = "First")
    private val second = Song(youtubeId = "second", title = "Second")

    @Test
    fun `new like is immediately placed first without duplicates`() {
        val updated = updateLikedSongsSnapshot(
            songs = listOf(first, second),
            changedSong = second.copy(title = "Second updated"),
            liked = true,
        )

        assertEquals(listOf("second", "first"), updated.map(Song::youtubeId))
        assertEquals("Second updated", updated.first().title)
        assertTrue(updated.first().isLiked == true)
    }

    @Test
    fun `unlike disappears from the persistent snapshot`() {
        val updated = updateLikedSongsSnapshot(
            songs = listOf(first, second),
            changedSong = first,
            liked = false,
        )

        assertEquals(listOf("second"), updated.map(Song::youtubeId))
    }
}
