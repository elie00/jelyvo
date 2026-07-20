package ca.ilianokokoro.umihi.music.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import ca.ilianokokoro.umihi.music.core.Constants
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * A fully on-device, user-editable playlist (created via import, reorderable, renamable).
 * Distinct from remote YouTube playlists: the ordered song list is stored as JSON in
 * [songsJson], mirroring the [PlayerQueueState] persistence pattern.
 */
@Entity(tableName = Constants.Database.LOCAL_PLAYLISTS_TABLE)
data class LocalPlaylist(
    @PrimaryKey val id: String,
    val name: String,
    val songsJson: String,
    val createdAt: Long,
)

/**
 * A song entry inside a local playlist. [uid] gives every row a stable identity so the
 * drag-and-drop list keeps working even when the same track appears twice.
 */
@Serializable
data class LocalPlaylistSong(
    val id: String,
    val title: String = "",
    val artist: String = "",
    val thumbnailHref: String = "",
    val duration: String = "",
    val uid: String = Uuid.random().toString(),
) {
    fun toSong(): Song = Song(
        youtubeId = id,
        title = title,
        artist = artist,
        duration = duration,
        thumbnailHref = thumbnailHref,
        uid = uid,
    )

    companion object {
        fun fromSong(song: Song) = LocalPlaylistSong(
            id = song.youtubeId,
            title = song.title,
            artist = song.artist,
            thumbnailHref = song.thumbnailPath ?: song.thumbnailHref,
            duration = song.duration,
        )
    }
}

/** Versioned interchange format written/read by export & import. */
@Serializable
data class PlaylistExportFile(
    val version: Int = 1,
    val name: String,
    val songs: List<PlaylistExportSong>,
)

@Serializable
data class PlaylistExportSong(
    val id: String,
    val title: String = "",
    val artist: String = "",
)
