package ca.ilianokokoro.umihi.music.data.datasources.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ca.ilianokokoro.umihi.music.models.Song

@Dao
interface LocalSongDataSource {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun create(song: Song)

    @Query(
        """
    SELECT * 
    FROM songs 
    WHERE audioFilePath IS NOT NULL 
      AND thumbnailPath IS NOT NULL
      ORDER BY  
        songs.title COLLATE NOCASE ASC,
        songs.artist COLLATE NOCASE ASC
"""
    )
    suspend fun getDownloadedSongs(): List<Song>

    /**
     * Downloaded songs that are NOT linked to any playlist. These are the auto-downloaded (offline
     * sync) tracks: manual downloads always create a playlist cross-reference, so anything without
     * one was added by [OfflineSyncWorker] and is safe for the quota purge to reclaim.
     */
    @Query(
        """
    SELECT *
    FROM songs
    WHERE audioFilePath IS NOT NULL
      AND thumbnailPath IS NOT NULL
      AND youtubeId NOT IN (SELECT songId FROM PlaylistSongCrossRef)
"""
    )
    suspend fun getUnlinkedDownloadedSongs(): List<Song>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createAll(songs: List<Song>)

    @Query("SELECT * FROM songs WHERE youtubeId = :songId")
    suspend fun getSong(songId: String): Song?

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    suspend fun setStreamInfo(songId: String, streamUrl: String, loudnessDb: Double?) {
        val existing = getSong(songId) ?: Song(
            youtubeId = songId,
            streamUrl = streamUrl,
            loudnessDb = loudnessDb,
        )

        val updated = existing.copy(streamUrl = streamUrl, loudnessDb = loudnessDb)
        create(updated)
    }

    @Query("UPDATE songs SET streamUrl = NULL")
    suspend fun clearStreamUrls()

    @Query("DELETE FROM songs WHERE youtubeId IN (:songIds)")
    suspend fun deleteByIds(songIds: List<String>)

    @Delete
    suspend fun delete(song: Song)

}
