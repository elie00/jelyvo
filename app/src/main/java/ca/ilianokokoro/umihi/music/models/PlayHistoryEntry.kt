package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey
import ca.ilianokokoro.umihi.music.core.Constants

/**
 * A single entry in the listening history. One row per track (keyed by [youtubeId]); replaying a
 * track updates [playedAt] instead of adding a duplicate, giving a clean "recently played" list.
 */
@Immutable
@Entity(tableName = Constants.Database.HISTORY_TABLE)
data class PlayHistoryEntry(
    @PrimaryKey
    val youtubeId: String,
    val title: String = "",
    val artist: String = "",
    val thumbnailHref: String = "",
    val duration: String = "",
    val isExplicit: Boolean = false,
    val playedAt: Long,
) {
    fun toSong(): Song = Song(
        youtubeId = youtubeId,
        title = title,
        artist = artist,
        thumbnailHref = thumbnailHref,
        duration = duration,
        isExplicit = isExplicit,
    )

    companion object {
        fun fromSong(song: Song, playedAt: Long): PlayHistoryEntry = PlayHistoryEntry(
            youtubeId = song.youtubeId,
            title = song.title,
            artist = song.artist,
            thumbnailHref = song.thumbnailHref,
            duration = song.duration,
            isExplicit = song.isExplicit,
            playedAt = playedAt,
        )
    }
}
