package ca.ilianokokoro.umihi.music.data.datasources.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import ca.ilianokokoro.umihi.music.models.LocalPlaylist
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalUserPlaylistDataSource {
    @Query("SELECT * FROM local_playlists ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LocalPlaylist>>

    @Query("SELECT * FROM local_playlists WHERE id = :id")
    fun observeById(id: String): Flow<LocalPlaylist?>

    @Query("SELECT * FROM local_playlists WHERE id = :id")
    suspend fun getById(id: String): LocalPlaylist?

    @Upsert
    suspend fun upsert(playlist: LocalPlaylist)

    @Query("DELETE FROM local_playlists WHERE id = :id")
    suspend fun deleteById(id: String)
}
