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

/** Number of plays recorded on a single local-time day ("yyyy-MM-dd"). Feeds the heatmap. */
@Immutable
data class DailyPlayCount(
    val day: String,
    val playCount: Int,
)

/**
 * Per-track play count with the best known duration, used to estimate total listening time.
 * [durationMs] comes from playback progress tracking and may be 0; [duration] is the
 * display string ("m:ss" / "h:mm:ss") that came with the track metadata.
 */
@Immutable
data class TrackPlayDuration(
    val duration: String,
    val durationMs: Long,
    val playCount: Int,
)
