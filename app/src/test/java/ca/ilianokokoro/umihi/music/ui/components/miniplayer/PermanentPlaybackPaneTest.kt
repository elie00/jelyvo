package ca.ilianokokoro.umihi.music.ui.components.miniplayer

import ca.ilianokokoro.umihi.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PermanentPlaybackPaneTest {
    private val queue = listOf(
        Song(youtubeId = "first"),
        Song(youtubeId = "second"),
        Song(youtubeId = "third"),
    )

    @Test
    fun `up next keeps controller indices after current song`() {
        val result = upNextQueueEntries(queue, currentIndex = 0)

        assertEquals(listOf(1, 2), result.map(IndexedValue<Song>::index))
        assertEquals(listOf("second", "third"), result.map { it.value.youtubeId })
    }

    @Test
    fun `last or invalid current song has no up next entries`() {
        assertEquals(emptyList<IndexedValue<Song>>(), upNextQueueEntries(queue, 2))
        assertEquals(emptyList<IndexedValue<Song>>(), upNextQueueEntries(queue, -1))
        assertEquals(emptyList<IndexedValue<Song>>(), upNextQueueEntries(queue, 3))
    }
}
