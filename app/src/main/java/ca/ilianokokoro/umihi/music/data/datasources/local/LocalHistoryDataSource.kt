package ca.ilianokokoro.umihi.music.data.datasources.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ca.ilianokokoro.umihi.music.models.PlayHistoryEntry
import ca.ilianokokoro.umihi.music.models.ArtistListeningStat
import ca.ilianokokoro.umihi.music.models.TrackListeningStat
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalHistoryDataSource {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PlayHistoryEntry)

    @Query(
        """
        SELECT latest.* FROM play_history AS latest
        WHERE latest.id = (
            SELECT candidate.id FROM play_history AS candidate
            WHERE candidate.youtubeId = latest.youtubeId
            ORDER BY candidate.playedAt DESC, candidate.id DESC
            LIMIT 1
        )
        ORDER BY latest.playedAt DESC
        LIMIT :limit
        """
    )
    fun getRecent(limit: Int): Flow<List<PlayHistoryEntry>>

    @Query(
        """
        SELECT youtubeId,
               MAX(title) AS title,
               MAX(artist) AS artist,
               MAX(thumbnailHref) AS thumbnailHref,
               MAX(duration) AS duration,
               MAX(isExplicit) AS isExplicit,
               COUNT(*) AS playCount,
               MAX(playedAt) AS lastPlayedAt
        FROM play_history
        WHERE playedAt >= :since
        GROUP BY youtubeId
        ORDER BY playCount DESC, lastPlayedAt DESC
        LIMIT :limit
        """
    )
    fun getTopTracks(since: Long, limit: Int): Flow<List<TrackListeningStat>>

    @Query(
        """
        SELECT artist, COUNT(*) AS playCount, MAX(playedAt) AS lastPlayedAt
        FROM play_history
        WHERE playedAt >= :since AND artist != ''
        GROUP BY artist COLLATE NOCASE
        ORDER BY playCount DESC, lastPlayedAt DESC
        LIMIT :limit
        """
    )
    fun getTopArtists(since: Long, limit: Int): Flow<List<ArtistListeningStat>>

    @Query("DELETE FROM play_history")
    suspend fun clear()
}
