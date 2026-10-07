package ca.ilianokokoro.umihi.music.desktop.library

import ca.ilianokokoro.umihi.music.core.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID

@Serializable
data class DesktopPlaylist(val id: String, val name: String, val tracks: List<Track>)

@Serializable
data class LibrarySnapshot(
    val version: Int = 1,
    val favorites: List<Track> = emptyList(),
    val recents: List<Track> = emptyList(),
    val playlists: List<DesktopPlaylist> = emptyList(),
)

/** A failed read never permits silently replacing the user's existing library. */
class DesktopLibrary(private val directory: Path = defaultDirectory()) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val mutex = Mutex()
    private val _state = MutableStateFlow(LibrarySnapshot())
    val state = _state.asStateFlow()
    private var loaded = false
    private val file get() = directory.resolve("library.json")

    suspend fun restore() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val restored = if (Files.exists(file)) {
                check(Files.size(file) <= MAX_LIBRARY_BYTES) { "Bibliothèque trop volumineuse." }
                json.decodeFromString<LibrarySnapshot>(Files.readString(file)).also {
                    check(it.version == 1) { "Version de bibliothèque non prise en charge." }
                }
            } else LibrarySnapshot()
            _state.value = restored
            loaded = true
        }
    }

    suspend fun toggleFavorite(track: Track) = update { current ->
        current.copy(favorites = if (current.favorites.any { it.id == track.id })
            current.favorites.filterNot { it.id == track.id }
        else listOf(track) + current.favorites)
    }

    suspend fun recordPlay(track: Track) = update {
        it.copy(recents = (listOf(track) + it.recents.filterNot { previous -> previous.id == track.id }).take(100))
    }

    suspend fun savePlaylist(name: String, tracks: List<Track>) = update {
        require(name.isNotBlank() && tracks.isNotEmpty())
        it.copy(playlists = it.playlists + DesktopPlaylist(UUID.randomUUID().toString(), name.trim(), tracks))
    }

    suspend fun renamePlaylist(id: String, name: String) = update {
        require(name.isNotBlank())
        it.copy(playlists = it.playlists.map { playlist -> if (playlist.id == id) playlist.copy(name = name.trim()) else playlist })
    }

    suspend fun deletePlaylist(id: String) = update {
        it.copy(playlists = it.playlists.filterNot { playlist -> playlist.id == id })
    }

    suspend fun clearHistory() = update { it.copy(recents = emptyList()) }

    private suspend fun update(transform: (LibrarySnapshot) -> LibrarySnapshot) = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(loaded) { "La bibliothèque n'a pas pu être chargée. Le fichier existant est conservé." }
            val next = transform(_state.value)
            val encoded = json.encodeToString(next)
            check(encoded.toByteArray(Charsets.UTF_8).size <= MAX_LIBRARY_BYTES) { "Bibliothèque trop volumineuse." }
            Files.createDirectories(directory)
            val temp = Files.createTempFile(directory, "library-", ".tmp")
            try {
                Files.writeString(temp, encoded)
                Files.move(temp, file, ATOMIC_MOVE, REPLACE_EXISTING)
                _state.value = next
            } finally { Files.deleteIfExists(temp) }
        }
    }

    companion object {
        private const val MAX_LIBRARY_BYTES = 8L * 1024 * 1024
        fun defaultDirectory(): Path = Path.of(System.getProperty("user.home"), "Library", "Application Support", "Jelyvo")
    }
}
