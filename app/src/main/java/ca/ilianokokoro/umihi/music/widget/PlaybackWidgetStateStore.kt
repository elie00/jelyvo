package ca.ilianokokoro.umihi.music.widget

import android.content.Context
import android.graphics.BitmapFactory
import ca.ilianokokoro.umihi.music.core.domain.Track
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
        track: Track?,
        isPlaying: Boolean,
        artworkBytes: ByteArray? = null,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val previousId = prefs.getString(KEY_MEDIA_ID, "").orEmpty()
        val artworkFile = File(context.filesDir, ARTWORK_FILE)
        val state = playbackWidgetState(
            track = track,
            isPlaying = isPlaying,
            artworkFile = artworkFile,
        )

        if (artworkBytes != null) {
            runCatching { artworkFile.writeBytes(artworkBytes) }
        } else if (state.mediaId != previousId && artworkFile.exists()) {
            runCatching { artworkFile.delete() }
        }

        prefs.edit()
            .putString(KEY_MEDIA_ID, state.mediaId)
            .putString(KEY_TITLE, state.title)
            .putString(KEY_ARTIST, state.artist)
            .putBoolean(KEY_IS_PLAYING, state.isPlaying)
            .apply()
    }
}

internal fun playbackWidgetState(
    track: Track?,
    isPlaying: Boolean,
    artworkFile: File? = null,
): PlaybackWidgetState = PlaybackWidgetState(
    mediaId = track?.id.orEmpty(),
    title = track?.title.orEmpty(),
    artist = track?.artist?.name.orEmpty(),
    isPlaying = isPlaying,
    artworkFile = artworkFile,
)
