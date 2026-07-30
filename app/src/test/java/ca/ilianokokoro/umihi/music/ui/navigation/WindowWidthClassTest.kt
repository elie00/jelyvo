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

    @Test
    fun `a permanent pane requires expanded width and usable height`() {
        assertEquals(false, supportsPermanentSecondaryPane(widthDp = 839, heightDp = 900))
        assertEquals(false, supportsPermanentSecondaryPane(widthDp = 900, heightDp = 479))
        assertEquals(true, supportsPermanentSecondaryPane(widthDp = 840, heightDp = 480))
        assertEquals(true, supportsPermanentSecondaryPane(widthDp = 1200, heightDp = 900))
    }

    @Test
    fun `app wide playback pane is reserved for desktop sized windows`() {
        assertEquals(false, supportsPermanentPlaybackPane(widthDp = 999, heightDp = 900))
        assertEquals(false, supportsPermanentPlaybackPane(widthDp = 1200, heightDp = 599))
        assertEquals(true, supportsPermanentPlaybackPane(widthDp = 1000, heightDp = 600))
        assertEquals(true, supportsPermanentPlaybackPane(widthDp = 1600, heightDp = 1000))
    }

    @Test
    fun `library companion is reserved for very wide desktop windows`() {
        assertEquals(false, supportsPermanentLibraryPane(widthDp = 1359, heightDp = 900))
        assertEquals(false, supportsPermanentLibraryPane(widthDp = 1600, heightDp = 699))
        assertEquals(true, supportsPermanentLibraryPane(widthDp = 1360, heightDp = 700))
        assertEquals(true, supportsPermanentLibraryPane(widthDp = 1920, heightDp = 1080))
    }
}
