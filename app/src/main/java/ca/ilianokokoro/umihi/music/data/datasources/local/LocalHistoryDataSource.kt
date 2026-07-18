package ca.ilianokokoro.umihi.music.data.datasources.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ca.ilianokokoro.umihi.music.models.PlayHistoryEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalHistoryDataSource {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PlayHistoryEntry)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<PlayHistoryEntry>>

    @Query("DELETE FROM play_history")
    suspend fun clear()
}
