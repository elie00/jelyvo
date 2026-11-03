package ca.ilianokokoro.umihi.music.ui.screens.equalizer

import org.junit.Assert.assertEquals
import org.junit.Test

class FrequencyLabelTest {
    @Test
    fun `decimals use a French comma`() {
        assertEquals("3,6 kHz", formatFrequency(3_600_000))
    }

    @Test
    fun `round frequencies have no decimals`() {
        assertEquals("14 kHz", formatFrequency(14_000_000))
        assertEquals("60 Hz", formatFrequency(60_000))
    }
}
