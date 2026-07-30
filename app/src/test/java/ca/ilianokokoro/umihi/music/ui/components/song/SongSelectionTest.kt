package ca.ilianokokoro.umihi.music.ui.components.song

import org.junit.Assert.assertEquals
import org.junit.Test

class SongSelectionTest {
    private val ids = listOf("a", "b", "c", "d", "e")

    @Test
    fun `command click toggles one song and sets the anchor`() {
        val selected = updateSongSelection(
            orderedIds = ids,
            selectedIds = emptySet(),
            anchorId = null,
            clickedId = "b",
            modifiers = SongSelectionModifiers(toggle = true),
        )

        assertEquals(setOf("b"), selected.selectedIds)
        assertEquals("b", selected.anchorId)
    }

    @Test
    fun `second command click removes an already selected song`() {
        val selected = updateSongSelection(
            orderedIds = ids,
            selectedIds = setOf("b", "d"),
            anchorId = "d",
            clickedId = "b",
            modifiers = SongSelectionModifiers(toggle = true),
        )

        assertEquals(setOf("d"), selected.selectedIds)
        assertEquals("b", selected.anchorId)
    }

    @Test
    fun `shift click selects an inclusive ordered range`() {
        val selected = updateSongSelection(
            orderedIds = ids,
            selectedIds = setOf("b"),
            anchorId = "b",
            clickedId = "e",
            modifiers = SongSelectionModifiers(range = true),
        )

        assertEquals(linkedSetOf("b", "c", "d", "e"), selected.selectedIds)
        assertEquals("e", selected.anchorId)
    }

    @Test
    fun `command shift click adds a range to the existing selection`() {
        val selected = updateSongSelection(
            orderedIds = ids,
            selectedIds = setOf("a"),
            anchorId = "c",
            clickedId = "e",
            modifiers = SongSelectionModifiers(toggle = true, range = true),
        )

        assertEquals(setOf("a", "c", "d", "e"), selected.selectedIds)
    }

    @Test
    fun `plain click toggles while selection mode is active`() {
        val selected = updateSongSelection(
            orderedIds = ids,
            selectedIds = setOf("a"),
            anchorId = "a",
            clickedId = "c",
            modifiers = SongSelectionModifiers(),
        )

        assertEquals(setOf("a", "c"), selected.selectedIds)
        assertEquals("c", selected.anchorId)
    }

    @Test
    fun `unknown ids leave selection untouched`() {
        val selected = updateSongSelection(
            orderedIds = ids,
            selectedIds = setOf("a"),
            anchorId = "a",
            clickedId = "missing",
            modifiers = SongSelectionModifiers(toggle = true),
        )

        assertEquals(setOf("a"), selected.selectedIds)
        assertEquals("a", selected.anchorId)
    }
}
