package ca.ilianokokoro.umihi.music.core.youtube

import android.content.Context
import android.widget.Toast
import androidx.core.net.toUri
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printd
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper.safeArray
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper.safeObject
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.models.ArtistAlbum
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

private val visitorDataRef = AtomicReference<String?>(null)

/**
 * Thread-safe cache for the YouTube `visitorData` token, read and written by
 * concurrent stream resolutions (up to [Constants.Downloads.MAX_CONCURRENT_DOWNLOADS] at once).
 */
val visitorData: String?
    get() = visitorDataRef.get()

object YoutubeDataExtractor {
    private val HEADER_RENDERER_KEYS = listOf(
        "musicImmersiveHeaderRenderer",
        "musicVisualHeaderRenderer",
        "musicHeaderRenderer",
    )

    fun extractYouTubeVideoId(url: String): String? {
        val uri = url.toUri()

        return when {
            uri.host?.contains("youtu.be") == true -> uri.lastPathSegment
            uri.host?.contains("youtube.com") == true || uri.host?.contains("music.youtube.com") == true -> uri.getQueryParameter(
                "v"
            )

            else -> null
        }
    }

    fun getBestThumbnailUrl(thumbnailElement: JsonElement): String {
        val url =
            thumbnailElement.safeObject()?.get("musicThumbnailRenderer")
                ?.safeObject()?.get("thumbnail")
                ?.safeObject()?.get("thumbnails")
                ?.safeArray()?.lastOrNull()
                ?.safeObject()?.get("url")
                ?.jsonPrimitive?.contentOrNull ?: ""
        return url
    }

    fun getSongInfo(songMap: JsonElement, songInfoIndex: SongInfoType): String {
        return songMap.safeObject()?.get("flexColumns")
            ?.safeArray()?.getOrNull(songInfoIndex.index)
            ?.safeObject()?.get("musicResponsiveListItemFlexColumnRenderer")
            ?.safeObject()?.get("text")
            ?.safeObject()?.get("runs")
            ?.safeArray()?.getOrNull(0)
            ?.safeObject()?.get("text")
            ?.jsonPrimitive?.contentOrNull ?: ""
    }

