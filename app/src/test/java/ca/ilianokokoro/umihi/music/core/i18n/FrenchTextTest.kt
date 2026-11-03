package ca.ilianokokoro.umihi.music.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** Jelyvo is entirely in French, so its French text has to hold up on its own. */
class FrenchTextTest {
    private fun moduleFile(relative: String): File {
        val direct = File(relative)
        return if (direct.exists()) direct else File("app", relative)
    }

    private fun texts(relative: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(moduleFile(relative))
        val root = document.documentElement.childNodes
        return (0 until root.length)
            .mapNotNull { root.item(it) as? Element }
            .filter { it.getAttribute("translatable") != "false" }
            .associate { it.getAttribute("name") to it.textContent }
    }

    private val french by lazy { texts("src/main/res/values-fr/strings.xml") }
    private val english by lazy { texts("src/main/res/values/strings.xml") }

    @Test
    fun `every text has its French version`() {
        assertEquals(emptySet<String>(), english.keys - french.keys)
    }

    @Test
    fun `no French text comes out garbled`() {
        // What UTF-8 looks like once it has been read as Latin-1.
        val garbled = Regex("Ã.|Â.|â€")
        assertEquals(emptyList<String>(), french.filterValues(garbled::containsMatchIn).keys.toList())
    }

    @Test
    fun `French text uses French words`() {
        // Brands and technical names (YouTube, Chromecast, Wi-Fi, USB, OLED) stay, and so do words
        // French has made its own (album, radio, podcast, karaoké, cache).
        val english = Regex(
            """\b(playlists?|scrobbl\w*|streaming|replay|singles?|top|mix|lik[ée]s?|wrapped|umihi|qr code|material you|cast)\b""",
            RegexOption.IGNORE_CASE,
        )
        val found = french
            .filterValues(english::containsMatchIn)
            .map { (name, text) -> "$name = $text" }
        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun `no text is left for a language the app does not speak`() {
        assertEquals(emptyList<String>(), english.keys.filter { it.startsWith("special_language") })
    }
}
