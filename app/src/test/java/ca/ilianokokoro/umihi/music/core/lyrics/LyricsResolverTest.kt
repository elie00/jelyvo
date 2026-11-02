package ca.ilianokokoro.umihi.music.core.lyrics

import ca.ilianokokoro.umihi.music.models.LyricLine
import ca.ilianokokoro.umihi.music.models.LyricWord
import ca.ilianokokoro.umihi.music.models.Lyrics
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsResolverTest {
    private val query = LyricsQuery("t", "a", 100)
    private val plain = Lyrics(listOf(LyricLine(null, "plain")), isSynced = false)
    private val lineTimed = Lyrics(listOf(LyricLine(1L, "line")), isSynced = true)
    private val wordTimed = Lyrics(
        listOf(LyricLine(1L, "word", listOf(LyricWord(1L, 2L, "word")))),
        isSynced = true,
    )

    @Test
    fun `better timing beats list order`() = runBlocking {
        val resolver = LyricsResolver(
            listOf(
                LyricsProvider { plain },
                LyricsProvider { lineTimed },
                LyricsProvider { wordTimed },
            )
        )

        assertEquals(wordTimed, resolver.resolve(query))
    }

    @Test
    fun `list order breaks a tie`() = runBlocking {
        val first = Lyrics(listOf(LyricLine(1L, "first")), isSynced = true)
        val resolver = LyricsResolver(listOf(LyricsProvider { first }, LyricsProvider { lineTimed }))

        assertEquals(first, resolver.resolve(query))
    }

    @Test
    fun `a failing or empty source is not an answer`() = runBlocking {
        val resolver = LyricsResolver(
            listOf(
                LyricsProvider { error("host is down") },
                LyricsProvider { Lyrics(emptyList(), isSynced = false) },
                LyricsProvider { plain },
            )
        )

        assertEquals(plain, resolver.resolve(query))
    }

    @Test
    fun `nothing found is null`() = runBlocking {
        assertNull(LyricsResolver(listOf(LyricsProvider { null })).resolve(query))
    }

    @Test
    fun `a word timed answer does not wait for slower sources`() = runBlocking {
        val resolver = LyricsResolver(
            listOf(LyricsProvider { wordTimed }, LyricsProvider { awaitCancellation() })
        )

        assertEquals(wordTimed, withTimeout(5_000) { resolver.resolve(query) })
    }
}
