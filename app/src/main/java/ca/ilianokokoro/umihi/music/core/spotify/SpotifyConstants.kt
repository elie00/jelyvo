package ca.ilianokokoro.umihi.music.core.spotify

/**
 * Constants for the Spotify import feature.
 *
 * Auth flow used: Authorization Code with PKCE (no client secret — safe for a mobile client).
 *
 * TODO(missing): Register the app on https://developer.spotify.com/dashboard, then:
 *   1. Paste the real Client ID into [CLIENT_ID].
 *   2. Add [REDIRECT_URI] ("umihi://spotify-callback") to the app's "Redirect URIs" in the dashboard.
 * Until both are done the OAuth flow will fail at runtime.
 */
object SpotifyConstants {
    /** Placeholder — replace once the app is registered on the Spotify developer dashboard. */
    const val CLIENT_ID = "TODO_SET_SPOTIFY_CLIENT_ID"

    /** Must match EXACTLY the redirect URI registered on the Spotify dashboard AND the manifest intent-filter. */
    const val REDIRECT_URI = "umihi://spotify-callback"

    /** Space-separated OAuth scopes requested during authorization. */
    const val SCOPES = "playlist-read-private user-library-read"

    /** Spotify max page size for the endpoints used here. */
    const val PAGE_LIMIT = 50

    object Endpoints {
        const val AUTHORIZE = "https://accounts.spotify.com/authorize"
        const val TOKEN = "https://accounts.spotify.com/api/token"
        const val API_BASE = "https://api.spotify.com/v1"

        const val CURRENT_USER_PLAYLISTS = "$API_BASE/me/playlists"
        const val SAVED_TRACKS = "$API_BASE/me/tracks"

        fun playlistTracks(playlistId: String) = "$API_BASE/playlists/$playlistId/tracks"
    }
}
