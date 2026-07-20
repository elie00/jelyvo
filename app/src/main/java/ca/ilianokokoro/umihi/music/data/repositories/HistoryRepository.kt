package ca.ilianokokoro.umihi.music.data.repositories

import android.content.Context
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.models.PlayHistoryEntry
import ca.ilianokokoro.umihi.music.models.ArtistListeningStat
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.TrackListeningStat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepository(context: Context) {
    private val localHistoryDataSource = AppDatabase.getInstance(context).historyRepository()

    fun recentSongs(limit: Int = DEFAULT_LIMIT): Flow<List<Song>> =
        localHistoryDataSource.getRecent(limit).map { entries -> entries.map { it.toSong() } }

    fun topTracks(since: Long, limit: Int = STATS_LIMIT): Flow<List<TrackListeningStat>> =
        localHistoryDataSource.getTopTracks(since, limit)

    fun topArtists(since: Long, limit: Int = STATS_LIMIT): Flow<List<ArtistListeningStat>> =
        localHistoryDataSource.getTopArtists(since, limit)

    suspend fun record(song: Song, playedAt: Long) {
        if (song.youtubeId.isBlank()) return
        localHistoryDataSource.upsert(PlayHistoryEntry.fromSong(song, playedAt))
    }

    suspend fun clear() = localHistoryDataSource.clear()

    companion object {
        const val DEFAULT_LIMIT = 25
        const val STATS_LIMIT = 10
    }
}