    suspend fun extractPlaylists(
        jsonString: String,
        settings: UmihiSettings
    ): List<PlaylistInfo> {
        val json = Json.parseToJsonElement(jsonString).jsonObject
        val playlistInfos = mutableListOf<PlaylistInfo>()

        suspend fun parseGridRenderer(renderer: JsonObject) {
            renderer["items"]
                ?.safeArray()
                ?.forEach { item ->
                    parsePlaylistItem(item)?.let { playlist ->
                        playlistInfos.add(playlist)
                    }
                }

            val continuationToken = renderer["continuations"]
                ?.safeArray()
                ?.firstOrNull()
                ?.safeObject()
                ?.get("nextContinuationData")
                ?.safeObject()
                ?.get("continuation")
                ?.jsonPrimitive
                ?.contentOrNull

            if (continuationToken != null) {
                val continuationJson = YoutubeApiClient.requestContinuation(
                    continuationToken = continuationToken,
                    settings = settings,
                    //   fields = Constants.YoutubeApi.Browse.Fields.PLAYLISTS_CONTINUATION,
                )
                playlistInfos.addAll(
                    extractPlaylists(
                        jsonString = continuationJson,
                        settings = settings
                    )
                )
            }
        }

        suspend fun parseMusicLibraryRenderer(renderer: JsonObject) {
            renderer["contents"]
                ?.safeArray()
                ?.forEach { item ->
                    parseMusicLibraryItem(item)?.let { playlist ->
                        playlistInfos.add(playlist)
                    }
                }

            val continuationToken = renderer["continuations"]
                ?.safeArray()
                ?.firstOrNull()
                ?.safeObject()
                ?.get("nextContinuationData")
                ?.safeObject()
                ?.get("continuation")
                ?.jsonPrimitive
                ?.contentOrNull

            if (continuationToken != null) {
                val continuationJson = YoutubeApiClient.requestContinuation(
                    continuationToken = continuationToken,
                    settings = settings,
                    //  fields = Constants.YoutubeApi.Browse.Fields.PLAYLISTS_CONTINUATION,
                )
                playlistInfos.addAll(
                    extractPlaylists(
                        jsonString = continuationJson,
                        settings = settings
                    )
                )
            }
        }

        val tabs = json["contents"]
            ?.safeObject()
            ?.get("singleColumnBrowseResultsRenderer")
            ?.safeObject()
            ?.get("tabs")
            ?.safeArray()
            ?: json["contents"]
                ?.safeObject()
                ?.get("twoColumnBrowseResultsRenderer")
                ?.safeObject()
                ?.get("tabs")
                ?.safeArray()

        if (tabs != null) {
            val selectedTab = tabs
                .firstOrNull {
                    it.safeObject()?.get("tabRenderer")
                        ?.safeObject()
                        ?.get("selected")
                        ?.jsonPrimitive
                        ?.booleanOrNull == true
                }
                ?.safeObject()?.get("tabRenderer")?.safeObject()

            val sectionList = selectedTab
                ?.get("content")
                ?.safeObject()
                ?.get("sectionListRenderer")
                ?.safeObject()
                ?.get("contents")
                ?.safeArray()

            if (sectionList != null) {
                for (section in sectionList) {
                    val musicLibrary =
                        section.safeObject()?.get("musicLibraryRenderer")?.safeObject()
                    if (musicLibrary != null) {
                        parseMusicLibraryRenderer(musicLibrary)
                        continue
                    }

                    val gridRenderer = section.safeObject()?.get("gridRenderer")?.safeObject()
                    if (gridRenderer != null) {
                        parseGridRenderer(gridRenderer)
                        continue
                    }

                    val nestedSectionList = section.safeObject()
                        ?.get("sectionListRenderer")?.safeObject()
                        ?.get("contents")?.safeArray()
                    if (nestedSectionList != null) {
                        for (nested in nestedSectionList) {
                            val nestedMusicLibrary =
                                nested.safeObject()?.get("musicLibraryRenderer")?.safeObject()
                            if (nestedMusicLibrary != null) {
                                parseMusicLibraryRenderer(nestedMusicLibrary)
                                continue
                            }
                            val nestedGrid =
                                nested.safeObject()?.get("gridRenderer")?.safeObject()
                            if (nestedGrid != null) {
                                parseGridRenderer(nestedGrid)
                                continue
                            }
                        }
                    }
                }
            } else if (selectedTab != null) {
                val tabContent = selectedTab["content"]?.safeObject()
                val directMusicLibrary = tabContent?.get("musicLibraryRenderer")?.safeObject()
                if (directMusicLibrary != null) {
                    parseMusicLibraryRenderer(directMusicLibrary)
                }
                val directGrid = tabContent?.get("gridRenderer")?.safeObject()
                if (directGrid != null) {
                    parseGridRenderer(directGrid)
                }
                if (directMusicLibrary == null && directGrid == null) {
                    printd("extractPlaylists: tab content has no recognizable renderer. Keys: ${tabContent?.keys}")
                }
            }
        } else {
            printd("extractPlaylists: no tabbed browse result found (may be continuation response)")
        }

        val gridContinuation = json["continuationContents"]
            ?.safeObject()
            ?.get("gridContinuation")
            ?.safeObject()
        if (gridContinuation != null) {
            parseGridRenderer(gridContinuation)
        }

        val musicLibraryContinuation = json["continuationContents"]
            ?.safeObject()
            ?.get("musicLibraryContinuation")
            ?.safeObject()
        if (musicLibraryContinuation != null) {
            parseMusicLibraryRenderer(musicLibraryContinuation)
        }

        if (playlistInfos.isEmpty() && tabs == null && gridContinuation == null && musicLibraryContinuation == null) {
            printd("extractPlaylists: no playlists found in any recognizable structure. Root keys: ${json.keys}")
        }

        return playlistInfos.distinctBy {
            it.id.removePrefix("VL")
        }
    }

    private fun parsePlaylistItem(item: JsonElement): PlaylistInfo? {
        val playlistRenderer = item.safeObject()?.get("musicTwoRowItemRenderer")
            ?.safeObject()
            ?: return null

        val navigationEndpoint = playlistRenderer["navigationEndpoint"]
            ?.safeObject()
            ?: return null

        val isCreatePlaylistTile =
            navigationEndpoint["createPlaylistEndpoint"] != null

        if (isCreatePlaylistTile) {
            return null
        }

        val browseId = navigationEndpoint["browseEndpoint"]
            ?.safeObject()
            ?.get("browseId")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return null

        val title = playlistRenderer["title"]
            ?.safeObject()
            ?.get("runs")
            ?.safeArray()
            ?.getOrNull(0)
            ?.safeObject()
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return null

        val thumbnailRenderer = playlistRenderer["thumbnailRenderer"]
            ?: return null

        val thumbnailUrl = getBestThumbnailUrl(thumbnailRenderer)

        return PlaylistInfo(
            id = browseId,
            title = title,
            coverHref = thumbnailUrl
        )
    }

