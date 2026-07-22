package ca.ilianokokoro.umihi.music.core.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.repositories.dataStore
import ca.ilianokokoro.umihi.music.models.LocalPlaylist
import ca.ilianokokoro.umihi.music.models.PlayHistoryEntry
import ca.ilianokokoro.umihi.music.models.PlayerQueueState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

/** Versioned single-file interchange format for the full local backup. */
@Serializable
data class BackupFile(
    val version: Int,
    val exportedAt: Long,
    val preferences: List<BackupPreference> = emptyList(),
    val history: List<BackupHistoryEntry> = emptyList(),
    val localPlaylists: List<BackupLocalPlaylist> = emptyList(),
    val playerQueue: BackupPlayerQueue? = null,
)

/**
 * One DataStore preference, stored with its type tag so the exact typed key can be rebuilt on
 * restore. [value] is the string form; string sets are JSON-encoded lists.
 */
@Serializable
data class BackupPreference(
    val key: String,
    val type: String,
    val value: String,
)

@Serializable
data class BackupHistoryEntry(
    val youtubeId: String,
    val title: String = "",
    val artist: String = "",
    val thumbnailHref: String = "",
    val duration: String = "",
    val isExplicit: Boolean = false,
    val playedAt: Long,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

@Serializable
data class BackupLocalPlaylist(
    val id: String,
    val name: String,
    val songsJson: String,
    val createdAt: Long,
)

@Serializable
data class BackupPlayerQueue(
    val itemsJson: String,
    val currentIndex: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffleEnabled: Boolean,
    val sourceName: String? = null,
    val isRadio: Boolean = false,
)

/**
 * Exports and restores the app's local data (listening history, local playlists, resume/queue
 * state and DataStore preferences — theme, player, equalizer profiles, …) to a single JSON file
 * chosen through the Storage Access Framework, so no storage permission is required.
 *
 * Credentials are deliberately excluded: cookies are encrypted with a device-bound Keystore key
 * and would not decrypt elsewhere, and a backup file must never leak the account session.
 * Downloaded audio files are also out of scope — only database rows and preferences travel.
 */
object BackupManager {

    const val BACKUP_VERSION = 1
    const val SUGGESTED_FILE_NAME = "umihi-backup.json"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private const val TYPE_STRING = "string"
    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_DOUBLE = "double"
    private const val TYPE_STRING_SET = "stringSet"

    /** Preference keys that must never leave the device (session credentials). */
    private val EXCLUDED_PREFERENCE_KEYS = setOf(
        Constants.Datastore.COOKIES_KEY,
        Constants.Datastore.DATA_SYNC_ID,
    )

    suspend fun exportToUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val backup = buildBackup(context)
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(json.encodeToString(backup).toByteArray())
        } ?: throw IOException("Unable to open output stream")
    }

    /**
     * Reads and validates the backup at [uri] then replaces the local data with its content.
     * Throws [IllegalArgumentException] for a malformed file or an unsupported version.
     */
    suspend fun restoreFromUri(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().decodeToString() }
            ?: throw IOException("Unable to open input stream")

        val backup = try {
            json.decodeFromString<BackupFile>(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed backup file", e)
        }

        if (backup.version != BACKUP_VERSION) {
            throw IllegalArgumentException("Unsupported backup version ${backup.version}")
        }

        restoreDatabase(context, backup)
        restorePreferences(context, backup.preferences)
    }

    private suspend fun buildBackup(context: Context): BackupFile {
        val database = AppDatabase.getInstance(context)

        val history = database.historyRepository().getAll().map {
            BackupHistoryEntry(
                youtubeId = it.youtubeId,
                title = it.title,
                artist = it.artist,
                thumbnailHref = it.thumbnailHref,
                duration = it.duration,
                isExplicit = it.isExplicit,
                playedAt = it.playedAt,
                positionMs = it.positionMs,
                durationMs = it.durationMs,
            )
        }

        val localPlaylists = database.localPlaylistRepository().getAll().map {
            BackupLocalPlaylist(
                id = it.id,
                name = it.name,
                songsJson = it.songsJson,
                createdAt = it.createdAt,
            )
        }

        val playerQueue = database.playerQueueRepository().get()?.let {
            BackupPlayerQueue(
                itemsJson = it.itemsJson,
                currentIndex = it.currentIndex,
                positionMs = it.positionMs,
                repeatMode = it.repeatMode,
                shuffleEnabled = it.shuffleEnabled,
                sourceName = it.sourceName,
                isRadio = it.isRadio,
            )
        }

        val preferences = context.dataStore.data.first().asMap().mapNotNull { (key, value) ->
            if (key.name in EXCLUDED_PREFERENCE_KEYS) {
                return@mapNotNull null
            }
            val (type, encoded) = when (value) {
                is Boolean -> TYPE_BOOLEAN to value.toString()
                is Int -> TYPE_INT to value.toString()
                is Long -> TYPE_LONG to value.toString()
                is Float -> TYPE_FLOAT to value.toString()
                is Double -> TYPE_DOUBLE to value.toString()
                is String -> TYPE_STRING to value
                is Set<*> -> TYPE_STRING_SET to json.encodeToString(
                    value.filterIsInstance<String>()
                )

                else -> return@mapNotNull null
            }
            BackupPreference(key = key.name, type = type, value = encoded)
        }

        return BackupFile(
            version = BACKUP_VERSION,
            exportedAt = System.currentTimeMillis(),
            preferences = preferences,
            history = history,
            localPlaylists = localPlaylists,
            playerQueue = playerQueue,
        )
    }

    private suspend fun restoreDatabase(context: Context, backup: BackupFile) {
        val database = AppDatabase.getInstance(context)

        val historyDao = database.historyRepository()
        historyDao.clear()
        if (backup.history.isNotEmpty()) {
            historyDao.insertAll(backup.history.map {
                PlayHistoryEntry(
                    youtubeId = it.youtubeId,
                    title = it.title,
                    artist = it.artist,
                    thumbnailHref = it.thumbnailHref,
                    duration = it.duration,
                    isExplicit = it.isExplicit,
                    playedAt = it.playedAt,
                    positionMs = it.positionMs,
                    durationMs = it.durationMs,
                )
            })
        }

        val playlistDao = database.localPlaylistRepository()
        playlistDao.deleteAll()
        backup.localPlaylists.forEach {
            playlistDao.upsert(
                LocalPlaylist(
                    id = it.id,
                    name = it.name,
                    songsJson = it.songsJson,
                    createdAt = it.createdAt,
                )
            )
        }

        val queueDao = database.playerQueueRepository()
        queueDao.clear()
        backup.playerQueue?.let {
            queueDao.upsert(
                PlayerQueueState(
                    itemsJson = it.itemsJson,
                    currentIndex = it.currentIndex,
                    positionMs = it.positionMs,
                    repeatMode = it.repeatMode,
                    shuffleEnabled = it.shuffleEnabled,
                    sourceName = it.sourceName,
                    isRadio = it.isRadio,
                )
            )
        }
    }

    /**
     * Replaces every non-credential preference with the backup's content. Session keys
     * (cookies, data-sync id) are preserved so restoring never logs the user out.
     */
    private suspend fun restorePreferences(context: Context, preferences: List<BackupPreference>) {
        context.dataStore.edit { prefs ->
            prefs.asMap().keys
                .filter { it.name !in EXCLUDED_PREFERENCE_KEYS }
                .forEach { prefs.remove(it) }

            preferences.forEach { entry ->
                if (entry.key in EXCLUDED_PREFERENCE_KEYS) {
                    return@forEach
                }
                when (entry.type) {
                    TYPE_BOOLEAN -> entry.value.toBooleanStrictOrNull()?.let {
                        prefs[booleanPreferencesKey(entry.key)] = it
                    }

                    TYPE_INT -> entry.value.toIntOrNull()?.let {
                        prefs[intPreferencesKey(entry.key)] = it
                    }

                    TYPE_LONG -> entry.value.toLongOrNull()?.let {
                        prefs[longPreferencesKey(entry.key)] = it
                    }

                    TYPE_FLOAT -> entry.value.toFloatOrNull()?.let {
                        prefs[floatPreferencesKey(entry.key)] = it
                    }

                    TYPE_DOUBLE -> entry.value.toDoubleOrNull()?.let {
                        prefs[doublePreferencesKey(entry.key)] = it
                    }

                    TYPE_STRING -> prefs[stringPreferencesKey(entry.key)] = entry.value

                    TYPE_STRING_SET -> runCatching {
                        json.decodeFromString<List<String>>(entry.value)
                    }.getOrNull()?.let {
                        prefs[stringSetPreferencesKey(entry.key)] = it.toSet()
                    }
                }
            }
        }
    }
}
