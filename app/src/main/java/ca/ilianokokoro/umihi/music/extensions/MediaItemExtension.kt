package ca.ilianokokoro.umihi.music.extensions

import androidx.media3.common.MediaItem
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.models.Song

fun MediaItem?.toSong(): Song {
    val extras = this?.mediaMetadata?.extras
    return Song(
        uid = extras?.getString(Constants.ExoPlayer.SongMetadata.UID).toStringOrEmpty(),
        youtubeId = this?.mediaId.toStringOrEmpty(),
        title = this?.mediaMetadata?.title.toStringOrEmpty(),
        artist = this?.mediaMetadata?.artist.toStringOrEmpty(),
        thumbnailHref = this?.mediaMetadata?.artworkUri.toString(), // TODO handle if not href
        duration = extras?.getString(Constants.ExoPlayer.SongMetadata.DURATION).toStringOrEmpty(),
        isExplicit = extras?.getBoolean(Constants.ExoPlayer.SongMetadata.IS_EXPLICIT, false)
            ?: false,
        isLiked = extras?.getBoolean(Constants.ExoPlayer.SongMetadata.IS_LIKED)
    ).also {
        it.artistId = extras?.getString(Constants.ExoPlayer.SongMetadata.ARTIST_ID)
        it.albumId = extras?.getString(Constants.ExoPlayer.SongMetadata.ALBUM_ID)
        it.albumName = extras?.getString(Constants.ExoPlayer.SongMetadata.ALBUM_NAME)
        it.isLocal = extras?.getBoolean(Constants.ExoPlayer.SongMetadata.IS_LOCAL, false) ?: false
        it.localUri = extras?.getString(Constants.ExoPlayer.SongMetadata.LOCAL_URI)
        it.isSuggestion =
            extras?.getBoolean(Constants.ExoPlayer.SongMetadata.IS_SUGGESTION, false) ?: false
        it.forceVideo =
            extras?.getBoolean(Constants.ExoPlayer.SongMetadata.FORCE_VIDEO, false) ?: false
    }
}