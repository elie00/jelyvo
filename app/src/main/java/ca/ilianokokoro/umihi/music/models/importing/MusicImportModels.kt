package ca.ilianokokoro.umihi.music.models.importing

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.models.Song

enum class MusicImportService {
    SPOTIFY,
    APPLE_MUSIC,
    DEEZER,
}

@Immutable
data class MusicImportTrack(
    val title: String,
    val artist: String,
    val durationMs: Long = 0L,
) {
    val searchQuery: String
        get() = listOf(artist, title)
            .filter(String::isNotBlank)
            .joinToString(" ")
}

@Immutable
data class ExternalMusicPlaylist(
    val name: String,
    val service: MusicImportService,
    val tracks: List<MusicImportTrack>,
)

@Immutable
data class MusicImportProgress(
    val playlistName: String,
    val total: Int,
    val processed: Int = 0,
    val matched: List<Song> = emptyList(),
    val failures: List<MusicImportTrack> = emptyList(),
    val createdPlaylistId: String? = null,
    val isComplete: Boolean = false,
) {
    val matchedCount: Int get() = matched.size
    val failureCount: Int get() = failures.size
}
