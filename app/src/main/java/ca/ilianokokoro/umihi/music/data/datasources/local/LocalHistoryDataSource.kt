package ca.ilianokokoro.umihi.music.data.datasources.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ca.ilianokokoro.umihi.music.models.PlayHistoryEntry
import ca.ilianokokoro.umihi.music.models.ArtistListeningStat
import ca.ilianokokoro.umihi.music.models.DailyPlayCount
import ca.ilianokokoro.umihi.music.models.TrackListeningStat
import ca.ilianokokoro.umihi.music.models.TrackPlayDuration
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalHistoryDataSource {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PlayHistoryEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<PlayHistoryEntry>)

    /** Full dump, oldest first, used by the local backup export. */
    @Query("SELECT * FROM play_history ORDER BY playedAt ASC, id ASC")
    suspend fun getAll(): List<PlayHistoryEntry>

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

    /** Stores the latest playback position on the most recent play row of the given track. */
    @Query(
        """
        UPDATE play_history
        SET positionMs = :positionMs, durationMs = :durationMs
        WHERE id = (
            SELECT id FROM play_history
            WHERE youtubeId = :youtubeId
            ORDER BY playedAt DESC, id DESC
            LIMIT 1
        )
        """
    )
    suspend fun updatePosition(youtubeId: String, positionMs: Long, durationMs: Long)

    /**
     * Most recently played tracks that were left partially listened (some progress, not near the
     * end), collapsed to one row per track. Feeds the "resume listening" home section.
     */
    @Query(
        """
        SELECT latest.* FROM play_history AS latest
        WHERE latest.id = (
            SELECT candidate.id FROM play_history AS candidate
            WHERE candidate.youtubeId = latest.youtubeId
            ORDER BY candidate.playedAt DESC, candidate.id DESC
            LIMIT 1
        )
        AND latest.positionMs > 0
        AND latest.durationMs > 0
        AND latest.positionMs < latest.durationMs - (latest.durationMs / 20)
        ORDER BY latest.playedAt DESC
        LIMIT :limit
        """
    )
    fun getResumable(limit: Int): Flow<List<PlayHistoryEntry>>

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

    /** Play counts grouped by local-time day, oldest first. */
    @Query(
        """
        SELECT strftime('%Y-%m-%d', playedAt / 1000, 'unixepoch', 'localtime') AS day,
               COUNT(*) AS playCount
        FROM play_history
        WHERE playedAt >= :since
        GROUP BY day
        ORDER BY day ASC
        """
    )
    fun getDailyPlayCounts(since: Long): Flow<List<DailyPlayCount>>

    @Query("SELECT COUNT(*) FROM play_history WHERE playedAt >= :since")
    fun getPlayCount(since: Long): Flow<Int>

    /** One row per distinct track with its play count and best known duration. */
    @Query(
        """
        SELECT MAX(duration) AS duration,
               MAX(durationMs) AS durationMs,
               COUNT(*) AS playCount
        FROM play_history
        WHERE playedAt >= :since
        GROUP BY youtubeId
        """
    )
    fun getTrackPlayDurations(since: Long): Flow<List<TrackPlayDuration>>

    /** Removes every play of the given track, so it leaves the recent and resume rows at once. */
    @Query("DELETE FROM play_history WHERE youtubeId = :youtubeId")
    suspend fun deleteTrack(youtubeId: String)

    @Query("DELETE FROM play_history")
    suspend fun clear()
}
