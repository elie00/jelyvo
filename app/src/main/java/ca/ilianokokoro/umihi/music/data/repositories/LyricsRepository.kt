package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.cachedApiFlow
import ca.ilianokokoro.umihi.music.data.datasources.LyricsDataSource
import ca.ilianokokoro.umihi.music.models.Lyrics
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.flow.Flow

class LyricsRepository {
    private val lyricsDataSource = LyricsDataSource()

    /**
     * Emits [ApiResult.Loading] then [ApiResult.Success] with the resolved
     * [Lyrics]. When no lyrics can be found the flow emits [ApiResult.Error]
     * (the UI treats this as "lyrics unavailable"). Network/parse failures are
     * surfaced through `.catch` as [ApiResult.Error].
     */
    fun getLyrics(song: Song, useCache: Boolean = true): Flow<ApiResult<Lyrics>> =
        cachedApiFlow(
            key = "lyrics:${song.youtubeId}",
            useCache = useCache,
            // Lyrics remain useful indefinitely and make the karaoke available offline.
            diskSerializer = Lyrics.serializer(),
        ) {
            lyricsDataSource.getLyrics(
                title = song.title,
                artist = song.artist,
                durationSeconds = durationToSeconds(song.duration),
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