    private fun parseMusicLibraryItem(item: JsonElement): PlaylistInfo? {
        val renderer = item.safeObject()?.get("musicLibraryItemRenderer")
            ?.safeObject()
            ?: return null

        val navigationEndpoint = renderer["navigationEndpoint"]
            ?.safeObject()
            ?: return null

        val browseId = navigationEndpoint["browseEndpoint"]
            ?.safeObject()
            ?.get("browseId")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return null

        val title = renderer["title"]
            ?.safeObject()
            ?.get("runs")
            ?.safeArray()
            ?.getOrNull(0)
            ?.safeObject()
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return null

        val thumbnailRenderer = renderer["thumbnailRenderer"]
            ?: return null

        val thumbnailUrl = getBestThumbnailUrl(thumbnailRenderer)

        return PlaylistInfo(
            id = browseId,
            title = title,
            coverHref = thumbnailUrl
        )
    }


    fun extractCreatedPlaylist(jsonString: String): PlaylistInfo? {
        val json = Json.parseToJsonElement(jsonString).jsonObject

        val renderer = json["actions"]
            ?.safeArray()
            ?.firstNotNullOfOrNull { action ->
                action.safeObject()?.get("handlePlaylistCreationCommand")
                    ?.safeObject()
                    ?.get("createdPlaylist")
                    ?.safeObject()
                    ?.get("musicTwoRowItemRenderer")
                    ?.safeObject()
            }
            ?: return null

        val title = renderer["title"]
            ?.safeObject()
            ?.get("runs")
            ?.safeArray()
            ?.getOrNull(0)
            ?.safeObject()
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return null

        val browseId = renderer["navigationEndpoint"]
            ?.safeObject()
            ?.get("browseEndpoint")
            ?.safeObject()
            ?.get("browseId")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return null

        val thumbnailUrl = getBestThumbnailUrl(
            renderer["thumbnailRenderer"] ?: return null
        )

        return PlaylistInfo(
            id = browseId,
            title = title,
            coverHref = thumbnailUrl
        )
    }

    fun extractSearchResults(jsonString: String): List<Song> {
        val json = Json.parseToJsonElement(jsonString).jsonObject

        val tabs = json["contents"]
            ?.safeObject()?.get("tabbedSearchResultsRenderer")
            ?.safeObject()?.get("tabs")
            ?.safeArray() ?: return emptyList()


        val selectedTab = tabs.firstOrNull {
            it.safeObject()?.get("tabRenderer")
                ?.safeObject()?.get("selected")
                ?.jsonPrimitive?.booleanOrNull == true
        }?.safeObject()?.get("tabRenderer")?.safeObject() ?: return emptyList()

        val contents = selectedTab["content"]
            ?.safeObject()?.get("sectionListRenderer")
            ?.safeObject()?.get("contents")
            ?.safeArray() ?: return emptyList()

        val songRendererList =
            contents
                .firstNotNullOfOrNull {
                    it.safeObject()?.get("musicShelfRenderer")
                        ?.safeObject()?.get("contents")
                        ?.safeArray()
                }
                ?: return emptyList()

        return songRendererList.mapNotNull { extractSong(it) }
    }


    fun extractSongInfo(jsonString: String): Song {
        val json = Json.parseToJsonElement(jsonString).jsonObject
        val details = json["videoDetails"]?.safeObject()

        val videoId = details?.get("videoId")?.jsonPrimitive?.contentOrNull ?: ""
        val title = details?.get("title")?.jsonPrimitive?.contentOrNull ?: ""
        val author = details?.get("author")?.jsonPrimitive?.contentOrNull ?: ""
        val lengthSeconds: Int =
            details?.get("lengthSeconds")?.jsonPrimitive?.contentOrNull?.toInt()
                ?: 0

        val isExplicit = json["microformat"]
            ?.safeObject()?.get("microformatDataRenderer")
            ?.safeObject()?.get("familySafe")
            ?.jsonPrimitive?.booleanOrNull
            ?.let { !it }
            ?: false

        return Song(
            youtubeId = videoId,
            title = title,
            artist = author,
            duration = formatSecondsForYouTubeDisplay(lengthSeconds),
            thumbnailHref = extractHighQualityThumbnail(jsonString),
            isExplicit = isExplicit
        )
    }


