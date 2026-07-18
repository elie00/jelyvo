package ca.ilianokokoro.umihi.music.ui.screens.spotifyimport

import ca.ilianokokoro.umihi.music.data.repositories.ImportProgress
import ca.ilianokokoro.umihi.music.models.spotify.SpotifyPlaylist

data class SpotifyImportState(
    val screenState: ScreenState = ScreenState.Disconnected,
    // Kept once OAuth completes; null while disconnected.
    val accessToken: String? = null,
)

sealed class ScreenState {
    /** No Spotify token yet — show the "Connect Spotify" button. */
    data object Disconnected : ScreenState()

    data object LoadingPlaylists : ScreenState()

    data class PlaylistsLoaded(
        val playlists: List<SpotifyPlaylist> = emptyList(),
    ) : ScreenState()

    /** A playlist / liked-songs import is running. */
    data class Importing(
        val progress: ImportProgress,
    ) : ScreenState()

    data class ImportComplete(
        val progress: ImportProgress,
    ) : ScreenState()

    data class Error(val exception: Exception) : ScreenState()
}
