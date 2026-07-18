package ca.ilianokokoro.umihi.music.models.spotify

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of POST https://accounts.spotify.com/api/token (code exchange & refresh). */
@Serializable
data class SpotifyTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresIn: Int = 0,
    // Not returned on a refresh call; keep the previous one when null.
    @SerialName("refresh_token") val refreshToken: String? = null,
    val scope: String? = null,
)

@Serializable
data class SpotifyArtist(
    val id: String? = null,
    val name: String = "",
)

@Serializable
data class SpotifyImage(
    val url: String = "",
    val height: Int? = null,
    val width: Int? = null,
)

/** Minimal album shape — only what the matcher / UI needs. */
@Serializable
data class SpotifyAlbum(
    val id: String? = null,
    val name: String = "",
    val images: List<SpotifyImage> = emptyList(),
)

@Serializable
data class SpotifyTrack(
    val id: String? = null,
    val name: String = "",
    val artists: List<SpotifyArtist> = emptyList(),
    val album: SpotifyAlbum? = null,
    @SerialName("duration_ms") val durationMs: Long = 0L,
    @SerialName("is_local") val isLocal: Boolean = false,
)

/** Item wrapper returned by the playlist-tracks endpoint. */
@Serializable
data class SpotifyPlaylistTrackItem(
    val track: SpotifyTrack? = null,
)

/** Item wrapper returned by the saved-tracks endpoint. */
@Serializable
data class SpotifySavedTrackItem(
    val track: SpotifyTrack? = null,
)

/** Page of GET /playlists/{id}/tracks. */
@Serializable
data class SpotifyTracksPage(
    val items: List<SpotifyPlaylistTrackItem> = emptyList(),
    val next: String? = null,
    val total: Int = 0,
)

/** Page of GET /me/tracks. */
@Serializable
data class SpotifySavedTracksPage(
    val items: List<SpotifySavedTrackItem> = emptyList(),
    val next: String? = null,
    val total: Int = 0,
)

/** The `tracks` sub-object of a playlist object (only the total count is used). */
@Serializable
data class SpotifyPlaylistTracksRef(
    val total: Int = 0,
)

@Serializable
@Immutable
data class SpotifyPlaylist(
    val id: String = "",
    val name: String = "",
    val images: List<SpotifyImage> = emptyList(),
    val tracks: SpotifyPlaylistTracksRef = SpotifyPlaylistTracksRef(),
) {
    val coverHref: String
        get() = images.firstOrNull()?.url ?: ""

    val trackCount: Int
        get() = tracks.total
}

/** Page of GET /me/playlists. */
@Serializable
data class SpotifyPlaylistsPage(
    val items: List<SpotifyPlaylist> = emptyList(),
    val next: String? = null,
    val total: Int = 0,
)
