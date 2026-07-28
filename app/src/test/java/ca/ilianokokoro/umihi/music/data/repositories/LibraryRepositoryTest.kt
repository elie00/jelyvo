package ca.ilianokokoro.umihi.music.data.repositories

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryRepositoryTest {

    @Test
    fun `new release subtitle starts with artist`() {
        assertEquals(
            "Werenoi • Single • 2026",
            newReleaseSubtitle("Werenoi", "Single • 2026"),
        )
    }

    @Test
    fun `new release subtitle does not duplicate artist`() {
        assertEquals(
            "Werenoi • Single • 2026",
            newReleaseSubtitle("Werenoi", "Werenoi • Single • 2026"),
        )
    }

    @Test
    fun `new release subtitle falls back to artist`() {
        assertEquals("Werenoi", newReleaseSubtitle("Werenoi", ""))
    }
}
