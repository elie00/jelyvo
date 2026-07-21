package ca.ilianokokoro.umihi.music.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import ca.ilianokokoro.umihi.music.core.Constants

@Entity(tableName = Constants.Database.PLAYER_QUEUE_TABLE)
data class PlayerQueueState(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val itemsJson: String,
    val currentIndex: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val sourceName: String? = null,
    val isRadio: Boolean = false,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
