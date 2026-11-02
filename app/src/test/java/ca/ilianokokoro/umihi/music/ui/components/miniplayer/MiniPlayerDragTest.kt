package ca.ilianokokoro.umihi.music.ui.components.miniplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class MiniPlayerDragTest {
    @Test
    fun `straight up is a pull`() {
        assertEquals(MiniPlayerDrag.Pull, classifyMiniPlayerDrag(dx = 0f, dy = -20f))
    }

    @Test
    fun `an upward arc is still a pull`() {
        assertEquals(MiniPlayerDrag.Pull, classifyMiniPlayerDrag(dx = 25f, dy = -20f))
    }

    @Test
    fun `mostly sideways changes track`() {
        assertEquals(MiniPlayerDrag.Sideways, classifyMiniPlayerDrag(dx = 40f, dy = -20f))
        assertEquals(MiniPlayerDrag.Sideways, classifyMiniPlayerDrag(dx = -40f, dy = 5f))
    }

    @Test
    fun `dragging down does nothing on the bar`() {
        assertEquals(MiniPlayerDrag.Ignored, classifyMiniPlayerDrag(dx = 5f, dy = 40f))
    }
}
