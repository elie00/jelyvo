package ca.ilianokokoro.umihi.music.widget

import android.content.Context
import android.graphics.BitmapFactory
import androidx.media3.common.MediaItem
import java.io.File

data class PlaybackWidgetState(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val artworkFile: File? = null,
) {
    val artwork get() = artworkFile
        ?.takeIf(File::exists)
        ?.let { BitmapFactory.decodeFile(it.absolutePath) }
}

object PlaybackWidgetStateStore {
    private const val PREFS_NAME = "playback_widget"
    private const val KEY_MEDIA_ID = "media_id"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"
    private const val KEY_IS_PLAYING = "is_playing"
    private const val ARTWORK_FILE = "playback_widget_artwork.jpg"

    fun read(context: Context): PlaybackWidgetState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return PlaybackWidgetState(
            mediaId = prefs.getString(KEY_MEDIA_ID, "").orEmpty(),
            title = prefs.getString(KEY_TITLE, "").orEmpty(),
            artist = prefs.getString(KEY_ARTIST, "").orEmpty(),
            isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false),
            artworkFile = File(context.filesDir, ARTWORK_FILE),
        )
    }

    fun write(
        context: Context,
        mediaItem: MediaItem?,
        isPlaying: Boolean,
        artworkBytes: ByteArray? = null,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousId = prefs.getString(KEY_MEDIA_ID, "").orEmpty()
        val mediaId = mediaItem?.mediaId.orEmpty()
        val artworkFile = File(context.filesDir, ARTWORK_FILE)

        if (artworkBytes != null) {
            runCatching { artworkFile.writeBytes(artworkBytes) }
        } else if (mediaId != previousId && artworkFile.exists()) {
            runCatching { artworkFile.delete() }
        }

        prefs.edit()
            .putString(KEY_MEDIA_ID, mediaId)
            .putString(KEY_TITLE, mediaItem?.mediaMetadata?.title?.toString().orEmpty())
            .putString(KEY_ARTIST, mediaItem?.mediaMetadata?.artist?.toString().orEmpty())
            .putBoolean(KEY_IS_PLAYING, isPlaying)
            .apply()
    }
}
