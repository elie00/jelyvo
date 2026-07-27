package ca.ilianokokoro.umihi.music.core

import ca.ilianokokoro.umihi.music.core.helpers.VersionCompare
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCompareTest {

    @Test
    fun `a strictly higher release is newer`() {
        assertTrue(VersionCompare.isNewer(incoming = "2.1.0", current = "2.0.0"))
        assertTrue(VersionCompare.isNewer(incoming = "3.0.0", current = "2.9.9"))
        assertTrue(VersionCompare.isNewer(incoming = "2.0.1", current = "2.0.0"))
    }

    @Test
    fun `the same or an older release is not newer`() {
        assertFalse(VersionCompare.isNewer(incoming = "2.0.0", current = "2.0.0"))
        assertFalse(VersionCompare.isNewer(incoming = "1.9.9", current = "2.0.0"))
        assertFalse(VersionCompare.isNewer(incoming = "2.0.0", current = "2.0.1"))
    }

    @Test
    fun `segment counts may differ, missing segments count as zero`() {
        assertTrue(VersionCompare.isNewer(incoming = "2.0.0.1", current = "2.0.0"))
        assertFalse(VersionCompare.isNewer(incoming = "2.0", current = "2.0.0"))
        assertTrue(VersionCompare.isNewer(incoming = "2.1", current = "2.0.9"))
    }

    @Test
    fun `a beta build updates to the stable release with the same digits`() {
        assertTrue(VersionCompare.isNewer(incoming = "2.0.0", current = "2.0.0-beta"))
        assertFalse(VersionCompare.isNewer(incoming = "1.9.0", current = "2.0.0-beta"))
        assertTrue(VersionCompare.isNewer(incoming = "2.1.0", current = "2.0.0-beta"))
    }

    @Test
    fun `garbage segments never crash and count as zero`() {
        // Regle du chantier : aucun parse String vers nombre non garde sous core/.
        assertFalse(VersionCompare.isNewer(incoming = "abc", current = "2.0.0"))
        assertTrue(VersionCompare.isNewer(incoming = "2.x.1", current = "2.0.0"))
        assertTrue(VersionCompare.isNewer(incoming = "2.0.0", current = "x.y.z"))
    }
}
