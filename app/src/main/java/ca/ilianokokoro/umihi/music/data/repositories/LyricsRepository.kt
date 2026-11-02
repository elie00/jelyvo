package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.cachedApiFlow
import ca.ilianokokoro.umihi.music.core.lyrics.BetterLyricsProvider
import ca.ilianokokoro.umihi.music.core.lyrics.LrcLibProvider
import ca.ilianokokoro.umihi.music.core.lyrics.LrcRedProvider
import ca.ilianokokoro.umihi.music.core.lyrics.LyricsQuery
import ca.ilianokokoro.umihi.music.core.lyrics.LyricsResolver
import ca.ilianokokoro.umihi.music.core.lyrics.UnisonProvider
import ca.ilianokokoro.umihi.music.core.lyrics.artistForLyricsSearch
import ca.ilianokokoro.umihi.music.core.lyrics.forLyricsSearch
import ca.ilianokokoro.umihi.music.models.Lyrics
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.flow.Flow

class LyricsRepository {
    // Priority order: the word-timed catalogues first, LRCLIB as the dependable fallback.
    private val resolver = LyricsResolver(
        listOf(LrcRedProvider, BetterLyricsProvider, UnisonProvider, LrcLibProvider())
    )

    /**
     * Emits [ApiResult.Loading] then [ApiResult.Success] with the resolved
     * [Lyrics]. When no lyrics can be found the flow emits [ApiResult.Error]
     * (the UI treats this as "lyrics unavailable"). Network/parse failures are
     * surfaced through `.catch` as [ApiResult.Error].
     */
    fun getLyrics(song: Song, useCache: Boolean = true): Flow<ApiResult<Lyrics>> =
        cachedApiFlow(
            // Versioned: entries cached before word timing would otherwise stay line-timed for good.
            key = "lyrics-v2:${song.youtubeId}",
            useCache = useCache,
            // Lyrics remain useful indefinitely and make the karaoke available offline.
            diskSerializer = Lyrics.serializer(),
        ) {
            resolver.resolve(
                LyricsQuery(
                    title = song.title.forLyricsSearch(),
                    artist = song.artist.artistForLyricsSearch(),
                    durationSeconds = durationToSeconds(song.duration),
                )
            ) ?: throw Exception("Lyrics not available")
        }

    /**
     * Converts a display duration string ("m:ss" or "h:mm:ss") into whole
     * seconds. Returns `null` when the string is empty or unparseable, in which
     * case the datasource simply omits the duration filter.
     */
    private fun durationToSeconds(duration: String): Int? {
        if (duration.isBlank()) return null

        val parts = duration.split(":").map { it.trim().toIntOrNull() ?: return null }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            1 -> parts[0]
            else -> null
        }
    }
}
