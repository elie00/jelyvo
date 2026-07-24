package ca.ilianokokoro.umihi.music.core.spotify

import ca.ilianokokoro.umihi.music.BuildConfig

/**
 * Constants for the Spotify import feature.
 *
 * Auth flow used: Authorization Code with PKCE (no client secret — safe for a mobile client).
 *
 * The feature is hidden unless [CLIENT_ID] is supplied at build time (-PspotifyClientId=<id>, or a
 * `spotifyClientId=<id>` line in the gitignored local.properties), so no build ships a fake id.
 *
 * TODO(missing): TWO things are still required before the flow works end to end:
 *   1. Register the app on https://developer.spotify.com/dashboard, add [REDIRECT_URI]
 *      ("umihi://spotify-callback") to its "Redirect URIs", then build with that client id.
 *   2. Wire the redirect back: MainActivity.handleViewIntent ignores umihi://spotify-callback, so
 *      nothing calls [SpotifyAuthHelper.parseAuthorizationCode] / [SpotifyAuthHelper.exchangeCodeForToken]
 *      / SpotifyImportViewModel.onAuthCompleted, and the PKCE verifier built in NavigationRoot is
 *      discarded. Until that glue exists the screen stays on "Disconnected" even with a valid id.
 */
object SpotifyConstants {
    /** Empty unless a client id was supplied at build time — see [IS_CONFIGURED]. */
    val CLIENT_ID: String = BuildConfig.SPOTIFY_CLIENT_ID

    /** Gates the Spotify entry points: with no client id the OAuth flow can only fail. */
    val IS_CONFIGURED: Boolean = CLIENT_ID.isNotBlank()

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
