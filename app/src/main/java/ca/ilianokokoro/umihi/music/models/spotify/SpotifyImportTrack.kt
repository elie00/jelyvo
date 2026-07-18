package ca.ilianokokoro.umihi.music.models.spotify

import androidx.compose.runtime.Immutable

/**
 * Internal, UI-facing representation of a track to import, decoupled from the raw Spotify DTOs.
 */
@Immutable
data class SpotifyImportTrack(
    val title: String,
    val artists: List<String>,
    val durationMs: Long,
    val album: String,
) {
    val artistsLabel: String
        get() = artists.joinToString(", ")

    /** Query used to match this track on YouTube: "artist(s) title". */
    val youtubeSearchQuery: String
        get() = listOf(artistsLabel, title)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()

    companion object {
        fun fromTrack(track: SpotifyTrack): SpotifyImportTrack {
            return SpotifyImportTrack(
                title = track.name,
                artists = track.artists.map { it.name }.filter { it.isNotBlank() },
                durationMs = track.durationMs,
                album = track.album?.name ?: "",
            )
        }
    }
}
