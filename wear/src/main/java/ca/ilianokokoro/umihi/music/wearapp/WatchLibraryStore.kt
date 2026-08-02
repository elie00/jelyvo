package ca.ilianokokoro.umihi.music.wearapp

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class WatchLibraryStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun favorites(): List<WatchSong> = read(KEY_FAVORITES)
    fun recents(): List<WatchSong> = read(KEY_RECENTS)

    fun toggleFavorite(song: WatchSong): Boolean {
        val songs = favorites().toMutableList()
        val removed = songs.removeAll { it.videoId == song.videoId }
        if (!removed) songs.add(0, song)
        write(KEY_FAVORITES, songs.take(MAX_FAVORITES))
        return !removed
    }

    fun addRecent(song: WatchSong) {
        write(
            KEY_RECENTS,
            (listOf(song) + recents().filterNot { it.videoId == song.videoId }).take(MAX_RECENTS),
        )
    }

    private fun read(key: String): List<WatchSong> = runCatching {
        val value = preferences.getString(key, null) ?: return emptyList()
        (Json.parseToJsonElement(value) as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            WatchSong(
                videoId = item.text("videoId") ?: return@mapNotNull null,
                title = item.text("title").orEmpty(),
                artist = item.text("artist").orEmpty(),
                artworkUrl = item.text("artworkUrl").orEmpty(),
                durationText = item.text("durationText").orEmpty(),
            )
        }
    }.getOrDefault(emptyList())

    private fun write(key: String, songs: List<WatchSong>) {
        val json = buildJsonArray {
            songs.forEach { song ->
                add(buildJsonObject {
                    put("videoId", JsonPrimitive(song.videoId))
                    put("title", JsonPrimitive(song.title))
                    put("artist", JsonPrimitive(song.artist))
                    put("artworkUrl", JsonPrimitive(song.artworkUrl))
                    put("durationText", JsonPrimitive(song.durationText))
                })
            }
        }
        preferences.edit { putString(key, json.toString()) }
    }

    private companion object {
        const val PREFERENCES = "standalone_library"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENTS = "recents"
        const val MAX_FAVORITES = 100
        const val MAX_RECENTS = 30
    }
}

private fun JsonObject.text(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
