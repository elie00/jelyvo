package ca.ilianokokoro.umihi.music.ui.components.song

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackArtistRedundancyTest {

    @Test
    fun `the album artist repeated on a track is hidden`() {
        assertTrue(isTrackArtistRedundant("Daft Punk", "Daft Punk"))
    }

    @Test
    fun `casing, spacing and accents do not matter`() {
        assertTrue(isTrackArtistRedundant("  daft punk ", "Daft Punk"))
        assertTrue(isTrackArtistRedundant("Melodie", "Mélodie"))
    }

    @Test
    fun `a featuring credit stays visible`() {
        assertFalse(isTrackArtistRedundant("Daft Punk & Pharrell Williams", "Daft Punk"))
        assertFalse(isTrackArtistRedundant("Pharrell Williams", "Daft Punk"))
    }

    @Test
    fun `nothing is hidden when the album artist is unknown`() {
        assertFalse(isTrackArtistRedundant("Daft Punk", ""))
        assertFalse(isTrackArtistRedundant("", "   "))
    }
}
