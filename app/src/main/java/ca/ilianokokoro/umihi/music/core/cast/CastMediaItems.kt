package ca.ilianokokoro.umihi.music.core.cast

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import ca.ilianokokoro.umihi.music.core.domain.ResolvedRemoteStream
import ca.ilianokokoro.umihi.music.models.Song

fun ResolvedRemoteStream.toCastMediaItem(song: Song): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(song.title)
        .setArtist(song.artist)
        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        .setIsBrowsable(false)
        .setIsPlayable(true)
        .apply {
            if (song.thumbnailHref.isNotBlank()) {
                // Never expose thumbnailPath: it points into app-private storage.
                setArtworkUri(song.thumbnailHref.toUri())
            }
        }
        .build()

    return MediaItem.Builder()
        .setMediaId(song.youtubeId)
        .setUri(url)
        .setMimeType(contentType)
        .setMediaMetadata(metadata)
        .build()
}
