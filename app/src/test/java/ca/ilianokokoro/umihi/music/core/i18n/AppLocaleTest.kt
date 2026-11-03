package ca.ilianokokoro.umihi.music.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLocaleTest {
    @Test
    fun `the app speaks French on a phone set to English`() {
        assertEquals("fr", AppLocale.frenchFor(Locale.US).language)
    }

    @Test
    fun `the phone's region still decides what music is on offer`() {
        assertEquals("US", AppLocale.frenchFor(Locale.US).country)
        assertEquals("CA", AppLocale.frenchFor(Locale.CANADA_FRENCH).country)
    }

    @Test
    fun `a phone with no region still gets French`() {
        assertEquals(Locale.FRENCH, AppLocale.frenchFor(Locale.ROOT))
    }
}
