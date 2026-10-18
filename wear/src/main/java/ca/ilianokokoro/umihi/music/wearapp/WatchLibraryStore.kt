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
    fun downloads(): List<WatchSong> = read(KEY_DOWNLOADS).filter { isWatchVideoId(it.videoId) && audioFile(it.videoId).let { file -> file.isFile && file.length() > 0 } }

    var smartDownloadLimit: Int
        get() = preferences.getInt(KEY_SMART_DOWNLOAD_LIMIT, 0)
        set(value) = preferences.edit { putInt(KEY_SMART_DOWNLOAD_LIMIT, value.coerceAtLeast(0)) }

    var wifiOnly: Boolean
        get() = preferences.getBoolean(KEY_WIFI_ONLY, true)
        set(value) = preferences.edit { putBoolean(KEY_WIFI_ONLY, value) }

    var restrictedMode: Boolean
        get() = preferences.getBoolean(KEY_RESTRICTED_MODE, false)
        set(value) = preferences.edit { putBoolean(KEY_RESTRICTED_MODE, value) }

    fun toggleFavorite(song: WatchSong): Boolean = synchronized(lock) {
        val (songs, added) = toggledFavorite(favorites(), song, MAX_FAVORITES)
        write(KEY_FAVORITES, songs)
        return added
    }

    fun addRecent(song: WatchSong) = synchronized(lock) {
        write(KEY_RECENTS, withRecent(recents(), song, MAX_RECENTS))
    }

    private fun addDownload(song: WatchSong) {
        write(
            KEY_DOWNLOADS,
            (listOf(song) + downloads().filterNot { it.videoId == song.videoId }),
        )
    }

    fun downloadToken(videoId: String): String = synchronized(lock) {
        "${preferences.getLong(KEY_DOWNLOAD_GENERATION, 0)}:${preferences.getLong("download:$videoId", 0)}"
    }

    fun registerExistingDownload(song: WatchSong, token: String): Boolean = synchronized(lock) {
        if (token != downloadToken(song.videoId)) return false
        val file = audioFile(song.videoId)
        if (!file.isFile || file.length() == 0L) return false
        addDownload(song)
        true
    }

    fun publishDownload(song: WatchSong, partial: File, token: String): Boolean = synchronized(lock) {
        if (token != downloadToken(song.videoId) || partial.length() == 0L) return false
        java.nio.file.Files.move(partial.toPath(), audioFile(song.videoId).toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        addDownload(song)
        true
    }

    fun removeDownload(videoId: String) = synchronized(lock) {
        preferences.edit { putLong("download:$videoId", preferences.getLong("download:$videoId", 0) + 1) }
        audioFile(videoId).delete()
        write(KEY_DOWNLOADS, downloads().filterNot { it.videoId == videoId })
    }

    fun clearDownloads() = synchronized(lock) {
        preferences.edit { putLong(KEY_DOWNLOAD_GENERATION, preferences.getLong(KEY_DOWNLOAD_GENERATION, 0) + 1) }
        downloadsDirectory.listFiles().orEmpty().forEach(File::delete)
        write(KEY_DOWNLOADS, emptyList())
    }

    fun audioFile(videoId: String): File {
        require(isWatchVideoId(videoId)) { "Identifiant YouTube invalide" }
        return File(downloadsDirectory, "$videoId.audio")
    }

    private val downloadsDirectory: File
        get() = File(preferencesFileDirectory, DOWNLOADS_DIRECTORY).apply { mkdirs() }

    private val preferencesFileDirectory: File
        get() = appContext.filesDir

    private fun read(key: String): List<WatchSong> = decodeWatchSongs(preferences.getString(key, null))

    private fun write(key: String, songs: List<WatchSong>) {
        preferences.edit { putString(key, encodeWatchSongs(songs)) }
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
        const val KEY_DOWNLOAD_GENERATION = "download_generation"
        val lock = Any()
    }
}

/** Removes [song] when present, otherwise adds it first; the second value is true when added. */
internal fun toggledFavorite(
    favorites: List<WatchSong>,
    song: WatchSong,
    max: Int,
): Pair<List<WatchSong>, Boolean> {
    val songs = favorites.toMutableList()
    val removed = songs.removeAll { it.videoId == song.videoId }
    if (!removed) songs.add(0, song)
    return songs.take(max) to !removed
}

/** Moves [song] to the front of the history without duplicating it. */
internal fun withRecent(recents: List<WatchSong>, song: WatchSong, max: Int): List<WatchSong> =
    (listOf(song) + recents.filterNot { it.videoId == song.videoId }).take(max)

internal fun encodeWatchSongs(songs: List<WatchSong>): String = buildJsonArray {
    songs.forEach { song ->
        add(buildJsonObject {
            put("videoId", JsonPrimitive(song.videoId))
            put("title", JsonPrimitive(song.title))
            put("artist", JsonPrimitive(song.artist))
            put("artworkUrl", JsonPrimitive(song.artworkUrl))
            put("durationText", JsonPrimitive(song.durationText))
            put("isExplicit", JsonPrimitive(song.isExplicit))
        })
    }
}.toString()

/** Unreadable or missing storage reads as an empty list, never as a crash. */
internal fun decodeWatchSongs(value: String?): List<WatchSong> = runCatching {
    if (value == null) return emptyList()
    (Json.parseToJsonElement(value) as? JsonArray).orEmpty().mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        WatchSong(
            videoId = item.text("videoId") ?: return@mapNotNull null,
            title = item.text("title").orEmpty(),
            artist = item.text("artist").orEmpty(),
            artworkUrl = item.text("artworkUrl").orEmpty(),
            durationText = item.text("durationText").orEmpty(),
            isExplicit = item.boolean("isExplicit"),
        )
    }
}.getOrDefault(emptyList())

private fun JsonObject.text(key: String): String? = get(key)?.jsonPrimitive?.contentOrNull
private fun JsonObject.boolean(key: String): Boolean =
    get(key)?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
