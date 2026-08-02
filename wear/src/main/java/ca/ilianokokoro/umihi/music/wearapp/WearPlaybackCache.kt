package ca.ilianokokoro.umihi.music.wearapp

import android.content.Context
import androidx.core.content.edit
import androidx.wear.tiles.TileService
import ca.ilianokokoro.umihi.music.wear.WearPlaybackSnapshot

data class CachedTilePlayback(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val hasMedia: Boolean,
)

class WearPlaybackCache(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(snapshot: WearPlaybackSnapshot) {
        preferences.edit {
            putString(KEY_TITLE, snapshot.title)
            putString(KEY_ARTIST, snapshot.artist)
            putBoolean(KEY_IS_PLAYING, snapshot.isPlaying)
            putBoolean(KEY_HAS_MEDIA, snapshot.hasMedia)
        }
        TileService.getUpdater(appContext).requestUpdate(JelyvoTileService::class.java)
    }

    fun read(): CachedTilePlayback = CachedTilePlayback(
        title = preferences.getString(KEY_TITLE, "").orEmpty(),
        artist = preferences.getString(KEY_ARTIST, "").orEmpty(),
        isPlaying = preferences.getBoolean(KEY_IS_PLAYING, false),
        hasMedia = preferences.getBoolean(KEY_HAS_MEDIA, false),
    )

    private companion object {
        const val PREFERENCES = "wear_playback_tile"
        const val KEY_TITLE = "title"
        const val KEY_ARTIST = "artist"
        const val KEY_IS_PLAYING = "is_playing"
        const val KEY_HAS_MEDIA = "has_media"
    }
}
