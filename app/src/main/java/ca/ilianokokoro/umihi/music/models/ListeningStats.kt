package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable

@Immutable
data class TrackListeningStat(
    val youtubeId: String,
    val title: String,
    val artist: String,
    val thumbnailHref: String,
    val duration: String,
    val isExplicit: Boolean,
    val playCount: Int,
    val lastPlayedAt: Long,
) {
    fun toSong() = Song(
        youtubeId = youtubeId,
        title = title,
        artist = artist,
        thumbnailHref = thumbnailHref,
        duration = duration,
        isExplicit = isExplicit,
    )
}

@Immutable
data class ArtistListeningStat(
    val artist: String,
    val playCount: Int,
    val lastPlayedAt: Long,
)
