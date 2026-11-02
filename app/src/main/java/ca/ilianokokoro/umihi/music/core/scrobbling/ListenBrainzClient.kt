package ca.ilianokokoro.umihi.music.core.scrobbling

import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** How a submission ended, which decides what happens to the listens waiting for it. */
enum class SubmitResult {
    Accepted,

    /** Offline, rate limited or a server fault: keep the listens and try again later. */
    RetryLater,

    /** Bad or revoked credentials: keep the listens, retrying before the user acts is pointless. */
    Unauthorized,

    /** The service refused this content: retrying can never help, so drop it. */
    Rejected,
}

/** Maps an HTTP status from a scrobbling service to what should happen next. */
internal fun submitResultOf(httpStatus: Int): SubmitResult = when {
    httpStatus in 200..299 -> SubmitResult.Accepted
    httpStatus == 401 || httpStatus == 403 -> SubmitResult.Unauthorized
    httpStatus == 408 || httpStatus == 429 || httpStatus >= 500 -> SubmitResult.RetryLater
    else -> SubmitResult.Rejected
}

/** Reads the `valid` flag of a validate-token reply. Anything unreadable counts as invalid. */
internal fun isValidTokenResponse(body: String): Boolean =
    runCatching {
        Json.parseToJsonElement(body).jsonObject["valid"]?.jsonPrimitive?.booleanOrNull == true
    }.getOrDefault(false)

/** Request bodies for the ListenBrainz submit-listens endpoint. */
internal object ListenBrainzPayload {
    private const val SUBMISSION_CLIENT = "Jelyvo"

    fun playingNow(track: ScrobbleTrack): JsonObject = envelope("playing_now", listOf(track)) {
        metadataOf(it)
    }

    /** One listen goes as `single`; a backlog must use `import`, the only multi-listen type. */
    fun listens(tracks: List<ScrobbleTrack>): JsonObject =
        envelope(if (tracks.size == 1) "single" else "import", tracks) {
            buildJsonObject {
                put("listened_at", it.startedAtMs / 1000L)
                metadataOf(it).forEach { (key, value) -> put(key, value) }
            }
        }

    private fun envelope(
        type: String,
        tracks: List<ScrobbleTrack>,
        listen: (ScrobbleTrack) -> JsonObject,
    ): JsonObject = buildJsonObject {
        put("listen_type", type)
        putJsonArray("payload") {
            tracks.forEach { track ->
                addJsonObject { listen(track).forEach { (key, value) -> put(key, value) } }
            }
        }
    }

    private fun metadataOf(track: ScrobbleTrack): JsonObject = buildJsonObject {
        putJsonObject("track_metadata") {
            put("artist_name", track.artist)
            put("track_name", track.title)
            track.album?.takeIf { it.isNotBlank() }?.let { put("release_name", it) }
            putJsonObject("additional_info") {
                put("submission_client", SUBMISSION_CLIENT)
                // The API rejects a non-positive duration, and the field is optional.
                if (track.durationMs > 0L) put("duration_ms", track.durationMs)
            }
        }
    }
}

/** ListenBrainz over its token API. The token is the user's own, read from their profile page. */
object ListenBrainzClient {
    private const val BASE_URL = "https://api.listenbrainz.org/1"
    private val JSON = "application/json".toMediaType()

    suspend fun nowPlaying(token: String, track: ScrobbleTrack): SubmitResult =
        submit(token, ListenBrainzPayload.playingNow(track))

    suspend fun submitListens(token: String, tracks: List<ScrobbleTrack>): SubmitResult =
        submit(token, ListenBrainzPayload.listens(tracks))

    /** True when the service recognises the token. Throws [IOException] if it cannot be reached. */
    suspend fun isTokenValid(token: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/validate-token")
            .header("Authorization", "Token $token")
            .build()
        UmihiHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use false
            isValidTokenResponse(response.body?.string().orEmpty())
        }
    }

    private suspend fun submit(token: String, body: JsonObject): SubmitResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL/submit-listens")
                .header("Authorization", "Token $token")
                .post(body.toString().toRequestBody(JSON))
                .build()
            try {
                UmihiHttpClient.client.newCall(request).execute().use { submitResultOf(it.code) }
            } catch (_: IOException) {
                SubmitResult.RetryLater
            }
        }
}
