package ca.ilianokokoro.umihi.music.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class DraggedTabPositionTest {
    private val step = 100f
    private val last = 2

    @Test
    fun `no drag stays on the starting tab`() {
        assertEquals(1f, draggedTabPosition(1, 0f, step, last), 0f)
    }

    @Test
    fun `dragging moves in tab units`() {
        assertEquals(1.5f, draggedTabPosition(1, 50f, step, last), 0f)
        assertEquals(0.25f, draggedTabPosition(1, -75f, step, last), 0f)
    }

    @Test
    fun `a drag cannot leave the bar`() {
        assertEquals(0f, draggedTabPosition(0, -500f, step, last), 0f)
        assertEquals(2f, draggedTabPosition(2, 500f, step, last), 0f)
    }
}
