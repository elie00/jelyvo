package ca.ilianokokoro.umihi.music.models

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
@Immutable
@Entity(tableName = Constants.Database.SONGS_TABLE)
data class Song(
    @PrimaryKey
    val youtubeId: String,
    val title: String = "",
    val artist: String = "",
    val duration: String = "",
    val thumbnailHref: String = "",
    val thumbnailPath: String? = null,
    val streamUrl: String? = null,
    val loudnessDb: Double? = null,
    val audioFilePath: String? = null,
    val uid: String = Uuid.random().toString(),
    val isExplicit: Boolean = false,
    val isLiked: Boolean? = null,
) {
    // Not persisted (transient): populated from search results to enable the artist/album pages.
    // Set once right after extraction; never mutated afterwards.
    @Ignore
    var albumId: String? = null

    @Ignore
    var albumName: String? = null

    // YouTube Music artist browseId ("UC…") — opens the official artist page (not a Topic channel).
    @Ignore
    var artistId: String? = null

    // Local (on-device) track: [localUri] is a playable content:// URI; when set, playback reads it
    // directly and never resolves a YouTube stream. Transient like the fields above.
    @Ignore
    var isLocal: Boolean = false

    @Ignore
    var localUri: String? = null

    // Autoplay suggestion: true when this queue entry was appended by the end-of-queue radio
    // mechanism (not explicitly added by the user). Transient; carried in the MediaItem extras and
    // persisted with the queue so the UI can separate "up next" from automatic suggestions.
    @Ignore
    var isSuggestion: Boolean = false

    // Play-the-clip override: when true, playback streams the music-video itself (skipping the
    // official-audio counterpart swap) and shows the clip poster instead of the album cover.
    // Default false = official audio. Transient; carried in the MediaItem extras and stream URI.
    @Ignore
    var forceVideo: Boolean = false

    // Video result (music video / UGC clip, or a row from the Videos search tab) as opposed to an
    // official-audio song. Drives the wide 16:9 row thumbnail and the view-count subtitle.
    // Transient; carried in the MediaItem extras so queue rows keep the video presentation.
    @Ignore
    var isVideo: Boolean = false

    // Human-readable view count (e.g. "1.2M views") parsed from the search subtitle runs of a
    // video row. Only set when [isVideo] is true and the API exposed it. Transient.
    @Ignore
    var viewCount: String? = null

    // Human-readable play count (e.g. "45M plays") from an artist page's song shelf, which is how
    // YouTube Music ranks an artist's most-streamed tracks. Unlike [viewCount] this is not tied to
    // video rows. Transient.
    @Ignore
    var playCount: String? = null

    val mediaItem: MediaItem
        get() {
            val extras = Bundle()
            extras.putString(Constants.ExoPlayer.SongMetadata.DURATION, duration)
            extras.putString(Constants.ExoPlayer.SongMetadata.UID, uid)
            extras.putBoolean(Constants.ExoPlayer.SongMetadata.IS_EXPLICIT, isExplicit)
            isLiked?.let { extras.putBoolean(Constants.ExoPlayer.SongMetadata.IS_LIKED, it) }
            artistId?.let { extras.putString(Constants.ExoPlayer.SongMetadata.ARTIST_ID, it) }
            albumId?.let { extras.putString(Constants.ExoPlayer.SongMetadata.ALBUM_ID, it) }
            albumName?.let { extras.putString(Constants.ExoPlayer.SongMetadata.ALBUM_NAME, it) }
            if (isLocal) {
                extras.putBoolean(Constants.ExoPlayer.SongMetadata.IS_LOCAL, true)
                localUri?.let { extras.putString(Constants.ExoPlayer.SongMetadata.LOCAL_URI, it) }
            }
            if (isSuggestion) {
                extras.putBoolean(Constants.ExoPlayer.SongMetadata.IS_SUGGESTION, true)
            }
            if (forceVideo) {
                extras.putBoolean(Constants.ExoPlayer.SongMetadata.FORCE_VIDEO, true)
            }
            if (isVideo) {
                extras.putBoolean(Constants.ExoPlayer.SongMetadata.IS_VIDEO, true)
            }
            viewCount?.let { extras.putString(Constants.ExoPlayer.SongMetadata.VIEW_COUNT, it) }

            val playbackUri = when {
                isLocal -> localUri ?: youtubeUrl
                // The prefix already carries a "?v=" query, so the marker is appended with "&".
                forceVideo -> "$youtubeUrl&${Constants.YoutubeApi.FORCE_VIDEO_PARAM}=1"
                isVideo -> "$youtubeUrl&${Constants.YoutubeApi.SOURCE_VIDEO_PARAM}=1"
                else -> youtubeUrl
            }

            return MediaItem.Builder()
                .setUri(playbackUri)
                .setMediaId(youtubeId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .setArtworkUri((thumbnailPath ?: thumbnailHref).toUri())
                        .setExtras(extras)
                        .build()

                )
                .build()
        }


    val youtubeUrl: String
        get() = "${Constants.YoutubeApi.YOUTUBE_URL_PREFIX}${youtubeId}"
    val downloaded: Boolean
        get() = audioFilePath != null && thumbnailPath != null


    suspend fun getThumbnailBitmap(): Bitmap? {
        val bytes = UmihiHelper.fetchArtworkBytes(thumbnailHref)
        return bytes?.let {
            BitmapFactory.decodeByteArray(it, 0, it.size)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Song) return false
        // Queue-entry identity: same track (youtubeId) AND same queue slot (uid).
        return youtubeId == other.youtubeId && uid == other.uid
    }

    override fun hashCode(): Int {
        return 31 * youtubeId.hashCode() + uid.hashCode()
    }

    fun isSameYoutubeSong(other: Song): Boolean {
        return this.youtubeId == other.youtubeId
    }

    companion object {
        fun createFromYoutubeUrl(url: String): Song {
            val raw = url.removePrefix(Constants.YoutubeApi.YOUTUBE_URL_PREFIX)
            // Split off any appended playback markers (e.g. "&umihiForceVideo=1") so the
            // youtubeId stays clean while the flag is recovered from the URI.
            val forceVideo = raw.contains("${Constants.YoutubeApi.FORCE_VIDEO_PARAM}=1")
            val sourceVideo = raw.contains("${Constants.YoutubeApi.SOURCE_VIDEO_PARAM}=1")
            return Song(youtubeId = raw.substringBefore("&")).also {
                it.forceVideo = forceVideo
                it.isVideo = sourceVideo || forceVideo
            }
        }

    }

}

