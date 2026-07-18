package ca.ilianokokoro.umihi.music.data.datasources

import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.models.Lyrics
import ca.ilianokokoro.umihi.music.models.dto.LrclibResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.io.IOException

/**
 * Fetches lyrics from the free LRCLIB API (no authentication required).
 *
 * See https://lrclib.net/docs
 */
class LyricsDataSource {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Resolves the best available lyrics for a track.
     *
     * Strategy:
     *  1. Exact match via `GET /api/get` (uses artist, track and, when known,
     *     duration for the most accurate hit).
     *  2. Fallback to `GET /api/search` and pick the first result that actually
     *     carries lyrics.
     *
     * @return parsed [Lyrics] (synced when available, else plain), or `null`
     *  when nothing usable was found.
     */
    suspend fun getLyrics(
        title: String,
        artist: String,
        durationSeconds: Int?,
    ): Lyrics? = withContext(Dispatchers.IO) {
        exactMatch(title, artist, durationSeconds)?.let { return@withContext it }
        searchFallback(title, artist)
    }

    private fun exactMatch(title: String, artist: String, durationSeconds: Int?): Lyrics? {
        val urlBuilder = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("get")
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)

        durationSeconds?.let { urlBuilder.addQueryParameter("duration", it.toString()) }

        val body = get(urlBuilder.build().toString()) ?: return null
        val response = runCatching { json.decodeFromString<LrclibResponse>(body) }.getOrNull()
        return response?.let { Lyrics.fromResponse(it) }
    }

    private fun searchFallback(title: String, artist: String): Lyrics? {
        val url = BASE_URL.toHttpUrl().newBuilder()
            .addPathSegment("api")
            .addPathSegment("search")
            .addQueryParameter("q", "$title $artist")
            .build()
            .toString()

        val body = get(url) ?: return null
        val results = runCatching {
            json.decodeFromString<List<LrclibResponse>>(body)
        }.getOrNull().orEmpty()

        return results
            .asSequence()
            .filter { it.hasLyrics }
            .mapNotNull { Lyrics.fromResponse(it) }
            .firstOrNull()
    }

    /**
     * Performs a GET request. Returns the response body on success, or `null`
     * on a 404 (track/lyrics not found) so callers can fall back gracefully.
     * Throws [IOException] for other unexpected HTTP failures.
     */
    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()

        UmihiHttpClient.client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                return null
            }
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            return response.body?.string()
        }
    }

    companion object {
        private const val BASE_URL = "https://lrclib.net"
        private const val USER_AGENT =
            "UmihiMusic (https://github.com/elie00/umihi-music)"
    }
}
