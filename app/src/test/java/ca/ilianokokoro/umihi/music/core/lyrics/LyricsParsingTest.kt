package ca.ilianokokoro.umihi.music.core.lyrics

import ca.ilianokokoro.umihi.music.models.LyricLine
import ca.ilianokokoro.umihi.music.models.LyricWord
import ca.ilianokokoro.umihi.music.models.Lyrics
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParsingTest {
    private fun ttml(body: String) = """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
          <body><div>$body</div></body>
        </tt>
    """.trimIndent()

    @Test
    fun `word timing survives the disk cache`() {
        val lyrics = Lyrics(
            lines = listOf(LyricLine(1_000L, "Hello", listOf(LyricWord(1_000L, 2_000L, "Hello")))),
            isSynced = true,
        )
        val stored = Json.encodeToString(Lyrics.serializer(), lyrics)

        assertEquals(lyrics, Json.decodeFromString(Lyrics.serializer(), stored))
    }

    @Test
    fun `entries cached before word timing still load`() {
        val stored = """{"lines":[{"timeMs":1000,"text":"Hello"}],"isSynced":true}"""

        assertTrue(Json.decodeFromString(Lyrics.serializer(), stored).lines.single().words.isEmpty())
    }

    @Test
    fun `ttml splits words on whitespace and joins syllables`() {
        val lines = TtmlLyrics.parse(
            ttml(
                """<p begin="1.000" end="3.000"><span begin="1.0" end="1.5">I</span> """ +
                    """<span begin="1.5" end="2.0">e</span><span begin="2.0" end="3.0">nough</span></p>"""
            )
        )

        assertEquals("I enough", lines.single().text)
        assertEquals(
            listOf(LyricWord(1_000, 1_500, "I"), LyricWord(1_500, 3_000, "enough")),
            lines.single().words,
        )
    }

    @Test
    fun `ttml leaves out translations and background vocals`() {
        val lines = TtmlLyrics.parse(
            ttml(
                """<p begin="1" end="2"><span begin="1" end="2">Lead</span> """ +
                    """<span ttm:role="x-bg" begin="1" end="2">backing</span> """ +
                    """<span ttm:role="x-translation">traduction</span></p>"""
            )
        )

        assertEquals("Lead", lines.single().text)
    }

    @Test
    fun `line synced ttml keeps its stamp without word timing`() {
        val line = TtmlLyrics.parse(ttml("""<p begin="1:05.20" end="1:06">Whole line</p>""")).single()

        assertEquals(65_200L, line.timeMs)
        assertEquals("Whole line", line.text)
        assertTrue(line.words.isEmpty())
    }

    @Test
    fun `ttml lines come back in time order`() {
        val lines = TtmlLyrics.parse(ttml("""<p begin="5">Second</p><p begin="1">First</p>"""))

        assertEquals(listOf("First", "Second"), lines.map { it.text })
    }

    @Test
    fun `malformed or hostile ttml yields nothing`() {
        assertTrue(TtmlLyrics.parse("<tt><body>").isEmpty())
        val external = """<!DOCTYPE tt [<!ENTITY x SYSTEM "file:///etc/passwd">]>""" +
            """<tt><body><div><p begin="1">&x;</p></div></body></tt>"""
        assertFalse(TtmlLyrics.parse(external).any { it.text.contains("root") })
    }

    @Test
    fun `ttml clock values`() {
        assertEquals(27_395L, TtmlLyrics.time("27.395"))
        assertEquals(3_723_400L, TtmlLyrics.time("1:02:03.4"))
        assertEquals(27_000L, TtmlLyrics.time("27s"))
        assertEquals(500L, TtmlLyrics.time("500ms"))
        assertNull(TtmlLyrics.time("soon"))
    }

    @Test
    fun `enhanced lrc runs each word until the next starts`() {
        val lines = EnhancedLrc.parse("[00:01.00]<00:01.00>Hello <00:02.00>there\n[00:05.00]Next")

        assertEquals(
            listOf(LyricWord(1_000, 2_000, "Hello"), LyricWord(2_000, 5_000, "there")),
            lines.first().words,
        )
        assertEquals("Next", lines.last().text)
    }

    @Test
    fun `plain lrc is not enhanced lrc`() {
        assertTrue(EnhancedLrc.parse("[00:01.00]Just a line").isEmpty())
    }

    @Test
    fun `replies are read through json envelopes`() {
        val inner = ttml("""<p begin="1"><span begin="1" end="2">Word</span></p>""")
        val reply = """{"data":{"ttml":${kotlinx.serialization.json.JsonPrimitive(inner)}}}"""

        assertTrue(ProviderReply.parse(reply)!!.hasWordTiming)
    }

    @Test
    fun `replies reporting an error are not lyrics`() {
        assertNull(ProviderReply.parse("""{"error":"not found","lyrics":"x"}"""))
        assertNull(ProviderReply.parse("Lyrics not found"))
    }

    @Test
    fun `unison plain entries are unsynced`() {
        val lyrics = UnisonProvider.lyricsOf(
            UnisonProvider.Entry(lyrics = "One\nTwo", format = "lrc", syncType = "plain")
        )!!

        assertFalse(lyrics.isSynced)
        assertEquals(2, lyrics.lines.size)
    }

    @Test
    fun `search cleaning removes credits but keeps the recording`() {
        assertEquals("Dracula", "Dracula (feat. JENNIE)".forLyricsSearch())
        assertEquals("Song", "Song [Official Music Video]".forLyricsSearch())
        assertEquals("Song (Live)", "Song (Live)".forLyricsSearch())
        assertEquals("Song (Remix)", "Song (Remix) ft. Someone".forLyricsSearch())
        assertEquals("Artist", "Artist - Topic".artistForLyricsSearch())
    }

    @Test
    fun `lrc red only takes the same recording`() {
        val hits = listOf(
            LrcRedProvider.Hit("GBUM71029604", "Bohemian Rhapsody (Live)", "Queen", 352.0),
            LrcRedProvider.Hit("GBUM71029605", "Bohemian Rhapsody", "Queen", 356.5),
            LrcRedProvider.Hit("GBUM71029606", "Bohemian Rhapsody", "Someone Else", 356.5),
        )

        assertEquals(
            "GBUM71029605",
            LrcRedProvider.best(hits, "Bohemian Rhapsody", "Queen", 355)?.isrc,
        )
        assertNull(LrcRedProvider.best(hits, "Bohemian Rhapsody", "Queen", 200))
        assertEquals(
            "GBUM71029604",
            LrcRedProvider.best(hits, "Bohemian Rhapsody (Live)", "Queen", null)?.isrc,
        )
    }

    @Test
    fun `lrc red never builds a url from an unchecked code`() {
        assertEquals("https://lrc.red/s/GBUM71029604.ttml", LrcRedProvider.documentUrl("gbum71029604"))
        assertNull(LrcRedProvider.documentUrl("../../etc/passwd"))
    }
}
