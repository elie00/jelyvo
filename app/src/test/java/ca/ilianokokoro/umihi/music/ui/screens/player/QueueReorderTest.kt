package ca.ilianokokoro.umihi.music.ui.screens.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Queue drag-and-drop is limited to the user part of the queue: autoplay suggestions (the
 * contiguous tail marked isSuggestion) must neither be dragged nor become drop targets, otherwise
 * a suggestion would silently join the user queue while PlayerManager still counts it as one.
 */
class QueueReorderTest {

    // "a".."c" are user items, "S1"/"S2" are the autoplay suggestion tail.
    private val queue = listOf("a", "b", "c", "S1", "S2")
    private val isSuggestion: (String) -> Boolean = { it.startsWith("S") }

    @Test
    fun `moves a user item inside the user queue`() {
        assertEquals(
            listOf("b", "a", "c", "S1", "S2"),
            moveWithinUserQueue(queue, 0, 1, isSuggestion),
        )
    }

    @Test
    fun `a drop past the boundary lands on the last user slot`() {
        assertEquals(
            listOf("b", "c", "a", "S1", "S2"),
            moveWithinUserQueue(queue, 0, 4, isSuggestion),
        )
    }

    @Test
    fun `dragging a suggestion is a no-op`() {
        assertSame(queue, moveWithinUserQueue(queue, 3, 0, isSuggestion))
    }

    @Test
    fun `identity and out-of-range moves return the same instance`() {
        assertSame(queue, moveWithinUserQueue(queue, 1, 1, isSuggestion))
        assertSame(queue, moveWithinUserQueue(queue, -1, 1, isSuggestion))
        assertSame(queue, moveWithinUserQueue(queue, 99, 0, isSuggestion))
    }

    @Test
    fun `a clamped drop that resolves to the source index is a no-op`() {
        // Dragging the last user item into the suggestion zone clamps back onto itself.
        assertSame(queue, moveWithinUserQueue(queue, 2, 3, isSuggestion))
    }

    @Test
    fun `a queue without suggestions is fully reorderable`() {
        val plain = listOf("a", "b", "c")
        assertEquals(
            listOf("b", "c", "a"),
            moveWithinUserQueue(plain, 0, 2) { false },
        )
    }
}
