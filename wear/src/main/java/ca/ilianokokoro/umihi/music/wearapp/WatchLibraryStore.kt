package ca.ilianokokoro.umihi.music.wearapp

import android.content.Context
import java.io.File
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
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun favorites(): List<WatchSong> = read(KEY_FAVORITES)
    fun recents(): List<WatchSong> = read(KEY_RECENTS)
    fun downloads(): List<WatchSong> = read(KEY_DOWNLOADS).filter { audioFile(it.videoId).exists() }

    var smartDownloadLimit: Int
        get() = preferences.getInt(KEY_SMART_DOWNLOAD_LIMIT, 0)
        set(value) = preferences.edit { putInt(KEY_SMART_DOWNLOAD_LIMIT, value.coerceAtLeast(0)) }

    var wifiOnly: Boolean
        get() = preferences.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = preferences.edit { putBoolean(KEY_WIFI_ONLY, value) }

    var restrictedMode: Boolean
        get() = preferences.getBoolean(KEY_RESTRICTED_MODE, false)
        set(value) = preferences.edit { putBoolean(KEY_RESTRICTED_MODE, value) }

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

    fun addDownload(song: WatchSong) {
        write(
            KEY_DOWNLOADS,
            (listOf(song) + downloads().filterNot { it.videoId == song.videoId }).take(MAX_DOWNLOADS),
        )
    }

    fun removeDownload(videoId: String) {
        audioFile(videoId).delete()
        write(KEY_DOWNLOADS, downloads().filterNot { it.videoId == videoId })
    }

    fun clearDownloads() {
        downloadsDirectory.listFiles().orEmpty().forEach(File::delete)
        write(KEY_DOWNLOADS, emptyList())
    }

    fun audioFile(videoId: String): File = File(downloadsDirectory, "$videoId.audio")

    private val downloadsDirectory: File
        get() = File(preferencesFileDirectory, DOWNLOADS_DIRECTORY).apply { mkdirs() }

    private val preferencesFileDirectory: File
        get() = appContext.filesDir

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
        const val KEY_DOWNLOADS = "downloads"
        const val KEY_SMART_DOWNLOAD_LIMIT = "smart_download_limit"
        const val KEY_WIFI_ONLY = "wifi_only"
        const val KEY_RESTRICTED_MODE = "restricted_mode"
        const val DOWNLOADS_DIRECTORY = "watch_audio"
        const val MAX_FAVORITES = 100
        const val MAX_RECENTS = 30
        const val MAX_DOWNLOADS = 200
    }
}

private fun JsonObject.text(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
