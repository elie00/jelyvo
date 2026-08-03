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
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

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
        searchFallback(title, artist, durationSeconds)
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

    private fun searchFallback(
        title: String,
        artist: String,
        durationSeconds: Int?,
    ): Lyrics? {
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

        return selectBestLyricsCandidate(
            results = results,
            title = title,
            artist = artist,
            durationSeconds = durationSeconds,
        )?.let(Lyrics::fromResponse)
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
            "Jelyvo (https://github.com/elie00/umihi-music)"
    }
}

/** Picks the closest LRCLIB row instead of trusting the API's first fuzzy-search result. */
internal fun selectBestLyricsCandidate(
    results: List<LrclibResponse>,
    title: String,
    artist: String,
    durationSeconds: Int?,
): LrclibResponse? {
    val wantedTitle = normalizeLyricsIdentity(title)
    val wantedArtist = normalizeLyricsIdentity(artist)
    return results
        .asSequence()
        .filter(LrclibResponse::hasLyrics)
        .map { candidate ->
            val candidateTitle = normalizeLyricsIdentity(candidate.trackName.orEmpty())
            val candidateArtist = normalizeLyricsIdentity(candidate.artistName.orEmpty())
            var score = when {
                candidateTitle.isNotBlank() && candidateTitle == wantedTitle -> 8
                candidateTitle.isNotBlank() && wantedTitle.isNotBlank() &&
                    (candidateTitle.contains(wantedTitle) || wantedTitle.contains(candidateTitle)) -> 4
                else -> 0
            }
            score += when {
                candidateArtist.isNotBlank() && candidateArtist == wantedArtist -> 6
                candidateArtist.isNotBlank() && wantedArtist.isNotBlank() &&
                    (candidateArtist.contains(wantedArtist) ||
                        wantedArtist.contains(candidateArtist)) -> 3
                else -> 0
            }
            if (durationSeconds != null && candidate.duration != null) {
                score += when (abs(candidate.duration - durationSeconds)) {
                    in 0.0..2.5 -> 4
                    in 2.5..8.0 -> 2
                    else -> -2
                }
            }
            if (!candidate.syncedLyrics.isNullOrBlank()) score += 1
            candidate to score
        }
        .maxByOrNull { it.second }
        ?.takeIf { it.second >= MIN_LYRICS_MATCH_SCORE }
        ?.first
}

internal fun normalizeLyricsIdentity(value: String): String = Normalizer
    .normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
    .replace(Regex("""\([^)]*(official|audio|video|lyrics?|remaster(?:ed)?)[^)]*\)"""), " ")
    .replace(Regex("[^a-z0-9]+"), " ")
    .trim()

private const val MIN_LYRICS_MATCH_SCORE = 4