    suspend fun extractSongList(jsonString: String, settings: UmihiSettings): List<Song> {
        val json = Json.parseToJsonElement(jsonString).jsonObject

        val contents = json["contents"]
            ?.safeObject()?.get("twoColumnBrowseResultsRenderer")
            ?.safeObject()?.get("secondaryContents")
            ?.safeObject()?.get("sectionListRenderer")
            ?.safeObject()?.get("contents")
            ?.safeArray()?.getOrNull(0)
            ?.safeObject()?.get("musicPlaylistShelfRenderer")
            ?.safeObject()?.get("contents")
            ?.safeArray()

        if (contents != null) {
            return parseSongsFromContents(contents, settings)
        }

        val altContents = json["contents"]
            ?.safeObject()?.get("singleColumnBrowseResultsRenderer")
            ?.safeObject()?.get("tabs")
            ?.safeArray()?.firstOrNull()
            ?.safeObject()?.get("tabRenderer")
            ?.safeObject()?.get("content")
            ?.safeObject()?.get("sectionListRenderer")
            ?.safeObject()?.get("contents")
            ?.safeArray()?.firstOrNull()
            ?.safeObject()?.get("musicPlaylistShelfRenderer")
            ?.safeObject()?.get("contents")
            ?.safeArray()

        if (altContents != null) {
            return parseSongsFromContents(altContents, settings)
        }

        printd("extractSongList: could not find playlist contents. Root keys: ${json.keys}")

        return emptyList()
    }

    /**
     * Extracts the track list of an album browse response. Albums use a slightly different shape
     * than playlists, so this first reuses the playlist path and, if that yields nothing, falls
     * back to a recursive scan collecting every song renderer on the page.
     */
    suspend fun extractAlbumSongs(jsonString: String, settings: UmihiSettings): List<Song> {
        val viaPlaylist = extractSongList(jsonString, settings)
        if (viaPlaylist.isNotEmpty()) {
            return viaPlaylist
        }

        val json = Json.parseToJsonElement(jsonString)
        val items = mutableListOf<JsonObject>()
        collectSongItems(json, items)

        return items
            .mapNotNull { extractSong(it) }
            .distinctBy { it.youtubeId }
    }

    private fun collectSongItems(element: JsonElement, out: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                if (element.containsKey("musicResponsiveListItemRenderer")) {
                    out.add(element)
                }
                element.values.forEach { collectSongItems(it, out) }
            }

