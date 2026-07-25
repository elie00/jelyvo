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
    /**
     * Free-form note the user can edit. Nullable rather than empty-defaulted: SQLite cannot add a
     * NOT NULL column without a DEFAULT, and NULL keeps "never described" a single state.
     * Declared last so the generated CREATE TABLE matches the column ALTER TABLE appends.
     */
    val description: String? = null,
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

/** How many tiles the playlist mosaic cover draws at most. */
const val MOSAIC_COVER_COUNT = 4

/**
 * The first [limit] distinct artwork URLs, in playlist order, used to build the mosaic cover.
 * Lazily evaluated so a 500-track playlist stops at the fourth distinct cover, and blank hrefs are
 * dropped so a partially-filled mosaic never draws an empty tile.
 */
fun List<LocalPlaylistSong>.mosaicCoverUris(limit: Int = MOSAIC_COVER_COUNT): List<String> =
    asSequence()
        .map { it.thumbnailHref }
        .filter { it.isNotBlank() }
        .distinct()
        .take(limit)
        .toList()
