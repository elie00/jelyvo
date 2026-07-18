package ca.ilianokokoro.umihi.music.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookiesTest {

    @Test
    fun `empty raw cookie is reported empty`() {
        assertTrue(Cookies("").isEmpty())
        assertTrue(Cookies("   ").isEmpty())
        assertFalse(Cookies("").isNotEmpty())
    }

    @Test
    fun `parses key value pairs separated by semicolons`() {
        val cookies = Cookies("SAPISID=abc; __Secure-3PAPISID=def; HSID=ghi")

        assertEquals("abc", cookies.data["SAPISID"])
        assertEquals("def", cookies.data["__Secure-3PAPISID"])
        assertEquals("ghi", cookies.data["HSID"])
        assertEquals(3, cookies.data.size)
    }

    @Test
    fun `keeps only the first equals sign as separator`() {
        val cookies = Cookies("token=a=b=c")

        assertEquals("a=b=c", cookies.data["token"])
    }

    @Test
    fun `ignores malformed entries without a value`() {
        val cookies = Cookies("valid=1; garbage; other=2")

        assertEquals("1", cookies.data["valid"])
        assertEquals("2", cookies.data["other"])
        assertFalse(cookies.data.containsKey("garbage"))
    }

    @Test
    fun `round-trips the raw string`() {
        val raw = "A=1; B=2"
        assertEquals(raw, Cookies(raw).toRawCookie())
    }
}
