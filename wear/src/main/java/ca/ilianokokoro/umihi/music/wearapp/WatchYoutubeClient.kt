package ca.ilianokokoro.umihi.music.wearapp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.schabi.newpipe.extractor.ServiceList

class WatchYoutubeClient(
    private val httpClient: OkHttpClient = defaultHttpClient(),
) {
    suspend fun searchSongs(query: String): List<WatchSong> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val body = """{
            "context":{"client":$WEB_REMIX_CLIENT,"user":{"lockedSafetyMode":false}},
            "query":${Json.encodeToString(query.trim())},
            "params":"$SONGS_FILTER"
        }""".trimIndent()
        parseSearchResponse(post(SEARCH_URL, body, WEB_REMIX_HEADERS))
    }

    suspend fun homeSongs(seedArtist: String? = null): List<WatchSong> = withContext(Dispatchers.IO) {
        val body = """{
            "context":{"client":$WEB_REMIX_CLIENT,"user":{"lockedSafetyMode":false}},
            "browseId":"FEmusic_home"
        }""".trimIndent()
        val directSongs = parseSearchResponse(post(BROWSE_URL, body, WEB_REMIX_HEADERS))
        if (directSongs.isNotEmpty()) directSongs
        else searchSongs(seedArtist?.takeIf(String::isNotBlank) ?: "Nouveautés musique")
    }

    suspend fun resolveAudioUrl(videoId: String): String = withContext(Dispatchers.IO) {
        val body = """{
            "context":{"client":$ANDROID_VR_CLIENT,"user":{"lockedSafetyMode":false}},
            "videoId":${Json.encodeToString(videoId)},
            "contentCheckOk":true,
            "racyCheckOk":true
        }""".trimIndent()
        val root = Json.parseToJsonElement(post(PLAYER_URL, body, ANDROID_VR_HEADERS)).jsonObject
        val status = root.objectAt("playabilityStatus")?.stringAt("status")
        val formats = root.objectAt("streamingData")
            ?.get("adaptiveFormats")
            ?.asArray()
            .orEmpty()
            .mapNotNull(JsonElement::asObject)
            .filter { format ->
                format.stringAt("mimeType")?.startsWith("audio/") == true &&
                    !format.stringAt("url").isNullOrBlank()
            }
        val best = formats.maxWithOrNull(
            compareBy<JsonObject> {
                when {
                    it.stringAt("mimeType")?.contains("opus") == true -> 3
                    it.stringAt("mimeType")?.contains("mp4a") == true -> 2
                    else -> 1
                }
            }.thenBy { it.intAt("bitrate") ?: 0 },
        )
        best?.stringAt("url") ?: resolveAudioWithNewPipe(videoId, status)
    }

    private fun resolveAudioWithNewPipe(videoId: String, fastStatus: String?): String {
        WatchYoutubeDownloader.ensureInitialized()
        return runCatching {
            ServiceList.YouTube
                .getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
                .apply { fetchPage() }
                .audioStreams
                .filter { it.content.isNotBlank() }
                .maxByOrNull { it.averageBitrate }
                ?.content
                ?: throw IOException("Aucun flux audio compatible")
        }.getOrElse { failure ->
            throw IOException(
                "Lecture indisponible${fastStatus?.let { " ($it)" }.orEmpty()}",
                failure,
            )
        }
    }

    internal fun parseSearchResponse(response: String): List<WatchSong> {
        val root = Json.parseToJsonElement(response)
        return root.walkObjects()
            .mapNotNull { it["musicResponsiveListItemRenderer"]?.asObject() }
            .mapNotNull(::parseSongRenderer)
            .distinctBy(WatchSong::videoId)
            .take(MAX_RESULTS)
            .toList()
    }

    private fun parseSongRenderer(renderer: JsonObject): WatchSong? {
        val videoId = renderer.objectAt("playlistItemData")?.stringAt("videoId")
            ?: renderer.walkObjects().mapNotNull { objectValue ->
                objectValue.objectAt("watchEndpoint")?.stringAt("videoId")
            }.firstOrNull()
            ?: return null

        val columns = renderer["flexColumns"]?.asArray().orEmpty()
        val title = columns.getOrNull(0)
            ?.asObject()
            ?.columnRuns()
            ?.firstOrNull()
            ?.stringAt("text")
            ?.takeIf(String::isNotBlank)
            ?: return null

        val subtitleRuns = columns.getOrNull(1)?.asObject()?.columnRuns().orEmpty()
        val artist = subtitleRuns.firstOrNull { run ->
            run.walkObjects().any { nested ->
                nested.objectAt("browseEndpoint")?.stringAt("browseId")?.startsWith("UC") == true
            }
        }?.stringAt("text")
            ?: subtitleRuns.asSequence()
                .mapNotNull { it.stringAt("text") }
                .firstOrNull { it.isNotBlank() && it != " • " }
            ?: "Artiste inconnu"

        val thumbnails = renderer.objectAt("thumbnail")
            ?.walkObjects()
            ?.filter { it.stringAt("url") != null }
            ?.toList()
            .orEmpty()
        val artworkUrl = thumbnails.maxByOrNull { it.intAt("width") ?: 0 }
            ?.stringAt("url")
            .orEmpty()
        val duration = renderer.walkObjects()
            .mapNotNull { it.stringAt("text") }
            .firstOrNull { DURATION_REGEX.matches(it) }
            .orEmpty()

        val isExplicit = renderer.walkObjects().any { it.stringAt("iconType") == "MUSIC_EXPLICIT_BADGE" }
        return WatchSong(videoId, title, artist, artworkUrl, duration, isExplicit)
    }

    private fun JsonObject.columnRuns(): List<JsonObject> = objectAt(
        "musicResponsiveListItemFlexColumnRenderer",
    )?.objectAt("text")?.get("runs")?.asArray().orEmpty().mapNotNull(JsonElement::asObject)

    private fun post(url: String, body: String, headers: Map<String, String>): String {
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .apply { headers.forEach(::addHeader) }
            .build()
        return httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${responseBody.take(240)}")
            }
            responseBody.ifBlank { throw IOException("Réponse vide") }
        }
    }

    private companion object {
        const val SEARCH_URL = "https://music.youtube.com/youtubei/v1/search?prettyPrint=false"
        const val BROWSE_URL = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
        const val PLAYER_URL = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        const val SONGS_FILTER = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
        const val MAX_RESULTS = 30
        val DURATION_REGEX = Regex("""\d{1,2}:\d{2}(?::\d{2})?""")
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 16; Watch) AppleWebKit/537.36 Chrome/140 Safari/537.36"
        const val VR_USER_AGENT =
            "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3)"
        val WEB_REMIX_CLIENT get() = """{
            "clientName":"WEB_REMIX","clientVersion":"1.20250212.01.00",
            "userAgent":"$WEB_USER_AGENT","hl":"${Locale.getDefault().language.ifBlank { "fr" }}"
        }""".trimIndent()
        val ANDROID_VR_CLIENT get() = """{
            "clientName":"ANDROID_VR","clientVersion":"1.61.48","clientId":"28",
            "userAgent":"$VR_USER_AGENT","osName":"Android","osVersion":"12",
            "deviceMake":"Oculus","deviceModel":"Quest 3","androidSdkVersion":"32",
            "packageName":"com.google.android.apps.youtube.vr.oculus",
            "hl":"${Locale.getDefault().language.ifBlank { "fr" }}"
        }""".trimIndent()
        val WEB_REMIX_HEADERS = mapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "Origin" to "https://music.youtube.com",
            "Referer" to "https://music.youtube.com/",
            "User-Agent" to WEB_USER_AGENT,
            "X-YouTube-Client-Name" to "67",
            "X-YouTube-Client-Version" to "1.20250212.01.00",
        )
        val ANDROID_VR_HEADERS = mapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "Origin" to "https://www.youtube.com",
            "User-Agent" to VR_USER_AGENT,
            "X-YouTube-Client-Version" to "1.61.48",
        )

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

private fun JsonElement.asObject(): JsonObject? = this as? JsonObject
private fun JsonElement.asArray(): JsonArray? = this as? JsonArray
private fun JsonObject.objectAt(key: String): JsonObject? = get(key)?.asObject()
private fun JsonObject.stringAt(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull
private fun JsonObject.intAt(key: String): Int? =
    (get(key) as? JsonPrimitive)?.intOrNull

private fun JsonElement.walkObjects(): Sequence<JsonObject> = sequence {
    when (this@walkObjects) {
        is JsonObject -> {
            yield(this@walkObjects)
            values.forEach { yieldAll(it.walkObjects()) }
        }
        is JsonArray -> forEach { yieldAll(it.walkObjects()) }
        else -> Unit
    }
}
