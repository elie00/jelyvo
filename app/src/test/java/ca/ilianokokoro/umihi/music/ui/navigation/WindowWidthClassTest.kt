package ca.ilianokokoro.umihi.music.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowWidthClassTest {
    @Test
    fun `phone widths stay compact`() {
        assertEquals(WindowWidthClass.Compact, classifyWindowWidth(0))
        assertEquals(WindowWidthClass.Compact, classifyWindowWidth(599))
    }

    @Test
    fun `tablet widths use the medium tier`() {
        assertEquals(WindowWidthClass.Medium, classifyWindowWidth(600))
        assertEquals(WindowWidthClass.Medium, classifyWindowWidth(839))
    }

    @Test
    fun `large and desktop windows use the expanded tier`() {
        assertEquals(WindowWidthClass.Expanded, classifyWindowWidth(840))
        assertEquals(WindowWidthClass.Expanded, classifyWindowWidth(1920))
    }
}
