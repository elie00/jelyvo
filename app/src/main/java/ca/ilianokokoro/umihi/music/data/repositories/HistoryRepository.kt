package ca.ilianokokoro.umihi.music.data.repositories

import android.content.Context
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.models.PlayHistoryEntry
import ca.ilianokokoro.umihi.music.models.ArtistListeningStat
import ca.ilianokokoro.umihi.music.models.DailyPlayCount
import ca.ilianokokoro.umihi.music.models.ResumeItem
import ca.ilianokokoro.umihi.music.models.TrackPlayDuration
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.TrackListeningStat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepository(context: Context) {
    private val localHistoryDataSource = AppDatabase.getInstance(context).historyRepository()

    fun recentSongs(limit: Int = DEFAULT_LIMIT): Flow<List<Song>> =
        localHistoryDataSource.getRecent(limit).map { entries -> entries.map { it.toSong() } }

    fun resumableSongs(limit: Int = RESUME_LIMIT): Flow<List<ResumeItem>> =
        localHistoryDataSource.getResumable(limit).map { entries ->
            entries.map { ResumeItem(it.toSong(), it.positionMs, it.durationMs) }
        }

    suspend fun updatePosition(youtubeId: String, positionMs: Long, durationMs: Long) {
        if (youtubeId.isBlank()) return
        localHistoryDataSource.updatePosition(youtubeId, positionMs, durationMs)
    }

    fun topTracks(since: Long, limit: Int = STATS_LIMIT): Flow<List<TrackListeningStat>> =
        localHistoryDataSource.getTopTracks(since, limit)

    fun topArtists(since: Long, limit: Int = STATS_LIMIT): Flow<List<ArtistListeningStat>> =
        localHistoryDataSource.getTopArtists(since, limit)

    fun dailyPlayCounts(since: Long): Flow<List<DailyPlayCount>> =
        localHistoryDataSource.getDailyPlayCounts(since)

    fun playCount(since: Long): Flow<Int> = localHistoryDataSource.getPlayCount(since)

    fun trackPlayDurations(since: Long): Flow<List<TrackPlayDuration>> =
        localHistoryDataSource.getTrackPlayDurations(since)

    suspend fun record(song: Song, playedAt: Long) {
        if (song.youtubeId.isBlank()) return
        localHistoryDataSource.upsert(PlayHistoryEntry.fromSong(song, playedAt))
    }

    suspend fun clear() = localHistoryDataSource.clear()

    companion object {
        const val DEFAULT_LIMIT = 25
        const val RESUME_LIMIT = 12
        const val STATS_LIMIT = 10
    }
}
