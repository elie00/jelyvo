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
import ca.ilianokokoro.umihi.music.core.helpers.ArtworkUrl
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiConstants
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

    // The directory an on-device track sits in, for browsing by folder. Transient, like its URI's source.
    @Ignore
    var localFolder: String? = null

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

    // Track number as printed by YouTube in the left column of an album row ("index" node). Null
    // outside an album page — search and playlist rows do not carry it. Transient.
    @Ignore
    var trackNumber: Int? = null

    // Disc number of a multi-CD album. YouTube exposes no such field: it is derived from the track
    // shelves (one per disc) or, failing that, inferred from a track-number reset — see
    // AlbumTrackGrouping. Null when the album is single-disc or the data doesn't allow it.
    @Ignore
    var discNumber: Int? = null

    /**
     * Returns this song with an updated like status while preserving the transient metadata that
     * a data-class [copy] would otherwise reset. This keeps artist/album navigation and local or
     * video playback intact when a like action updates a queue or playlist row optimistically.
     */
    fun withLikeStatus(liked: Boolean?): Song =
        copy(isLiked = liked).also(::copyTransientMetadataTo)

    /**
     * Attaches the album page that supplied this track before it enters the playback queue.
     * Album rows frequently omit their own MPRE browse link because the surrounding page already
     * provides it; carrying that context in MediaItem extras keeps "View album" available later.
     */
    fun withAlbumContext(
        albumId: String,
        albumName: String,
        artworkHref: String = thumbnailHref,
    ): Song = copy(
        thumbnailHref = thumbnailHref.ifBlank { artworkHref },
    ).also { updated ->
        copyTransientMetadataTo(updated)
        updated.albumId = albumId
        updated.albumName = albumName
    }

    private fun copyTransientMetadataTo(updated: Song) {
        updated.albumId = albumId
        updated.albumName = albumName
        updated.artistId = artistId
        updated.isLocal = isLocal
        updated.localUri = localUri
        updated.localFolder = localFolder
        updated.isSuggestion = isSuggestion
        updated.forceVideo = forceVideo
        updated.isVideo = isVideo
        updated.viewCount = viewCount
        updated.playCount = playCount
        updated.trackNumber = trackNumber
        updated.discNumber = discNumber
    }

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
                forceVideo -> "$youtubeUrl&${YoutubeApiConstants.FORCE_VIDEO_PARAM}=1"
                isVideo -> "$youtubeUrl&${YoutubeApiConstants.SOURCE_VIDEO_PARAM}=1"
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
                        // La miniature d'une ligne de liste ne fait que 60-120 px ; reecrite en
                        // haute resolution pour le lecteur, la notification et l'ecran verrouille.
                        .setArtworkUri((thumbnailPath ?: ArtworkUrl.upgrade(thumbnailHref)).toUri())
                        .setExtras(extras)
                        .build()

                )
                .build()
        }


    val youtubeUrl: String
        get() = "${YoutubeApiConstants.YOUTUBE_URL_PREFIX}${youtubeId}"
    val downloaded: Boolean
        get() = audioFilePath != null && thumbnailPath != null


    suspend fun getThumbnailBitmap(): Bitmap? {
        val bytes = UmihiHelper.fetchArtworkBytes(ArtworkUrl.upgrade(thumbnailHref))
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
            val raw = url.removePrefix(YoutubeApiConstants.YOUTUBE_URL_PREFIX)
            // Split off any appended playback markers (e.g. "&umihiForceVideo=1") so the
            // youtubeId stays clean while the flag is recovered from the URI.
            val forceVideo = raw.contains("${YoutubeApiConstants.FORCE_VIDEO_PARAM}=1")
            val sourceVideo = raw.contains("${YoutubeApiConstants.SOURCE_VIDEO_PARAM}=1")
            return Song(youtubeId = raw.substringBefore("&")).also {
                it.forceVideo = forceVideo
                it.isVideo = sourceVideo || forceVideo
            }
        }

    }

}
