package ca.ilianokokoro.umihi.music.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackTest {
    @Test
    fun `parses minute and hour durations`() {
        assertEquals(185_000L, parseTrackDurationMillis("3:05"))
        assertEquals(3_723_000L, parseTrackDurationMillis("1:02:03"))
    }

    @Test
    fun `rejects malformed or impossible durations`() {
        assertNull(parseTrackDurationMillis(""))
        assertNull(parseTrackDurationMillis("3"))
        assertNull(parseTrackDurationMillis("1:60"))
        assertNull(parseTrackDurationMillis("-1:20"))
        assertNull(parseTrackDurationMillis("music"))
    }

    @Test
    fun `formats durations without platform APIs`() {
        assertEquals("3:05", formatTrackDuration(185_999L))
        assertEquals("1:02:03", formatTrackDuration(3_723_000L))
        assertEquals("", formatTrackDuration(null))
    }
}

