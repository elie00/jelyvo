package ca.ilianokokoro.umihi.music.core.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareHelperTest {

    private val url = "https://music.youtube.com/watch?v=abc123"

    @Test
    fun `no position shares the plain url`() {
        assertEquals(url, ShareHelper.shareUrl(url, null))
    }

    @Test
    fun `zero and negative positions share the plain url`() {
        assertEquals(url, ShareHelper.shareUrl(url, 0L))
        assertEquals(url, ShareHelper.shareUrl(url, -5L))
    }

    @Test
    fun `a position appends the seconds marker with an ampersand`() {
        // The watch URL already carries "?v=", so "t" must join with "&", never "?".
        assertEquals("$url&t=75s", ShareHelper.shareUrl(url, 75L))
    }
}
