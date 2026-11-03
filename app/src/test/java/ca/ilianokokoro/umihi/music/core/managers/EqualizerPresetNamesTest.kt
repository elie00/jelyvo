package ca.ilianokokoro.umihi.music.core.managers

import org.junit.Assert.assertEquals
import org.junit.Test

class EqualizerPresetNamesTest {
    @Test
    fun `the system's preset names read in French`() {
        assertEquals("Classique", frenchPresetName("Classical"))
        assertEquals("Plat", frenchPresetName("Flat"))
        assertEquals("Hip-hop", frenchPresetName("Hip Hop"))
    }

    @Test
    fun `a preset this list does not know keeps its name`() {
        assertEquals("Salle de concert", frenchPresetName("Salle de concert"))
    }
}
