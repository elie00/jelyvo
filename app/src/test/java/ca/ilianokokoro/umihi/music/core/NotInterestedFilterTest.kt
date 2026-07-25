package ca.ilianokokoro.umihi.music.core

import ca.ilianokokoro.umihi.music.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotInterestedFilterTest {

    private fun song(id: String, uid: String, isSuggestion: Boolean = false) =
        Song(youtubeId = id, uid = uid).also { it.isSuggestion = isSuggestion }

    @Test
    fun `add puts the newest rejection first and removes duplicates`() {
        assertEquals(
            listOf("a", "b", "c"),
            NotInterestedFilter.add(listOf("b", "a", "c"), "a", max = 10),
        )
    }

    @Test
    fun `add drops the oldest rejection past the cap`() {
        assertEquals(
            listOf("a", "b"),
            NotInterestedFilter.add(listOf("b", "c"), "a", max = 2),
        )
    }

    @Test
    fun `add ignores a blank id`() {
        val current = listOf("a")
        assertEquals(current, NotInterestedFilter.add(current, " ", max = 10))
    }

    @Test
    fun `remove drops only the requested id`() {
        assertEquals(
            listOf("a", "c"),
            NotInterestedFilter.remove(listOf("a", "b", "c"), "b"),
        )
    }

    @Test
    fun `stored ids round trip and corrupt storage is harmless`() {
        val ids = listOf("a", "b")
        assertEquals(ids, NotInterestedFilter.decode(NotInterestedFilter.encode(ids)))
        assertEquals(emptyList<String>(), NotInterestedFilter.decode(null))
        assertEquals(emptyList<String>(), NotInterestedFilter.decode("{not json"))
    }

    @Test
    fun `fresh continuations exclude rejected tracks and preserve order`() {
        val songs = listOf(song("a", "1"), song("b", "2"), song("c", "3"))
        assertEquals(
            listOf("a", "c"),
            NotInterestedFilter.excludeHidden(songs, setOf("b")).map { it.youtubeId },
        )
    }

    @Test
    fun `queue pruning only targets autoplay entries`() {
        val queue = listOf(
            song("a", "1"),
            song("b", "2"),
            song("b", "3", isSuggestion = true),
        )
        assertEquals(
            listOf(2),
            NotInterestedFilter.hiddenSuggestionIndices(queue, setOf("b"), currentIndex = 0),
        )
    }

    @Test
    fun `queue pruning never removes the item playing`() {
        val queue = listOf(
            song("b", "1", isSuggestion = true),
            song("b", "2", isSuggestion = true),
        )
        assertEquals(
            listOf(1),
            NotInterestedFilter.hiddenSuggestionIndices(queue, setOf("b"), currentIndex = 0),
        )
    }

    @Test
    fun `a one-item queue cannot be emptied`() {
        val queue = listOf(song("b", "1", isSuggestion = true))
        assertTrue(
            NotInterestedFilter.hiddenSuggestionIndices(
                queue,
                setOf("b"),
                currentIndex = 0,
            ).isEmpty()
        )
    }

    @Test
    fun `current index follows removals before it`() {
        assertEquals(2, NotInterestedFilter.indexAfterRemovals(4, listOf(0, 3)))
        assertEquals(4, NotInterestedFilter.indexAfterRemovals(4, listOf(5, 7)))
    }

    @Test
    fun `the persisted cap is enforced`() {
        val current = (1..Constants.Datastore.NOT_INTERESTED_MAX).map { "id-$it" }
        val updated = NotInterestedFilter.add(
            current,
            "newest",
            Constants.Datastore.NOT_INTERESTED_MAX,
        )
        assertEquals(Constants.Datastore.NOT_INTERESTED_MAX, updated.size)
        assertEquals("newest", updated.first())
    }
}
