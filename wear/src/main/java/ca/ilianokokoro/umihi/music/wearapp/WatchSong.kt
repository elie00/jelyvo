package ca.ilianokokoro.umihi.music.wearapp

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class WatchSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val durationText: String = "",
    val isExplicit: Boolean = false,
)

fun WatchSong.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(videoId)
    .setUri("jelyvo://youtube/$videoId")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(artworkUrl.takeIf(String::isNotBlank)?.toUri())
            .build(),
    )
    .build()
