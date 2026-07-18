package ca.ilianokokoro.umihi.music.core.spotify

import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.models.spotify.SpotifyImportTrack
import ca.ilianokokoro.umihi.music.models.spotify.SpotifyPlaylist
import ca.ilianokokoro.umihi.music.models.spotify.SpotifyPlaylistsPage
import ca.ilianokokoro.umihi.music.models.spotify.SpotifySavedTracksPage
import ca.ilianokokoro.umihi.music.models.spotify.SpotifyTracksPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * Reads the current user's Spotify library. All calls require a valid OAuth access token
 * (Bearer). Pagination is followed via the `next` field until exhausted.
 *
 * Throws [IOException] on network / non-2xx responses (repositories map these to ApiResult).
 */
object SpotifyApiClient {
    private val json = Json { ignoreUnknownKeys = true }

    /** GET /me/playlists — all pages. */
    suspend fun getCurrentUserPlaylists(accessToken: String): List<SpotifyPlaylist> =
        withContext(Dispatchers.IO) {
            val playlists = mutableListOf<SpotifyPlaylist>()
            var nextUrl: String? = firstPageUrl(SpotifyConstants.Endpoints.CURRENT_USER_PLAYLISTS)

            while (nextUrl != null) {
                val page = json.decodeFromString<SpotifyPlaylistsPage>(get(nextUrl, accessToken))
                playlists += page.items
                nextUrl = page.next
            }
            playlists
        }

    /** GET /playlists/{id}/tracks — all pages, mapped to internal tracks. */
    suspend fun getPlaylistTracks(
        accessToken: String,
        playlistId: String,
    ): List<SpotifyImportTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<SpotifyImportTrack>()
        var nextUrl: String? =
            firstPageUrl(SpotifyConstants.Endpoints.playlistTracks(playlistId))

        while (nextUrl != null) {
            val page = json.decodeFromString<SpotifyTracksPage>(get(nextUrl, accessToken))
            page.items
                .mapNotNull { it.track }
                .filter { !it.isLocal && it.name.isNotBlank() }
                .forEach { tracks += SpotifyImportTrack.fromTrack(it) }
            nextUrl = page.next
        }
        tracks
    }

    /** GET /me/tracks (Liked Songs) — all pages, mapped to internal tracks. */
    suspend fun getSavedTracks(accessToken: String): List<SpotifyImportTrack> =
        withContext(Dispatchers.IO) {
            val tracks = mutableListOf<SpotifyImportTrack>()
            var nextUrl: String? = firstPageUrl(SpotifyConstants.Endpoints.SAVED_TRACKS)

            while (nextUrl != null) {
                val page = json.decodeFromString<SpotifySavedTracksPage>(get(nextUrl, accessToken))
                page.items
                    .mapNotNull { it.track }
                    .filter { !it.isLocal && it.name.isNotBlank() }
                    .forEach { tracks += SpotifyImportTrack.fromTrack(it) }
                nextUrl = page.next
            }
            tracks
        }

    private fun firstPageUrl(baseUrl: String): String {
        return baseUrl.toHttpUrl()
            .newBuilder()
            .addQueryParameter("limit", SpotifyConstants.PAGE_LIMIT.toString())
            .build()
            .toString()
    }

    private fun get(url: String, accessToken: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        UmihiHttpClient.client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                throw IOException("Spotify API HTTP ${response.code}: ${body?.take(500) ?: "null"}")
            }
            return body ?: throw IOException("Empty Spotify API response body")
        }
    }
}
