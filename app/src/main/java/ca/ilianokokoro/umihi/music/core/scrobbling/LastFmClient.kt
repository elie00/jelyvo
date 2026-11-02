package ca.ilianokokoro.umihi.music.core.scrobbling

import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException
import java.security.MessageDigest

/**
 * A Last.fm connection. The API key and secret belong to the user's own Last.fm API account: a
 * secret built into the app could be extracted from the APK by anyone.
 */
@Serializable
data class LastFmCredentials(
    val apiKey: String,
    val secret: String,
    val sessionKey: String,
)

/** The signed parameters of each Last.fm call. */
internal object LastFmRequests {
    /** Last.fm's signature: sorted name-value pairs, then the secret, as lowercase MD5. */
    fun signature(params: Map<String, String>, secret: String): String {
        val base = params.toSortedMap().entries.joinToString("") { it.key + it.value } + secret
        return MessageDigest.getInstance("MD5")
            .digest(base.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    fun authorizeUrl(apiKey: String, token: String): String =
        "https://www.last.fm/api/auth/?api_key=$apiKey&token=$token"

    fun nowPlaying(track: ScrobbleTrack): Map<String, String> = buildMap {
        put("method", "track.updateNowPlaying")
        put("artist", track.artist)
        put("track", track.title)
        track.album?.takeIf { it.isNotBlank() }?.let { put("album", it) }
        if (track.durationMs > 0L) put("duration", (track.durationMs / 1000L).toString())
    }

    fun scrobble(tracks: List<ScrobbleTrack>): Map<String, String> = buildMap {
        put("method", "track.scrobble")
        tracks.forEachIndexed { index, track ->
            put("artist[$index]", track.artist)
            put("track[$index]", track.title)
            put("timestamp[$index]", (track.startedAtMs / 1000L).toString())
            track.album?.takeIf { it.isNotBlank() }?.let { put("album[$index]", it) }
            if (track.durationMs > 0L) {
                put("duration[$index]", (track.durationMs / 1000L).toString())
            }
        }
    }

    /** Reads Last.fm's own error code, which is more precise than the HTTP status it comes with. */
    fun resultOf(httpStatus: Int, body: String): SubmitResult {
        val error = runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.int
        }.getOrNull() ?: return submitResultOf(httpStatus)
        return when (error) {
            // Authentication failed, invalid key, invalid session, invalid signature, suspended.
            4, 9, 10, 13, 26 -> SubmitResult.Unauthorized
            // Service offline, temporarily unavailable, rate limited.
            11, 16, 29 -> SubmitResult.RetryLater
            else -> SubmitResult.Rejected
        }
    }
}

/** Thrown when Last.fm answers an authorization step with an error. */
class LastFmAuthException(message: String) : Exception(message)

object LastFmClient {
    private const val API_URL = "https://ws.audioscrobbler.com/2.0/"

    /** First step of web authorization: a token the user then approves in their browser. */
    suspend fun requestToken(apiKey: String, secret: String): String =
        authCall(mapOf("method" to "auth.getToken", "api_key" to apiKey), secret) { body ->
            body["token"]?.jsonPrimitive?.content
        }

    /** Last step: trades an approved token for the session key that signs every later call. */
    suspend fun requestSessionKey(apiKey: String, secret: String, token: String): String =
        authCall(
            mapOf("method" to "auth.getSession", "api_key" to apiKey, "token" to token),
            secret,
        ) { body ->
            body["session"]?.jsonObject?.get("key")?.jsonPrimitive?.content
        }

    suspend fun nowPlaying(credentials: LastFmCredentials, track: ScrobbleTrack): SubmitResult =
        submit(credentials, LastFmRequests.nowPlaying(track))

    suspend fun scrobble(
        credentials: LastFmCredentials,
        tracks: List<ScrobbleTrack>,
    ): SubmitResult = submit(credentials, LastFmRequests.scrobble(tracks))

    private suspend fun submit(
        credentials: LastFmCredentials,
        params: Map<String, String>,
    ): SubmitResult = try {
        val signed = params + mapOf(
            "api_key" to credentials.apiKey,
            "sk" to credentials.sessionKey,
        )
        post(signed, credentials.secret) { status, body -> LastFmRequests.resultOf(status, body) }
    } catch (_: IOException) {
        SubmitResult.RetryLater
    }

    private suspend fun authCall(
        params: Map<String, String>,
        secret: String,
        read: (kotlinx.serialization.json.JsonObject) -> String?,
    ): String = post(params, secret) { _, body ->
        val reply = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
        reply?.let(read)
            ?: throw LastFmAuthException(
                reply?.get("message")?.jsonPrimitive?.content ?: "Unexpected Last.fm reply"
            )
    }

    private suspend fun <T> post(
        params: Map<String, String>,
        secret: String,
        onReply: (status: Int, body: String) -> T,
    ): T = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().apply {
            params.forEach { (name, value) -> add(name, value) }
            add("api_sig", LastFmRequests.signature(params, secret))
            // Sent but not signed: Last.fm excludes `format` from the signature.
            add("format", "json")
        }.build()
        val request = Request.Builder().url(API_URL).post(form).build()
        UmihiHttpClient.client.newCall(request).execute().use { response ->
            onReply(response.code, response.body?.string().orEmpty())
        }
    }
}
