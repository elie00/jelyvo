package ca.ilianokokoro.umihi.music.ui.screens.player.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsPanelTest {

    @Test
    fun `active synced line progressively highlights words`() {
        val text = karaokeText(
            text = "one two three four",
            progress = 0.5f,
            highlightedColor = Color.Red,
            enabled = true,
        )

        assertEquals("one two three four", text.text)
        assertEquals(2, text.spanStyles.size)
        assertTrue(text.spanStyles.all { it.item.color == Color.Red })
    }

    @Test
    fun `inactive line remains unstyled`() {
        val text = karaokeText(
            text = "one two",
            progress = 1f,
            highlightedColor = Color.Red,
            enabled = false,
        )

        assertEquals("one two", text.text)
        assertTrue(text.spanStyles.isEmpty())
    }
}
