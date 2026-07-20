package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ca.ilianokokoro.umihi.music.core.Constants

/**
 * A single playback event. Keeping one row per play makes monthly listening statistics accurate;
 * the recent-items query is responsible for collapsing repeated tracks.
 */
@Immutable
@Entity(
    tableName = Constants.Database.HISTORY_TABLE,
    indices = [Index("youtubeId"), Index("playedAt")],
)
data class PlayHistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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
