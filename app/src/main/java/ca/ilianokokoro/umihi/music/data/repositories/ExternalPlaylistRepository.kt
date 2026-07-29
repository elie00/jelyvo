package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.models.importing.ExternalMusicPlaylist
import ca.ilianokokoro.umihi.music.models.importing.MusicImportService
import ca.ilianokokoro.umihi.music.models.importing.MusicImportTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Request
import java.io.IOException
import java.net.URI

/**
 * Reads public/shared playlist pages without embedding provider secrets in the APK.
 *
 * Spotify and Apple Music expose the visible playlist metadata in their official embeddable/web
 * pages. Deezer exposes public playlists through its public API. Private libraries still require
 * each provider's OAuth/MusicKit credentials and are deliberately not scraped.
 */
class ExternalPlaylistRepository(
    private val defaultPlaylistName: String = "Imported playlist",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun resolve(input: String): ExternalMusicPlaylist = withContext(Dispatchers.IO) {
        val sharedUrl = extractSharedPlaylistUrl(input)
            ?: throw IllegalArgumentException("No supported playlist URL")
        val expectedService = detectMusicImportService(sharedUrl)
            ?: throw IllegalArgumentException("Unsupported music service")
        val landing = fetch(sharedUrl)
        val service = detectMusicImportService(landing.finalUrl)
            ?: expectedService
        if (service != expectedService) {
            throw IllegalArgumentException("The playlist link redirected to another service")
        }

        val playlist = when (service) {
            MusicImportService.SPOTIFY -> {
                val id = extractSpotifyPlaylistId(landing.finalUrl)
                    ?: throw IllegalArgumentException("Invalid Spotify playlist URL")
                val embed = fetch("https://open.spotify.com/embed/playlist/$id")
                parseSpotifyEmbed(embed.body)
            }

            MusicImportService.APPLE_MUSIC -> parseAppleMusicPage(landing.body)

            MusicImportService.DEEZER -> {
                val id = extractDeezerPlaylistId(landing.finalUrl)
                    ?: throw IllegalArgumentException("Invalid Deezer playlist URL")
                parseDeezerPlaylist(fetch("https://api.deezer.com/playlist/$id").body)
            }
        }

        if (playlist.tracks.isEmpty()) {
            throw IllegalArgumentException("The shared playlist has no visible tracks")
        }
        playlist
    }

    private fun fetch(url: String): FetchResult {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_USER_AGENT)
            .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.7")
            .get()
            .build()
        return UmihiHttpClient.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Playlist request failed: HTTP ${response.code}")
            }
            FetchResult(
                finalUrl = response.request.url.toString(),
                body = response.body?.string()
                    ?: throw IOException("Playlist request returned an empty body"),
            )
        }
    }

    internal fun parseSpotifyEmbed(html: String): ExternalMusicPlaylist {
        val root = json.parseToJsonElement(
            extractScript(html, "__NEXT_DATA__")
                ?: throw IllegalArgumentException("Spotify playlist data is unavailable")
        )
        val entity = root.walkObjects()
            .firstOrNull { it["trackList"] is JsonArray }
            ?: throw IllegalArgumentException("Spotify playlist data is invalid")
        val tracks = entity["trackList"]
            ?.jsonArray
            .orEmpty()
            .mapNotNull { element ->
                val item = element.jsonObject
                val title = item.string("title").cleanProviderText()
                if (title.isBlank()) return@mapNotNull null
                MusicImportTrack(
                    title = title,
                    artist = item.string("subtitle").cleanProviderText(),
                    durationMs = item["duration"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }
        return ExternalMusicPlaylist(
            name = entity.string("name").ifBlank { entity.string("title") }
                .ifBlank { defaultPlaylistName },
            service = MusicImportService.SPOTIFY,
            tracks = tracks,
        )
    }

    internal fun parseAppleMusicPage(html: String): ExternalMusicPlaylist {
        val root = json.parseToJsonElement(
            extractScript(html, "serialized-server-data")
                ?: throw IllegalArgumentException("Apple Music playlist data is unavailable")
        )
        val objects = root.walkObjects().toList()
        val playlistObject = objects.firstOrNull { item ->
            item["contentDescriptor"]
                ?.asObject()
                ?.string("kind") == "playlist" &&
                item.string("title").isNotBlank()
        }
        val seenIds = mutableSetOf<String>()
        val tracks = objects.mapNotNull { item ->
            val descriptor = item["contentDescriptor"]?.asObject() ?: return@mapNotNull null
            if (descriptor.string("kind") != "song") return@mapNotNull null
            val title = item.string("title").cleanProviderText()
            if (title.isBlank()) return@mapNotNull null
            val id = descriptor["identifiers"]?.asObject()?.string("storeAdamID").orEmpty()
            if (id.isNotBlank() && !seenIds.add(id)) return@mapNotNull null
            MusicImportTrack(
                title = title,
                artist = item.string("artistName").cleanProviderText(),
                durationMs = parseDisplayDurationMs(item.string("duration")),
            )
        }
        return ExternalMusicPlaylist(
            name = playlistObject?.string("title").orEmpty().ifBlank { defaultPlaylistName },
            service = MusicImportService.APPLE_MUSIC,
            tracks = tracks,
        )
    }

    internal fun parseDeezerPlaylist(body: String): ExternalMusicPlaylist {
        val root = json.parseToJsonElement(body).jsonObject
        root["error"]?.let { throw IllegalArgumentException("Deezer playlist is unavailable") }
        val tracks = root["tracks"]
            ?.asObject()
            ?.get("data")
            ?.jsonArray
            .orEmpty()
            .mapNotNull(::parseDeezerTrack)
        return ExternalMusicPlaylist(
            name = root.string("title").cleanProviderText().ifBlank { defaultPlaylistName },
            service = MusicImportService.DEEZER,
            tracks = tracks,
        )
    }

    private fun parseDeezerTrack(element: JsonElement): MusicImportTrack? {
        val item = element.jsonObject
        val title = item.string("title").cleanProviderText()
        if (title.isBlank()) return null
        return MusicImportTrack(
            title = title,
            artist = item["artist"]?.asObject()?.string("name").orEmpty().cleanProviderText(),
            durationMs = (item["duration"]?.jsonPrimitive?.longOrNull ?: 0L) * 1_000L,
        )
    }

    private data class FetchResult(val finalUrl: String, val body: String)

    private companion object {
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36"
    }
}

internal fun extractSharedPlaylistUrl(input: String): String? =
    Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
        .find(input)
        ?.value
        ?.trimEnd('.', ',', ';', ')', ']', '}')

internal fun detectMusicImportService(url: String): MusicImportService? {
    val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
    return when {
        host == "open.spotify.com" || host.endsWith(".spotify.com") ||
            host == "spotify.link" -> MusicImportService.SPOTIFY
        host == "music.apple.com" -> MusicImportService.APPLE_MUSIC
        host == "deezer.com" || host.endsWith(".deezer.com") ||
            host == "deezer.page.link" || host == "link.deezer.com" ->
            MusicImportService.DEEZER
        else -> null
    }
}

internal fun extractSpotifyPlaylistId(url: String): String? =
    Regex("""/playlist/([A-Za-z0-9]+)""").find(url)?.groupValues?.getOrNull(1)

internal fun extractDeezerPlaylistId(url: String): String? =
    Regex("""/playlist/(\d+)""").find(url)?.groupValues?.getOrNull(1)

private fun extractScript(html: String, id: String): String? =
    Regex(
        """<script\b[^>]*\bid=["']${Regex.escape(id)}["'][^>]*>([\s\S]*?)</script>""",
        RegexOption.IGNORE_CASE,
    ).find(html)?.groupValues?.getOrNull(1)

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

private fun JsonElement.asObject(): JsonObject? = this as? JsonObject

private fun JsonObject.string(key: String): String =
    get(key)?.jsonPrimitive?.contentOrNull.orEmpty()

private fun String.cleanProviderText(): String =
    replace('\u00a0', ' ').replace(Regex("""\s+"""), " ").trim()

private fun parseDisplayDurationMs(value: String): Long {
    val parts = value.split(':').mapNotNull(String::toLongOrNull)
    if (parts.isEmpty() || parts.size > 3) return 0L
    val seconds = parts.fold(0L) { total, part -> total * 60L + part }
    return seconds * 1_000L
}
