package ca.ilianokokoro.umihi.music.ui.screens.player.components

import ca.ilianokokoro.umihi.music.models.LyricLine
import ca.ilianokokoro.umihi.music.models.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsPanelTest {
    private val line = LyricLine(
        timeMs = 1_000L,
        text = "One more",
        words = listOf(
            LyricWord(1_000L, 2_000L, "One"),
            LyricWord(3_000L, 4_000L, "more"),
        ),
    )

    @Test
    fun `nothing is sung before the first word`() {
        assertEquals(0f, sungCharPosition(line, 500L), 0f)
    }

    @Test
    fun `the sweep moves through a word at its own pace`() {
        assertEquals(1.5f, sungCharPosition(line, 1_500L), 0.001f)
        assertEquals(6f, sungCharPosition(line, 3_500L), 0.001f)
    }

    @Test
    fun `between two words the sweep rests at the end of the first`() {
        assertEquals(3f, sungCharPosition(line, 2_500L), 0f)
    }

    @Test
    fun `a finished line is fully sung`() {
        assertEquals(line.text.length.toFloat(), sungCharPosition(line, 9_000L), 0f)
    }

    @Test
    fun `a repeated word is found at its own place`() {
        val repeated = LyricLine(
            timeMs = 0L,
            text = "la la",
            words = listOf(LyricWord(0L, 100L, "la"), LyricWord(100L, 200L, "la")),
        )

        assertEquals(4f, sungCharPosition(repeated, 150L), 0.001f)
    }

    @Test
    fun `a word missing from the text does not break the sweep`() {
        val mismatched = LyricLine(
            timeMs = 0L,
            text = "kept",
            words = listOf(LyricWord(0L, 100L, "gone"), LyricWord(100L, 200L, "kept")),
        )

        assertEquals(2f, sungCharPosition(mismatched, 150L), 0.001f)
    }

    @Test
    fun `lines fade with distance from the active one, on both sides`() {
        assertEquals(1f, lineAlpha(0), 0f)
        assertEquals(lineAlpha(2), lineAlpha(-2), 0f)
        assertTrue(lineAlpha(1) > lineAlpha(3))
    }

    @Test
    fun `lines far away stay readable at the floor`() {
        assertEquals(lineAlpha(5), lineAlpha(40), 0f)
    }

    @Test
    fun `small backward reports do not stutter the sweep`() {
        assertFalse(acceptPositionReport(displayedMs = 10_100L, reportedMs = 10_000L))
    }

    @Test
    fun `reports ahead and seeks are taken`() {
        assertTrue(acceptPositionReport(displayedMs = 10_000L, reportedMs = 10_150L))
        assertTrue(acceptPositionReport(displayedMs = 60_000L, reportedMs = 5_000L))
    }
}