            is JsonArray -> element.forEach { collectSongItems(it, out) }
            else -> {}
        }
    }

    /** Finds a continuation token (for pagination) anywhere in a response. */
    fun extractContinuationToken(jsonString: String): String? {
        return findContinuationToken(Json.parseToJsonElement(jsonString))
    }

    private fun findContinuationToken(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                element["nextContinuationData"]?.safeObject()
                    ?.get("continuation")?.jsonPrimitive?.contentOrNull?.let { return it }
                element["continuationCommand"]?.safeObject()
                    ?.get("token")?.jsonPrimitive?.contentOrNull?.let { return it }
                element.values.forEach { child -> findContinuationToken(child)?.let { return it } }
            }

            is JsonArray -> element.forEach { findContinuationToken(it)?.let { return it } }
            else -> {}
        }
        return null
    }

    /**
     * Finds the "Show all" endpoint of a shelf (its `bottomEndpoint.browseEndpoint`, browseId +
     * params) — used to open an artist's full song list. Targets `musicShelfRenderer` specifically
     * so it doesn't pick up header/tab links (e.g. "Featured").
     */
    fun extractMoreEndpoint(jsonString: String): Pair<String, String>? {
        return findShelfBottomEndpoint(Json.parseToJsonElement(jsonString))
    }

    private fun findShelfBottomEndpoint(element: JsonElement): Pair<String, String>? {
        when (element) {
            is JsonObject -> {
                val shelf = element["musicShelfRenderer"]?.safeObject()
                if (shelf != null) {
                    val browseEndpoint = shelf["bottomEndpoint"]?.safeObject()
                        ?.get("browseEndpoint")?.safeObject()
                    val browseId = browseEndpoint?.get("browseId")?.jsonPrimitive?.contentOrNull
                    val params = browseEndpoint?.get("params")?.jsonPrimitive?.contentOrNull
                    if (browseId != null && params != null) {
                        return browseId to params
                    }
                }
                element.values.forEach { child ->
                    findShelfBottomEndpoint(child)?.let { return it }
                }
            }

            is JsonArray -> element.forEach { findShelfBottomEndpoint(it)?.let { return it } }
            else -> {}
        }
        return null
    }

    /**
     * Returns the largest thumbnail URL in a response — used as an album cover, since album track
     * rows carry no per-track thumbnail so the biggest image on the page is the album art.
     */
    fun extractLargestThumbnail(jsonString: String): String? {
        var bestWidth = -1
        var bestUrl: String? = null

        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    val url = element["url"]?.jsonPrimitive?.contentOrNull
                    val width = element["width"]?.jsonPrimitive?.intOrNull
                    if (url != null && width != null && width > bestWidth) {
                        bestWidth = width
                        bestUrl = url
                    }
                    element.values.forEach { walk(it) }
                }

                is JsonArray -> element.forEach { walk(it) }
                else -> {}
            }
        }

        walk(Json.parseToJsonElement(jsonString))
        return bestUrl
    }

    /** Extracts songs from a search continuation response (musicShelfContinuation). */
    fun extractSearchContinuationSongs(jsonString: String): List<Song> {
        val json = Json.parseToJsonElement(jsonString)
        val items = mutableListOf<JsonObject>()
        collectSongItems(json, items)
        return items.mapNotNull { extractSong(it) }.distinctBy { it.youtubeId }
    }

    /**
     * Extracts the songs from a YouTube Music artist page browse response (browseId "UC…"). The
     * artist page mixes shelves and carousels, so we recursively collect every song renderer.
     */
    fun extractArtistSongs(jsonString: String): List<Song> {
        val json = Json.parseToJsonElement(jsonString)
        val items = mutableListOf<JsonObject>()
        collectSongItems(json, items)
        return items.mapNotNull { extractSong(it) }.distinctBy { it.youtubeId }
    }

    /** Extracts the album/single cards (musicTwoRowItemRenderer with an "MPRE…" browseId) of an artist page. */
    fun extractArtistAlbums(jsonString: String): List<ArtistAlbum> {
        val items = mutableListOf<JsonObject>()
        collectTwoRowItems(Json.parseToJsonElement(jsonString), items)
        return items.mapNotNull { it.toArtistAlbum() }.distinctBy { it.browseId }
    }

    private fun collectTwoRowItems(element: JsonElement, out: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                if (element.containsKey("musicTwoRowItemRenderer")) {
                    out.add(element)
                }
                element.values.forEach { collectTwoRowItems(it, out) }
            }

            is JsonArray -> element.forEach { collectTwoRowItems(it, out) }
            else -> {}
        }
    }

    private fun JsonObject.toArtistAlbum(): ArtistAlbum? {
        val renderer = this["musicTwoRowItemRenderer"]?.safeObject() ?: return null
        val browseId = renderer["navigationEndpoint"]?.safeObject()
            ?.get("browseEndpoint")?.safeObject()
            ?.get("browseId")?.jsonPrimitive?.contentOrNull ?: return null
        if (!browseId.startsWith("MPRE")) {
            return null
        }
        val title = renderer["title"]?.safeObject()
            ?.get("runs")?.safeArray()?.firstOrNull()
            ?.safeObject()?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
        val thumbnail = renderer["thumbnailRenderer"]?.safeObject()
            ?.get("musicThumbnailRenderer")?.safeObject()
            ?.get("thumbnail")?.safeObject()
            ?.get("thumbnails")?.safeArray()?.lastOrNull()
            ?.safeObject()?.get("url")?.jsonPrimitive?.contentOrNull ?: ""
        return ArtistAlbum(browseId = browseId, title = title, thumbnail = thumbnail)
    }

    /** Reads the artist name from an artist page header, if present. */
    fun extractArtistName(jsonString: String): String? {
        val header = Json.parseToJsonElement(jsonString).jsonObject["header"]?.safeObject()
            ?: return null
        for (key in HEADER_RENDERER_KEYS) {
            val name = header[key]?.safeObject()
                ?.get("title")?.safeObject()
                ?.get("runs")?.safeArray()?.firstOrNull()
                ?.safeObject()?.get("text")?.jsonPrimitive?.contentOrNull
            if (!name.isNullOrBlank()) {
                return name
            }
        }
        return null
    }

    /** Reads the artist header thumbnail (largest) from an artist page, if present. */
    fun extractArtistThumbnail(jsonString: String): String? {
        val header = Json.parseToJsonElement(jsonString).jsonObject["header"]?.safeObject()
            ?: return null
        for (key in HEADER_RENDERER_KEYS) {
            val thumbnails = header[key]?.safeObject()
                ?.get("thumbnail")?.safeObject()
                ?.get("musicThumbnailRenderer")?.safeObject()
                ?.get("thumbnail")?.safeObject()
                ?.get("thumbnails")?.safeArray()
            val url = thumbnails?.lastOrNull()?.safeObject()
                ?.get("url")?.jsonPrimitive?.contentOrNull
            if (!url.isNullOrBlank()) {
                return url
            }
        }
        return null
    }

    suspend fun extractContinuationSongs(jsonString: String, settings: UmihiSettings): List<Song> {
        val json = Json.parseToJsonElement(jsonString).jsonObject

        val contents = json["onResponseReceivedActions"]
            ?.safeArray()?.getOrNull(0)
            ?.safeObject()?.get("appendContinuationItemsAction")
            ?.safeObject()?.get("continuationItems")
            ?.safeArray()

        if (contents == null) {
            printd("extractContinuationSongs: no continuationItems found. Keys: ${json.keys}")
        }

        return parseSongsFromContents(contents, settings)
    }


    private fun formatSecondsForYouTubeDisplay(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    private fun extractHighQualityThumbnail(jsonString: String): String {
        val json = Json.parseToJsonElement(jsonString).jsonObject
        val url = json["videoDetails"]
            ?.safeObject()?.get("thumbnail")
            ?.safeObject()?.get("thumbnails")
            ?.safeArray()?.lastOrNull()
            ?.safeObject()?.get("url")
            ?.jsonPrimitive?.contentOrNull

        return url ?: ""
    }

    private suspend fun parseSongsFromContents(
        contents: JsonArray?,
        settings: UmihiSettings
    ): List<Song> {
        val songs = mutableListOf<Song>()
        if (contents == null) {
            return songs
        }

        for (shelf in contents) {
            val continuationContent = shelf.safeObject()?.get("continuationItemRenderer")

            if (continuationContent != null) {
                val token = continuationContent.safeObject()?.get("continuationEndpoint")
                    ?.safeObject()?.get("continuationCommand")
                    ?.safeObject()?.get("token")
                    ?.jsonPrimitive?.contentOrNull ?: ""

                val otherSongs = extractContinuationSongs(
                    YoutubeApiClient.requestContinuation(
                        continuationToken = token,
                        settings = settings,
                        // fields = Constants.YoutubeApi.Browse.Fields.SONGS_CONTINUATION,
                    ), settings
                )
                songs.addAll(otherSongs)

                continue
            }


            val song = extractSong(shelf) ?: continue
            songs.add(
                song
            )
        }

        return songs
    }

    fun extractSong(
        json: JsonElement,
    ): Song? {
        val songContent =
            json.safeObject()?.get("musicResponsiveListItemRenderer")?.safeObject() ?: return null
        // Album/artist track rows may share the cover and omit a per-track thumbnail.
        val thumbnailUrl = songContent["thumbnail"]?.let { getBestThumbnailUrl(it) } ?: ""

        val title = getSongInfo(songContent, SongInfoType.TITLE)
        val artist = getSongInfo(songContent, SongInfoType.ARTIST)
        val videoId = extractVideoId(songContent) ?: return null


        val duration = extractDuration(songContent)

        val isExplicit = songContent["badges"]
            ?.safeArray()
            ?.any { badge ->
                badge.safeObject()
                    ?.get("musicInlineBadgeRenderer")
                    ?.safeObject()
                    ?.get("icon")
                    ?.safeObject()
                    ?.get("iconType")
                    ?.jsonPrimitive
                    ?.contentOrNull == "MUSIC_EXPLICIT_BADGE"
            } ?: false

        val isLiked = songContent["menu"]
            ?.safeObject()?.get("menuRenderer")
            ?.safeObject()?.get("topLevelButtons")
            ?.safeArray()
            ?.firstOrNull { item ->
                item.safeObject()?.get("likeButtonRenderer") != null
            }
            ?.safeObject()?.get("likeButtonRenderer")
            ?.safeObject()?.get("likeStatus")
            ?.jsonPrimitive?.contentOrNull == "LIKE"

        val album = extractBrowseId(songContent, "MPRE")
        val artistRef = extractBrowseId(songContent, "UC")

        return Song(
            youtubeId = videoId,
            title = title,
            artist = artist,
            duration = duration,
            thumbnailHref = thumbnailUrl,
            isExplicit = isExplicit,
            isLiked = isLiked,
        ).also {
            it.albumId = album?.first
            it.albumName = album?.second
            it.artistId = artistRef?.first
        }

    }

    /**
     * Scans the flex-column runs of a search result for a browseEndpoint whose browseId starts with
     * [prefix] (e.g. "MPRE" for an album, "UC" for a YouTube Music artist page). Returns
     * (browseId, runText) when present. Not always exposed by YouTube, hence the nullable result.
     */
    /**
     * Extracts a track's videoId. Search/playlist items expose it via `playlistItemData`, but
     * album/artist page items instead put it on the title run's watchEndpoint or the play-button
     * overlay — so we fall back to those.
     */
    private fun extractVideoId(songContent: JsonObject): String? {
        songContent["playlistItemData"]?.safeObject()
            ?.get("videoId")?.jsonPrimitive?.contentOrNull
            ?.let { return it }

        val titleRuns = songContent["flexColumns"]?.safeArray()?.getOrNull(0)
            ?.safeObject()?.get("musicResponsiveListItemFlexColumnRenderer")?.safeObject()
            ?.get("text")?.safeObject()?.get("runs")?.safeArray()
        titleRuns?.forEach { run ->
            run.safeObject()?.get("navigationEndpoint")?.safeObject()
                ?.get("watchEndpoint")?.safeObject()
                ?.get("videoId")?.jsonPrimitive?.contentOrNull
                ?.let { return it }
        }

        return songContent["overlay"]?.safeObject()
            ?.get("musicItemThumbnailOverlayRenderer")?.safeObject()
            ?.get("content")?.safeObject()
            ?.get("musicPlayButtonRenderer")?.safeObject()
            ?.get("playNavigationEndpoint")?.safeObject()
            ?.get("watchEndpoint")?.safeObject()
            ?.get("videoId")?.jsonPrimitive?.contentOrNull
    }

    private fun extractBrowseId(songContent: JsonObject, prefix: String): Pair<String, String>? {
        val flexColumns = songContent["flexColumns"]?.safeArray() ?: return null
        for (column in flexColumns) {
            val runs = column.safeObject()
                ?.get("musicResponsiveListItemFlexColumnRenderer")?.safeObject()
                ?.get("text")?.safeObject()
                ?.get("runs")?.safeArray() ?: continue
            for (run in runs) {
                val browseId = run.safeObject()
                    ?.get("navigationEndpoint")?.safeObject()
                    ?.get("browseEndpoint")?.safeObject()
                    ?.get("browseId")?.jsonPrimitive?.contentOrNull
                if (browseId != null && browseId.startsWith(prefix)) {
                    val name = run.safeObject()?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
                    return browseId to name
                }
            }
        }
        return null
    }


    suspend fun getSongPlayerUrl(
        context: Context,
        song: Song,
        allowLocal: Boolean = false
    ): String {
        val localSongRepository = AppDatabase.getInstance(context).songRepository()
        var savedSong: Song? = null
        try {
            savedSong = localSongRepository.getSong(song.youtubeId)
        } catch (ex: Exception) {
            Toast.makeText(context, "Failed to get song from local repository", Toast.LENGTH_LONG)
                .show()
            printe(ex.toString())
        }

        if (savedSong != null) {
            if (allowLocal && savedSong.audioFilePath != null) {
                printd("${song.youtubeId} : Was downloaded")
                return savedSong.audioFilePath
            }

            if (savedSong.streamUrl != null) {
                if (isYoutubeUrlValid(savedSong.streamUrl)) {
                    printd("${song.youtubeId} : Got url from saved")
                    return savedSong.streamUrl
                }
                printd("${song.youtubeId} : Saved url was invalid")
            }
        }

        val newUri = getSongUrlFromYoutube(song)
        localSongRepository.setStreamUrl(songId = song.youtubeId, streamUrl = newUri)
        printd("${song.youtubeId} : Got url from YouTube and saved song")
        return newUri
    }


    private fun extractDuration(songContent: JsonObject): String {
        val durationRegex = Regex("""\d+:\d{2}(:\d{2})?""")

        val fixedDuration = songContent["fixedColumns"]
            ?.safeArray()
            ?.firstOrNull()
            ?.safeObject()
            ?.get("musicResponsiveListItemFixedColumnRenderer")
            ?.safeObject()
            ?.get("text")
            ?.safeObject()
            ?.get("runs")
            ?.safeArray()
            ?.firstOrNull()
            ?.safeObject()
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull

        if (fixedDuration != null) {
            return fixedDuration
        }

        val flexColumns = songContent["flexColumns"]
            ?.safeArray()
            ?: return ""

        for (column in flexColumns) {
            val runs = column.safeObject()?.get("musicResponsiveListItemFlexColumnRenderer")
                ?.safeObject()
                ?.get("text")
                ?.safeObject()
                ?.get("runs")
                ?.safeArray()
                ?: continue

            for (run in runs) {
                val text = run.safeObject()?.get("text")
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?: continue

                if (durationRegex.matches(text)) {
                    return text
                }
            }
        }

        return ""
    }

    private suspend fun getSongUrlFromYoutube(
        song: Song,
        retries: Int = Constants.YoutubeApi.RETRY_COUNT
    ): String {
        var lastError: Throwable? = null

        val fastUrl = resolveAndroidVrStreamUrl(song.youtubeId)

        if (fastUrl != null) {
            return fastUrl
        }

        printd("${song.youtubeId} : Falling back to NewPipe")
        repeat(retries) { attempt ->
            try {
                return withContext(Dispatchers.IO) {
                    resolveNewPipeStreamUrl(song)
                }
            } catch (e: Throwable) {
                lastError = e

                printe(
                    "${song.youtubeId} : Failed attempt ${attempt + 1}/$retries with NewPipe: " +
                            "${e::class.simpleName}: ${e.message ?: "no message"}"
                )

                if (attempt < retries - 1) {
                    delay((Constants.YoutubeApi.RETRY_DELAY * (attempt + 1)).milliseconds)
                }
            }
        }

        throw Exception(
            "${song.youtubeId} : Fatal fail. Could not get it after $retries attempts",
            lastError
        )
    }

    private suspend fun resolveAndroidVrStreamUrl(
        videoId: String,
        retries: Int = Constants.YoutubeApi.RETRY_COUNT,
    ): String? = withContext(Dispatchers.IO) {
        suspend fun executeRequest(): String? {
            val response = YoutubeApiClient.getPlayerInfo(
                videoId = videoId,
                client = Constants.YoutubeApi.Client.ANDROID_VR,
                visitorData = visitorData,
                //   fields = Constants.YoutubeApi.PlayerInfo.Fields.STREAM,
            )

            return extractStreamFromAndroidVrResponse(response)
        }

        repeat(retries) { attempt ->
            val previousVisitorData = visitorData

            executeRequest()?.let {
                return@withContext it
            }

            val visitorDataUpdated =
                previousVisitorData != visitorData

            val isLastAttempt =
                attempt >= retries - 1

            if (!visitorDataUpdated || isLastAttempt) {
                return@repeat
            }

            printd(
                "Retrying ANDROID_VR with updated visitorData " +
                        "(${attempt + 1}/$retries)"
            )
        }

        null
    }

    private fun resolveNewPipeStreamUrl(song: Song): String {
        val service = ServiceList.YouTube
        val extractor = service.getStreamExtractor(song.youtubeUrl)

        extractor.fetchPage()

        val bestAudioStream = extractor.audioStreams
            .filter { it.content.isNotBlank() }
            .maxByOrNull { it.averageBitrate }
            ?: error("No valid audio streams found")

        return bestAudioStream.content
    }

    private fun extractStreamFromAndroidVrResponse(
        text: String,
    ): String? {
        val root = Json.parseToJsonElement(text).jsonObject

        val newVisitorData = root["responseContext"]
            ?.safeObject()
            ?.get("visitorData")
            ?.jsonPrimitive
            ?.contentOrNull

        if (newVisitorData != null) {
            // First writer wins; concurrent resolutions never clobber each other.
            visitorDataRef.compareAndSet(null, newVisitorData)
        }

        val status = root["playabilityStatus"]
            ?.safeObject()
            ?.get("status")
            ?.jsonPrimitive
            ?.contentOrNull

        val reason = root["playabilityStatus"]
            ?.safeObject()
            ?.get("reason")
            ?.jsonPrimitive
            ?.contentOrNull

        val directUrl = root["streamingData"]
            ?.safeObject()
            ?.get("adaptiveFormats")
            ?.safeArray()
            ?.asSequence()
            ?.mapNotNull { it.safeObject() }
            ?.filter {
                it["url"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
            }
            ?.filter {
                it["mimeType"]
                    ?.jsonPrimitive
                    ?.contentOrNull
                    ?.startsWith("audio/", ignoreCase = true) == true
            }
            ?.maxByOrNull {
                it["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
            }
            ?.get("url")
            ?.jsonPrimitive
            ?.contentOrNull

        if (reason != null) {
            printe("ANDROID_VR Failed ($status). Reason : $reason ")
        }

        return directUrl
    }

    private suspend fun isYoutubeUrlValid(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .head()
                .build()

            UmihiHttpClient.client
                .newCall(request)
                .execute()
                .use { response ->
                    response.code in 200..399
                }
        } catch (_: Exception) {
            false
        }
    }
}


enum class SongInfoType(val index: Int) {
    TITLE(0),
    ARTIST(1),
}
