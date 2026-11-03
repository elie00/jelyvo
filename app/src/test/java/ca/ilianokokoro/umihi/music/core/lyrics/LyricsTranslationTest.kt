package ca.ilianokokoro.umihi.music.core.lyrics

import ca.ilianokokoro.umihi.music.models.LyricLine
import ca.ilianokokoro.umihi.music.models.LyricWord
import ca.ilianokokoro.umihi.music.models.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTranslationTest {
    private val lyrics = Lyrics(
        lines = listOf(
            LyricLine(1_000, "Hello", words = listOf(LyricWord(1_000, 1_400, "Hel"), LyricWord(1_400, 2_000, "lo"))),
            LyricLine(3_000, ""),
            LyricLine(4_000, "World"),
        ),
        isSynced = true,
    )

    private fun response(text: String, language: String = "en") =
        """[[["$text","source",null,null]],null,"$language"]"""

    @Test
    fun `instrumental gaps are never sent`() {
        val slots = LyricsTranslation.slotsOf(lyrics.lines)
        assertEquals(listOf(0, 2), slots.map { it.lineIndex })
    }

    @Test
    fun `a translated line keeps its time, and gaps stay where they were`() {
        val slots = LyricsTranslation.slotsOf(lyrics.lines)
        val result = LyricsTranslation.rebuild(lyrics, slots, listOf("Bonjour", "Monde"))
        assertEquals(listOf<Long?>(1_000, 3_000, 4_000), result.lines.map { it.timeMs })
        assertEquals(listOf("Bonjour", "", "Monde"), result.lines.map { it.text })
    }

    @Test
    fun `a word-timed line keeps the span it was sung over, as one stretch`() {
        val slots = LyricsTranslation.slotsOf(lyrics.lines)
        val result = LyricsTranslation.rebuild(lyrics, slots, listOf("Bonjour", "Monde"))
        assertEquals(listOf(LyricWord(1_000, 2_000, "Bonjour")), result.lines[0].words)
        assertTrue(result.lines[2].words.isEmpty())
    }

    @Test
    fun `the answer is split back on the markers`() {
        val batch = LyricsTranslation.batchesOf(LyricsTranslation.slotsOf(lyrics.lines)).single()
        assertTrue(batch.payload.contains(''))
        val answer = LyricsTranslation.parseAnswer(response("Bonjour\\n0001\\nMonde"), 2)
        assertNotNull(answer)
        assertEquals(listOf("Bonjour", "Monde"), answer!!.texts)
        assertEquals("en", answer.sourceLanguage)
    }

    @Test
    fun `an answer with a line missing is rejected rather than shifted`() {
        assertNull(LyricsTranslation.parseAnswer(response("Bonjour"), 2))
        assertNull(LyricsTranslation.parseAnswer(response("Bonjour\\n0001\\n "), 2))
        assertNull(LyricsTranslation.parseAnswer("not json", 2))
    }

    @Test
    fun `a long song is split without losing or reordering a line`() {
        val lines = List(400) { LyricLine(it.toLong(), "Ligne numero $it de la chanson") }
        val slots = LyricsTranslation.slotsOf(lines)
        val batches = LyricsTranslation.batchesOf(slots)
        assertTrue(batches.size > 1)
        assertTrue(batches.all { it.payload.length <= 3_500 + 16 })
        assertEquals(slots, batches.flatMap { it.slots })
    }

    @Test
    fun `lyrics already in the language are left alone`() {
        val slots = LyricsTranslation.slotsOf(lyrics.lines)
        val answer = LyricsTranslation.Answer(listOf("Bonjour", "Monde"), "fr")
        assertEquals(
            LyricsTranslation.Result.SameLanguage("fr"),
            LyricsTranslation.combine(lyrics, slots, listOf(answer), "fr-FR"),
        )
    }

    @Test
    fun `one failed batch fails the whole song`() {
        val slots = LyricsTranslation.slotsOf(lyrics.lines)
        val answer = LyricsTranslation.Answer(listOf("Bonjour", "Monde"), "en")
        assertEquals(
            LyricsTranslation.Result.Unavailable,
            LyricsTranslation.combine(lyrics, slots, listOf(answer, null), "fr"),
        )
        assertEquals(
            LyricsTranslation.Result.Unavailable,
            LyricsTranslation.combine(lyrics, slots, emptyList(), "fr"),
        )
    }

    @Test
    fun `old and new language codes are the same language, and regions do not split one`() {
        assertTrue(LyricsTranslation.sameLanguage("iw", "he"))
        assertTrue(LyricsTranslation.sameLanguage("in", "id"))
        assertTrue(LyricsTranslation.sameLanguage("zh-CN", "zh_TW"))
        assertFalse(LyricsTranslation.sameLanguage("en", "fr"))
    }
}
