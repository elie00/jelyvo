package ca.ilianokokoro.umihi.music.data.datasources.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import ca.ilianokokoro.umihi.music.models.PlayEvent
import ca.ilianokokoro.umihi.music.models.PlayEventAggregate

@Dao
interface LocalPlayEventDataSource {
    @Insert
    suspend fun insert(event: PlayEvent)

    @Query("SELECT * FROM play_events WHERE playedAt >= :since ORDER BY playedAt ASC, id ASC")
    suspend fun eventsSince(since: Long): List<PlayEvent>

    @Query("SELECT * FROM play_events WHERE youtubeId = :youtubeId ORDER BY playedAt ASC, id ASC")
    suspend fun eventsForSong(youtubeId: String): List<PlayEvent>

    /** Per-track rollup of the window, the cheap first pass of any scoring. */
    @Query(
        """
        SELECT youtubeId,
               COUNT(*) AS playCount,
               SUM(CASE WHEN endReason = 'COMPLETED' THEN 1 ELSE 0 END) AS completedCount,
               SUM(CASE WHEN endReason = 'SKIPPED_EARLY' THEN 1 ELSE 0 END) AS skippedEarlyCount,
               AVG(completionRatio) AS averageCompletion,
               SUM(durationListenedMs) AS totalListenedMs,
               MAX(playedAt) AS lastPlayedAt
        FROM play_events
        WHERE playedAt >= :since
        GROUP BY youtubeId
        ORDER BY playCount DESC, lastPlayedAt DESC
        """
    )
    suspend fun aggregatesSince(since: Long): List<PlayEventAggregate>

    /** Retention: drops events older than the cutoff, returns how many rows went away. */
    @Query("DELETE FROM play_events WHERE playedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int
}
