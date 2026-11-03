package ca.ilianokokoro.umihi.music.wearapp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class WatchLocaleTest {
    @Test
    fun `the watch speaks French when set to English`() {
        assertEquals("fr", WatchLocale.frenchFor(Locale.US).language)
    }

    @Test
    fun `the watch keeps its region`() {
        assertEquals("US", WatchLocale.frenchFor(Locale.US).country)
    }
}
