package ca.ilianokokoro.umihi.music.data.repositories

import android.app.Application
import android.content.Context
import android.net.Uri
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.models.LocalPlaylist
import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong
import ca.ilianokokoro.umihi.music.models.mosaicCoverUris
import ca.ilianokokoro.umihi.music.models.PlaylistExportFile
import ca.ilianokokoro.umihi.music.models.PlaylistExportSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import kotlin.uuid.Uuid

/**
 * Storage and file I/O for on-device editable playlists.
 * Persists the ordered song list as JSON (like [PlayerQueueRepository]) and reads/writes
 * export files through the Storage Access Framework (no storage permission required).
 */
class LocalPlaylistRepository(application: Application) {
    private val dao = AppDatabase.getInstance(application).localPlaylistRepository()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun observeAll(): Flow<List<LocalPlaylist>> = dao.observeAll()

    fun observeById(id: String): Flow<LocalPlaylist?> = dao.observeById(id)

    fun songsOf(playlist: LocalPlaylist): List<LocalPlaylistSong> =
        runCatching { json.decodeFromString<List<LocalPlaylistSong>>(playlist.songsJson) }
            .getOrDefault(emptyList())

    /** Artwork URLs for [playlist]'s mosaic cover; decodes [LocalPlaylist.songsJson] once. */
    fun coversOf(playlist: LocalPlaylist): List<String> = songsOf(playlist).mosaicCoverUris()

    suspend fun create(name: String, songs: List<LocalPlaylistSong>): String {
        val id = Constants.Database.LOCAL_PLAYLIST_ID_PREFIX + Uuid.random().toString()
        dao.upsert(
            LocalPlaylist(
                id = id,
                name = name,
                songsJson = json.encodeToString(songs),
                createdAt = System.currentTimeMillis(),
            )
        )
        return id
    }

    suspend fun rename(id: String, name: String) {
        val existing = dao.getById(id) ?: return
        dao.upsert(existing.copy(name = name))
    }

    suspend fun delete(id: String) = dao.deleteById(id)

    suspend fun updateSongs(id: String, songs: List<LocalPlaylistSong>) {
        val existing = dao.getById(id) ?: return
        dao.upsert(existing.copy(songsJson = json.encodeToString(songs)))
    }

    /** Serializes [name]/[songs] to the versioned JSON format at the user-chosen [uri]. */
    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        name: String,
        songs: List<LocalPlaylistSong>,
    ) = withContext(Dispatchers.IO) {
        val export = PlaylistExportFile(
            version = EXPORT_VERSION,
            name = name,
            songs = songs.map { PlaylistExportSong(id = it.id, title = it.title, artist = it.artist) },
        )
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(json.encodeToString(export).toByteArray())
        } ?: throw IOException("Unable to open output stream")
    }

    /**
     * Reads a JSON playlist from [uri], validates it and creates a new local playlist.
     * Throws [IllegalArgumentException] when the file is malformed, an unsupported version
     * or contains no usable song.
     */
    suspend fun importFromUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: throw IOException("Unable to open input stream")

        val parsed = try {
            json.decodeFromString<PlaylistExportFile>(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Malformed playlist file", e)
        }

        if (parsed.version != EXPORT_VERSION) {
            throw IllegalArgumentException("Unsupported playlist version ${parsed.version}")
        }

        val songs = parsed.songs
            .filter { it.id.isNotBlank() }
            .map { LocalPlaylistSong(id = it.id, title = it.title, artist = it.artist) }

        if (songs.isEmpty()) {
            throw IllegalArgumentException("Playlist file contains no song")
        }

        create(parsed.name.ifBlank { DEFAULT_IMPORT_NAME }, songs)
    }

    companion object {
        const val EXPORT_VERSION = 1
        private const val DEFAULT_IMPORT_NAME = "Imported playlist"
    }
}
