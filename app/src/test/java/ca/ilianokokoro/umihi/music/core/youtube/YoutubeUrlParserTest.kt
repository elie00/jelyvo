package ca.ilianokokoro.umihi.music.core.youtube

import ca.ilianokokoro.umihi.music.core.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins every link form MainActivity is advertised for. The VIEW intent-filter claims /watch, /v/
 * and /embed/ across five hosts, but the extractor only ever read `?v=` and youtu.be's last path
 * segment, so an /embed/ or /v/ link launched the app and then did nothing — a silent no-op the
 * user cannot tell apart from a hang. Parsing was moved off android.net.Uri precisely so these
 * cases can be asserted here: android.* throws "not mocked" in JVM unit tests.
 */
class YoutubeUrlParserTest {

    @Test
    fun `watch links resolve on every host the manifest declares`() {
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("https://youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        // The filter declares http as well as https.
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("http://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `embed and v links carry the id in the path`() {
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ?start=10"))
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("https://www.youtube.com/v/dQw4w9WgXcQ?version=3"))
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("https://m.youtube.com/embed/dQw4w9WgXcQ"))
    }

    @Test
    fun `shorts links resolve although only the share sheet delivers them`() {
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("https://youtube.com/shorts/dQw4w9WgXcQ?feature=share"))
    }

    @Test
    fun `youtu dot be links resolve with share suffixes`() {
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("https://youtu.be/dQw4w9WgXcQ?si=aBcDeFgH&t=42"))
        // A trailing slash leaves an empty last segment behind.
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("https://youtu.be/dQw4w9WgXcQ/"))
        assertEquals("dQw4w9WgXcQ", parseYoutubeVideoId("https://youtu.be/dQw4w9WgXcQ#t=10"))
    }

    @Test
    fun `extra query parameters never hide the id`() {
        assertEquals(
            "dQw4w9WgXcQ",
            parseYoutubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabcdefghij&t=42s"),
        )
        // The id is not always the first parameter.
        assertEquals(
            "dQw4w9WgXcQ",
            parseYoutubeVideoId("https://m.youtube.com/watch?app=desktop&v=dQw4w9WgXcQ"),
        )
    }

    @Test
    fun `links without a single video resolve to nothing`() {
        assertNull(parseYoutubeVideoId("https://www.youtube.com/"))
        assertNull(parseYoutubeVideoId("https://www.youtube.com/watch"))
        assertNull(parseYoutubeVideoId("https://www.youtube.com/watch?v="))
        assertNull(parseYoutubeVideoId("https://music.youtube.com/playlist?list=PLabcdefghij"))
        // A playlist embed whose segment is exactly 11 legal characters.
        assertNull(parseYoutubeVideoId("https://www.youtube.com/embed/videoseries?list=PLabcdefghij"))
    }

    @Test
    fun `look-alike hosts are rejected`() {
        assertNull(parseYoutubeVideoId("https://youtu.be.example.com/dQw4w9WgXcQ"))
        assertNull(parseYoutubeVideoId("https://youtube.com.example.com/watch?v=dQw4w9WgXcQ"))
        assertNull(parseYoutubeVideoId("https://www.youtube-nocookie.com/embed/dQw4w9WgXcQ"))
        assertNull(parseYoutubeVideoId("https://vimeo.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `ids of the wrong shape are rejected`() {
        assertNull(parseYoutubeVideoId("https://www.youtube.com/embed/tooshort"))
        assertNull(parseYoutubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQextra"))
        // Share text often glues the sentence's punctuation onto the link.
        assertNull(parseYoutubeVideoId("https://youtu.be/dQw4w9WgXc!"))
    }

    @Test
    fun `input that is not an http url is rejected`() {
        assertNull(parseYoutubeVideoId(""))
        assertNull(parseYoutubeVideoId("not a url"))
        // A scheme is mandatory; both call sites always supply one.
        assertNull(parseYoutubeVideoId("www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `extractYouTubeVideoId delegates to the parser`() {
        assertEquals(
            "dQw4w9WgXcQ",
            YoutubeDataExtractor.extractYouTubeVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"),
        )
    }

    @Test
    fun `the share sheet regex accepts every host the manifest declares`() {
        val regex = Regex(YoutubeApiConstants.URL_REGEX)
        listOf(
            "https://www.youtube.com/embed/dQw4w9WgXcQ",
            "https://youtube.com/v/dQw4w9WgXcQ",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
            "https://youtu.be/dQw4w9WgXcQ",
        ).forEach { url ->
            assertEquals(url, regex.find("Listen to this $url")?.value)
        }
        assertTrue(regex.find("https://vimeo.com/1234") == null)
    }
}
