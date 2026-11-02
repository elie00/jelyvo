package ca.ilianokokoro.umihi.music.core.lyrics

import ca.ilianokokoro.umihi.music.models.Lyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** Word timing beats line timing, which beats plain text. */
internal fun lyricsQuality(lyrics: Lyrics): Int = when {
    lyrics.hasWordTiming -> 2
    lyrics.isSynced -> 1
    else -> 0
}

/**
 * Asks every provider at once and returns the best answer, the list order breaking ties. Answers
 * are read in that order and the wait stops at the first word-timed one, so a slow source behind
 * it costs nothing, and one that fails is simply not an answer.
 */
class LyricsResolver(private val providers: List<LyricsProvider>) {
    suspend fun resolve(query: LyricsQuery): Lyrics? = coroutineScope {
        val answers = providers.map { provider ->
            async {
                try {
                    provider.fetch(query)?.takeUnless { it.isEmpty }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
        }
        var best: Lyrics? = null
        for (answer in answers) {
            val lyrics = answer.await() ?: continue
            if (lyricsQuality(lyrics) == WORD_TIMED) {
                best = lyrics
                break
            }
            if (best == null || lyricsQuality(lyrics) > lyricsQuality(best)) best = lyrics
        }
        answers.forEach { it.cancel() }
        best
    }

    private companion object {
        const val WORD_TIMED = 2
    }
}
