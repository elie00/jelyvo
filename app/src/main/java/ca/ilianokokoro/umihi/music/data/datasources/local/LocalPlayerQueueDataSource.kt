package ca.ilianokokoro.umihi.music.data.datasources.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import ca.ilianokokoro.umihi.music.models.PlayerQueueState

@Dao
interface LocalPlayerQueueDataSource {
    @Upsert
    suspend fun upsert(state: PlayerQueueState)

    @Query("SELECT * FROM player_queue_state WHERE id = 0 LIMIT 1")
    suspend fun get(): PlayerQueueState?

    @Query("DELETE FROM player_queue_state")
    suspend fun clear()
}
