package ca.ilianokokoro.umihi.music.core.youtube

import android.content.Context
import android.widget.Toast
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.PlaybackLoudnessStore
import ca.ilianokokoro.umihi.music.core.YoutubeExtractor
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printd
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.helpers.ConnectivityHelper
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper.safeArray
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper.safeObject
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.AudioQuality
import ca.ilianokokoro.umihi.music.models.AlbumHeaderInfo
import ca.ilianokokoro.umihi.music.models.Artist
import ca.ilianokokoro.umihi.music.models.ArtistAlbum
import ca.ilianokokoro.umihi.music.models.ArtistSection
import ca.ilianokokoro.umihi.music.models.HomeFeedItem
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import ca.ilianokokoro.umihi.music.models.MoodCategory
import ca.ilianokokoro.umihi.music.models.MoodCategorySection
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.SpellCorrection
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.schabi.newpipe.extractor.ServiceList
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
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
    private val VIEW_COUNT_KEYWORDS = listOf("view", "vue")
    private val PLAY_COUNT_KEYWORDS = listOf("play", "écoute", "ecoute", "lecture")

    private val HEADER_RENDERER_KEYS = listOf(
        "musicImmersiveHeaderRenderer",
        "musicVisualHeaderRenderer",
        "musicHeaderRenderer",
    )

    /** Delegates to [parseYoutubeVideoId] so the link forms are covered by JVM unit tests. */
    fun extractYouTubeVideoId(url: String): String? = parseYoutubeVideoId(url)

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

    fun extractSearchItems(jsonString: String, filter: SearchFilter): List<SearchItem> {
        val root = Json.parseToJsonElement(jsonString)
        return when (filter) {
            SearchFilter.Songs, SearchFilter.Videos -> {
                // Scoped song and video searches both return musicResponsiveListItemRenderer rows
                // whose payload is a playable videoId, so they share the same extraction.
                val songItems = mutableListOf<JsonObject>()
                collectSongItems(root, songItems)
                songItems.mapNotNull { item ->
                    extractSong(item)?.also { song ->
                        // Every row of the Videos tab is a video, even when its watch payload
                        // does not expose a musicVideoType.
                        if (filter == SearchFilter.Videos && !song.isVideo) {
                            song.isVideo = true
                            song.viewCount = item["musicResponsiveListItemRenderer"]
                                ?.safeObject()?.let(::extractViewCount)
                        }
                    }?.let(SearchItem::Track)
                }
            }
            SearchFilter.Albums, SearchFilter.Artists -> {
                // Scoped album/artist search returns musicResponsiveListItemRenderer list rows
                // (not musicTwoRowItemRenderer tiles). Classify each by its browseId, then keep
                // only the entity type this tab asked for.
                val rows = mutableListOf<JsonObject>()
                collectSongItems(root, rows)
                val items = rows.mapNotNull { it.toMixedSearchItem() }
                when (filter) {
                    SearchFilter.Albums -> items.filterIsInstance<SearchItem.Album>()
                    else -> items.filterIsInstance<SearchItem.Artist>()
                }
            }

            SearchFilter.All -> {
                // "Top results" mixes entity types: the musicShelf list rows (classified per item:
                // album/artist browseId, else a track) and the album/artist carousel tiles
                // (musicTwoRowItemRenderer). The top-result card shelf is surfaced separately
                // through [extractTopResult] so the UI can render it as a dedicated card.
                val rows = mutableListOf<JsonObject>()
                collectSongItems(root, rows)
                val tiles = mutableListOf<JsonObject>()
                collectTwoRowItems(root, tiles)
                rows.mapNotNull { it.toMixedSearchItem() } +
                        tiles.mapNotNull { it.twoRowToSearchItem() }
            }
        }.distinctBy(SearchItem::id)
    }

    /**
     * Extracts the saved albums of the account's library (`FEmusic_liked_albums`): a grid of
     * `musicTwoRowItemRenderer` tiles whose browseIds start with "MPRE".
     */
    fun extractLibraryAlbums(jsonString: String): List<SearchItem.Album> {
        val rows = mutableListOf<JsonObject>()
        collectTwoRowItems(Json.parseToJsonElement(jsonString), rows)
        return rows
            .mapNotNull { it.toSearchItem(SearchFilter.Albums) as? SearchItem.Album }
            .distinctBy(SearchItem.Album::id)
    }

    /**
     * Extracts the followed/subscribed artists of the account's library. Depending on the browse
     * feed the rows are either `musicResponsiveListItemRenderer` list rows or
     * `musicTwoRowItemRenderer` tiles, both navigating to a "UC…" artist browseId.
     */
    fun extractLibraryArtists(jsonString: String): List<SearchItem.Artist> {
        val root = Json.parseToJsonElement(jsonString)

        val listRows = mutableListOf<JsonObject>()
        collectSongItems(root, listRows)

        val tiles = mutableListOf<JsonObject>()
        collectTwoRowItems(root, tiles)

        return (listRows.mapNotNull { it.toMixedSearchItem() as? SearchItem.Artist } +
                tiles.mapNotNull { it.toSearchItem(SearchFilter.Artists) as? SearchItem.Artist })
            .distinctBy(SearchItem.Artist::id)
    }

    /**
     * The search response's spell correction, when the query looked misspelled: either a
     * `showingResultsForRenderer` (results were auto-corrected, the original query is offered
     * back) or a `didYouMeanRenderer` (results match the typed query, a correction is suggested).
     * Null when the response carries neither.
     */
    fun extractSpellCorrection(jsonString: String): SpellCorrection? {
        var correction: SpellCorrection? = null

        fun JsonObject.searchEndpoint(key: String): JsonObject? =
            this[key]?.safeObject()?.get("searchEndpoint")?.safeObject()

        fun JsonObject?.endpointQuery(): String? =
            this?.get("query")?.jsonPrimitive?.contentOrNull

        fun JsonObject?.endpointParams(): String? =
            this?.get("params")?.jsonPrimitive?.contentOrNull

        fun JsonObject.toCorrection(autoCorrected: Boolean): SpellCorrection? {
            val correctedEndpoint = searchEndpoint("correctedQueryEndpoint")
            val correctedQuery = correctedEndpoint.endpointQuery()
                ?: this["correctedQuery"].runsText().takeIf(String::isNotBlank)
                ?: return null
            val originalEndpoint = searchEndpoint("originalQueryEndpoint")
            return SpellCorrection(
                correctedQuery = correctedQuery,
                correctedParams = correctedEndpoint.endpointParams(),
                originalQuery = originalEndpoint.endpointQuery()
                    ?: this["originalQuery"].runsText().takeIf(String::isNotBlank),
                originalParams = originalEndpoint.endpointParams(),
                autoCorrected = autoCorrected,
            )
        }

        fun walk(element: JsonElement) {
            if (correction != null) return
            when (element) {
                is JsonObject -> {
                    element["showingResultsForRenderer"]?.safeObject()
                        ?.toCorrection(autoCorrected = true)
                        ?.let { correction = it }
                        ?: element["didYouMeanRenderer"]?.safeObject()
                            ?.toCorrection(autoCorrected = false)
                            ?.let { correction = it }
                    element.values.forEach(::walk)
                }

                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(Json.parseToJsonElement(jsonString))
        return correction
    }

    fun extractSearchSuggestions(jsonString: String): List<String> {
        val suggestions = mutableListOf<String>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    element["searchSuggestionRenderer"]?.safeObject()
                        ?.get("suggestion").runsText()
                        .takeIf(String::isNotBlank)
                        ?.let(suggestions::add)
                    element.values.forEach(::walk)
                }
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(Json.parseToJsonElement(jsonString))
        return suggestions.distinct().take(8)
    }

    private fun collectTwoRowItems(element: JsonElement, output: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                element["musicTwoRowItemRenderer"]?.safeObject()?.let(output::add)
                element.values.forEach { collectTwoRowItems(it, output) }
            }
            is JsonArray -> element.forEach { collectTwoRowItems(it, output) }
            else -> Unit
        }
    }

    private fun collectCardShelves(element: JsonElement, output: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                element["musicCardShelfRenderer"]?.safeObject()?.let(output::add)
                element.values.forEach { collectCardShelves(it, output) }
            }
            is JsonArray -> element.forEach { collectCardShelves(it, output) }
            else -> Unit
        }
    }

    /**
     * The unscoped ("All") search's top-result card (musicCardShelfRenderer), classified into an
     * artist, album or track item. Null when the response carries no card the app can render.
     */
    fun extractTopResult(jsonString: String): SearchItem? {
        val cards = mutableListOf<JsonObject>()
        collectCardShelves(Json.parseToJsonElement(jsonString), cards)
        return cards.firstNotNullOfOrNull { it.cardToSearchItem() }
    }

    /**
     * Classifies a top-result card (musicCardShelfRenderer) into an album, artist or track item.
     * The card's title run navigates either to an entity page (browseEndpoint) or, for song/video
     * top results, straight to playback (watchEndpoint).
     */
    private fun JsonObject.cardToSearchItem(): SearchItem? {
        val titleRun = this["title"]?.safeObject()?.get("runs")?.safeArray()
            ?.firstOrNull()?.safeObject()
        val endpoint = titleRun?.get("navigationEndpoint")?.safeObject()
        val browseId = endpoint?.get("browseEndpoint")?.safeObject()
            ?.get("browseId")?.jsonPrimitive?.contentOrNull
        val title = this["title"].runsText()
        val thumbnail = this["thumbnail"]?.let(::getBestThumbnailUrl).orEmpty()
        return when {
            browseId?.startsWith("UC") == true -> SearchItem.Artist(
                id = browseId,
                name = title,
                thumbnail = thumbnail,
            )

            browseId?.startsWith("MPRE") == true -> SearchItem.Album(
                id = browseId,
                title = title,
                subtitle = this["subtitle"].runsText(),
                thumbnail = thumbnail,
            )

            else -> cardToTrack(titleRun, title, thumbnail)?.let(SearchItem::Track)
        }
    }

    /**
     * A song/video top-result card: its title run carries a watchEndpoint instead of a browseId.
     * Subtitle runs read "Song • Artist • 3:45" (or "Video • Artist • 1.2M views • 3:45").
     */
    private fun JsonObject.cardToTrack(
        titleRun: JsonObject?,
        title: String,
        thumbnail: String,
    ): Song? {
        val videoId = titleRun?.get("navigationEndpoint")?.safeObject()
            ?.get("watchEndpoint")?.safeObject()
            ?.get("videoId")?.jsonPrimitive?.contentOrNull ?: return null

        val subtitleParts = this["subtitle"]?.safeObject()
            ?.get("runs")?.safeArray()
            ?.mapNotNull { it.safeObject()?.get("text")?.jsonPrimitive?.contentOrNull?.trim() }
            ?.filter { it.isNotEmpty() && it != "•" }
            .orEmpty()
        // First run is the entity type ("Song"/"Video"), the second the artist; the duration is
        // the trailing "m:ss"-shaped run.
        val artist = subtitleParts.getOrNull(1).orEmpty()
        val duration = subtitleParts.lastOrNull { it.matches(DURATION_REGEX) }.orEmpty()

        val isExplicit = this["subtitleBadges"]
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

        return Song(
            youtubeId = videoId,
            title = title,
            artist = artist,
            duration = duration,
            thumbnailHref = thumbnail,
            isExplicit = isExplicit,
        ).also { song ->
            val videoType = titleRun?.let(::musicVideoType)
            song.isVideo = videoType != null && videoType != MUSIC_VIDEO_TYPE_ATV
            if (song.isVideo) {
                // On video cards the run between the artist and the duration is the view count.
                song.viewCount = subtitleParts.drop(2)
                    .firstOrNull { !it.matches(DURATION_REGEX) }
            }
        }
    }

    /**
     * Classifies a musicResponsiveListItemRenderer wrapper from the unscoped ("All") results:
     * an item navigating to an album/artist page becomes that entity, anything else that has a
     * videoId becomes a track.
     */
    private fun JsonObject.toMixedSearchItem(): SearchItem? {
        val renderer = this["musicResponsiveListItemRenderer"]?.safeObject() ?: return null
        val browseId = renderer["navigationEndpoint"]?.safeObject()
            ?.get("browseEndpoint")?.safeObject()
            ?.get("browseId")?.jsonPrimitive?.contentOrNull
        val thumbnail = renderer["thumbnail"]?.let(::getBestThumbnailUrl).orEmpty()
        return when {
            browseId?.startsWith("MPRE") == true -> SearchItem.Album(
                id = browseId,
                title = flexColumnText(renderer, 0),
                subtitle = flexColumnText(renderer, 1),
                thumbnail = thumbnail,
            )

            browseId?.startsWith("UC") == true -> SearchItem.Artist(
                id = browseId,
                name = flexColumnText(renderer, 0),
                thumbnail = thumbnail,
            )

            else -> extractSong(this)?.let(SearchItem::Track)
        }
    }

    private fun flexColumnText(renderer: JsonObject, index: Int): String =
        renderer["flexColumns"]?.safeArray()?.getOrNull(index)
            ?.safeObject()?.get("musicResponsiveListItemFlexColumnRenderer")
            ?.safeObject()?.get("text").runsText()

    private fun JsonObject.toSearchItem(filter: SearchFilter): SearchItem? {
        val endpoint = this["navigationEndpoint"]?.safeObject()
            ?: this["title"]?.safeObject()?.get("runs")?.safeArray()
                ?.firstOrNull()?.safeObject()?.get("navigationEndpoint")?.safeObject()
            ?: return null
        val browseId = endpoint["browseEndpoint"]?.safeObject()
            ?.get("browseId")?.jsonPrimitive?.contentOrNull ?: return null
        val title = this["title"].runsText()
        val thumbnail = (this["thumbnailRenderer"] ?: this["thumbnail"])
            ?.let(::getBestThumbnailUrl).orEmpty()
        return when (filter) {
            SearchFilter.Albums -> browseId.takeIf { it.startsWith("MPRE") }?.let {
                SearchItem.Album(
                    id = it,
                    title = title,
                    subtitle = this["subtitle"].runsText(),
                    thumbnail = thumbnail,
                )
            }
            SearchFilter.Artists -> browseId.takeIf { it.startsWith("UC") }?.let {
                SearchItem.Artist(id = it, name = title, thumbnail = thumbnail)
            }
            SearchFilter.Songs, SearchFilter.Videos, SearchFilter.All -> null
        }
    }

    /**
     * Classifies a musicTwoRowItemRenderer tile (a carousel card in "Top results") into an album or
     * artist by its browseId prefix. Tracks/playlists (watch or "VL…" endpoints) return null, since
     * the "All" view already collects tracks from the list rows.
     */
    private fun JsonObject.twoRowToSearchItem(): SearchItem? {
        val renderer = this["musicTwoRowItemRenderer"]?.safeObject() ?: this
        val endpoint = renderer["navigationEndpoint"]?.safeObject()
            ?: renderer["title"]?.safeObject()?.get("runs")?.safeArray()
                ?.firstOrNull()?.safeObject()?.get("navigationEndpoint")?.safeObject()
            ?: return null
        val browseId = endpoint["browseEndpoint"]?.safeObject()
            ?.get("browseId")?.jsonPrimitive?.contentOrNull ?: return null
        val title = renderer["title"].runsText()
        val thumbnail = (renderer["thumbnailRenderer"] ?: renderer["thumbnail"])
            ?.let(::getBestThumbnailUrl).orEmpty()
        return when {
            browseId.startsWith("MPRE") -> SearchItem.Album(
                id = browseId,
                title = title,
                subtitle = renderer["subtitle"].runsText(),
                thumbnail = thumbnail,
            )

            browseId.startsWith("UC") -> SearchItem.Artist(
                id = browseId,
                name = title,
                thumbnail = thumbnail,
            )

            else -> null
        }
    }

    fun extractHomeFeed(jsonString: String): List<HomeFeedSection> {
        val shelves = mutableListOf<HomeShelf>()
        collectHomeShelves(Json.parseToJsonElement(jsonString), shelves)
        return shelves.mapNotNull { it.toSection() }
            .filter { it.items.isNotEmpty() }
            .distinctBy { section -> section.title to section.items.firstOrNull() }
    }

    /**
     * Parses a generic browse page (e.g. a mood/genre category opened via its params browse) into
     * home-style sections. Unlike [extractHomeFeed] it also walks plain `gridRenderer` sections,
     * whose headers may be absent, so untitled sections are kept.
     */
    fun extractBrowseSections(jsonString: String): List<HomeFeedSection> {
        val shelves = mutableListOf<HomeShelf>()
        collectHomeShelves(Json.parseToJsonElement(jsonString), shelves, includeGrids = true)
        return shelves.mapNotNull { it.toSection(allowBlankTitle = true) }
            .filter { it.items.isNotEmpty() }
            .distinctBy { section -> section.title to section.items.firstOrNull() }
    }

    /**
     * Parses the FEmusic_moods_and_genres hub: `gridRenderer` sections ("Moods & moments",
     * "Genres") whose items are `musicNavigationButtonRenderer` chips carrying the category's
     * `browseEndpoint` (browseId + params).
     */
    fun extractMoodsAndGenres(jsonString: String): List<MoodCategorySection> {
        val sections = mutableListOf<MoodCategorySection>()

        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    element["gridRenderer"]?.safeObject()?.let { grid ->
                        val title = grid["header"]?.safeObject()
                            ?.get("gridHeaderRenderer")?.safeObject()
                            ?.get("title").runsText()
                        val categories = grid["items"]?.safeArray().orEmpty()
                            .mapNotNull { it.toMoodCategory() }
                        if (categories.isNotEmpty()) {
                            sections += MoodCategorySection(title = title, categories = categories)
                        }
                        return
                    }
                    element.values.forEach(::walk)
                }
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }

        walk(Json.parseToJsonElement(jsonString))
        return sections
    }

    private fun JsonElement.toMoodCategory(): MoodCategory? {
        val renderer = safeObject()
            ?.get("musicNavigationButtonRenderer")?.safeObject() ?: return null
        val title = renderer["buttonText"].runsText().ifBlank { return null }
        val endpoint = renderer["clickCommand"]?.safeObject()
            ?.get("browseEndpoint")?.safeObject() ?: return null
        val browseId = endpoint["browseId"]?.jsonPrimitive?.contentOrNull ?: return null
        val params = endpoint["params"]?.jsonPrimitive?.contentOrNull ?: return null
        return MoodCategory(title = title, browseId = browseId, params = params)
    }

    private fun collectHomeShelves(
        element: JsonElement,
        output: MutableList<HomeShelf>,
        includeGrids: Boolean = false,
    ) {
        when (element) {
            is JsonObject -> {
                element["musicCarouselShelfRenderer"]?.safeObject()?.let {
                    output += HomeShelf(it, isCarousel = true)
                    return
                }
                element["musicShelfRenderer"]?.safeObject()?.let {
                    output += HomeShelf(it, isCarousel = false)
                    return
                }
                if (includeGrids) {
                    element["gridRenderer"]?.safeObject()?.let {
                        output += HomeShelf(it, isCarousel = false, isGrid = true)
                        return
                    }
                }
                element.values.forEach { collectHomeShelves(it, output, includeGrids) }
            }
            is JsonArray -> element.forEach { collectHomeShelves(it, output, includeGrids) }
            else -> Unit
        }
    }

    private fun HomeShelf.toSection(allowBlankTitle: Boolean = false): HomeFeedSection? {
        val title = when {
            isCarousel -> renderer["header"]?.safeObject()
                ?.let { header ->
                    header["musicCarouselShelfBasicHeaderRenderer"]
                        ?: header["musicCarouselShelfHeaderRenderer"]
                }
                ?.safeObject()?.get("title").runsText()

            isGrid -> renderer["header"]?.safeObject()
                ?.get("gridHeaderRenderer")?.safeObject()
                ?.get("title").runsText()

            else -> renderer["title"].runsText()
        }
        if (title.isBlank() && !allowBlankTitle) return null

        val items = renderer[if (isGrid) "items" else "contents"]?.safeArray().orEmpty()
            .mapNotNull { item ->
                extractSong(item)?.let(HomeFeedItem::Track)
                    ?: item.toHomeTwoRowItem()
            }
        return HomeFeedSection(title = title, items = items)
    }

    private fun JsonElement.toHomeTwoRowItem(): HomeFeedItem? {
        val renderer = safeObject()?.get("musicTwoRowItemRenderer")?.safeObject() ?: return null
        val title = renderer["title"].runsText()
        if (title.isBlank()) return null
        val subtitle = renderer["subtitle"].runsText()
        val thumbnail = (renderer["thumbnailRenderer"] ?: renderer["thumbnail"])
            ?.let(::getBestThumbnailUrl).orEmpty()
        val endpoint = renderer["navigationEndpoint"]?.safeObject()
            ?: renderer["title"]?.safeObject()?.get("runs")?.safeArray()
                ?.firstOrNull()?.safeObject()?.get("navigationEndpoint")?.safeObject()
            ?: return null
        val watchEndpoint = endpoint["watchEndpoint"]?.safeObject()
        val playlistId = watchEndpoint?.get("playlistId")?.jsonPrimitive?.contentOrNull
        if (!playlistId.isNullOrBlank()) {
            return HomeFeedItem.Playlist(
                playlist = PlaylistInfo(
                    id = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId",
                    title = title,
                    coverHref = thumbnail,
                ),
                subtitle = subtitle,
            )
        }
        val videoId = watchEndpoint?.get("videoId")?.jsonPrimitive?.contentOrNull
        if (!videoId.isNullOrBlank()) {
            return HomeFeedItem.Track(
                Song(
                    youtubeId = videoId,
                    title = title,
                    artist = subtitle,
                    thumbnailHref = thumbnail,
                )
            )
        }

        val browseId = endpoint["browseEndpoint"]?.safeObject()
            ?.get("browseId")?.jsonPrimitive?.contentOrNull ?: return null
        return when {
            browseId.startsWith("MPRE") -> HomeFeedItem.Album(
                browseId = browseId,
                title = title,
                subtitle = subtitle,
                thumbnail = thumbnail,
            )
            browseId.startsWith("UC") -> HomeFeedItem.Artist(
                browseId = browseId,
                name = title,
                thumbnail = thumbnail,
            )
            else -> HomeFeedItem.Playlist(
                playlist = PlaylistInfo(browseId, title, thumbnail),
                subtitle = subtitle,
            )
        }
    }

    private fun JsonElement?.runsText(): String = this?.safeObject()
        ?.get("runs")?.safeArray()
        ?.mapNotNull { it.safeObject()?.get("text")?.jsonPrimitive?.contentOrNull }
        ?.joinToString("")
        .orEmpty()

    private data class HomeShelf(
        val renderer: JsonObject,
        val isCarousel: Boolean,
        val isGrid: Boolean = false,
    )


    fun extractSongInfo(jsonString: String): Song {
        val json = Json.parseToJsonElement(jsonString).jsonObject
        val details = json["videoDetails"]?.safeObject()

        val videoId = details?.get("videoId")?.jsonPrimitive?.contentOrNull ?: ""
        val title = details?.get("title")?.jsonPrimitive?.contentOrNull ?: ""
        val author = details?.get("author")?.jsonPrimitive?.contentOrNull ?: ""
        // lengthSeconds arrives as a free-form string and is not always a plain Int (live and
        // upcoming items omit or garble it, and values past Int.MAX_VALUE have been seen). Falling
        // back to 0 renders as "0:00" and keeps the rest of the song usable, instead of failing the
        // whole parse into an ApiResult.Error that loses the track.
        val lengthSeconds: Int =
            details?.get("lengthSeconds")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
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
     * Extracts the album cover from an album browse response header. Album pages also embed other
     * carousels ("More from this artist", videos…), so scanning the whole page for the biggest
     * image can pick an unrelated cover or a 16:9 clip frame — the header thumbnail is the only
     * one guaranteed to be the album art.
     */
    fun extractAlbumCover(jsonString: String): String? {
        val json = Json.parseToJsonElement(jsonString)
        val header = findFirstObject(
            json,
            listOf("musicResponsiveHeaderRenderer", "musicDetailHeaderRenderer")
        ) ?: return null
        // "thumbnail" holds the cover; the header may also carry a small artist avatar
        // ("straplineThumbnail"), so restrict the search to the cover node.
        val cover = header["thumbnail"] ?: return null
        return largestThumbnailIn(cover)
    }

    /**
     * Extracts the header metadata of an album browse response: title, artist (+ browseId),
     * the "Album • 2023" subtitle, the "12 songs • 41 minutes" second subtitle and the
     * description. Fields the header doesn't carry are returned empty so callers can fall
     * back to locally-known values.
     */
    fun extractAlbumHeaderInfo(jsonString: String): AlbumHeaderInfo? {
        val header = findFirstObject(
            Json.parseToJsonElement(jsonString),
            listOf("musicResponsiveHeaderRenderer", "musicDetailHeaderRenderer")
        ) ?: return null

        val artistRun = header["straplineTextOne"]?.safeObject()
            ?.get("runs")?.safeArray()?.firstOrNull()?.safeObject()
        val artistId = artistRun?.get("navigationEndpoint")?.safeObject()
            ?.get("browseEndpoint")?.safeObject()
            ?.get("browseId")?.jsonPrimitive?.contentOrNull

        // Newer headers wrap the description in a musicDescriptionShelfRenderer; older detail
        // headers expose plain runs directly.
        val description = header["description"]?.safeObject()
            ?.get("musicDescriptionShelfRenderer")?.safeObject()
            ?.get("description").runsText()
            .ifEmpty { header["description"].runsText() }

        return AlbumHeaderInfo(
            title = header["title"].runsText(),
            artist = header["straplineTextOne"].runsText(),
            artistId = artistId,
            subtitle = header["subtitle"].runsText(),
            secondSubtitle = header["secondSubtitle"].runsText(),
            description = description,
        )
    }

    /**
     * Album carousels of an album page (e.g. "Other versions"): every carousel shelf whose
     * items are albums, with its localized shelf title. Video-only carousels are dropped.
     */
    fun extractAlbumSections(jsonString: String): List<ArtistSection> {
        val shelves = mutableListOf<JsonObject>()
        collectCarouselShelves(Json.parseToJsonElement(jsonString), shelves)
        return shelves
            .mapNotNull { it.toArtistSection() }
            .map { it.copy(videos = emptyList()) }
            .filter { it.albums.isNotEmpty() }
    }

    private fun findFirstObject(element: JsonElement, keys: List<String>): JsonObject? {
        when (element) {
            is JsonObject -> {
                for (key in keys) {
                    element[key]?.safeObject()?.let { return it }
                }
                element.values.forEach { child ->
                    findFirstObject(child, keys)?.let { return it }
                }
            }

            is JsonArray -> element.forEach { findFirstObject(it, keys)?.let { return it } }
            else -> {}
        }
        return null
    }

    /**
     * Returns the largest thumbnail URL in a response, preferring square images (album/track art)
     * over 16:9 video frames. Fallback when no header cover is available.
     */
    fun extractLargestThumbnail(jsonString: String): String? {
        return largestThumbnailIn(Json.parseToJsonElement(jsonString))
    }

    private fun largestThumbnailIn(root: JsonElement): String? {
        var bestSquareWidth = -1
        var bestSquareUrl: String? = null
        var bestAnyWidth = -1
        var bestAnyUrl: String? = null

        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    val url = element["url"]?.jsonPrimitive?.contentOrNull
                    val width = element["width"]?.jsonPrimitive?.intOrNull
                    val height = element["height"]?.jsonPrimitive?.intOrNull
                    if (url != null && width != null) {
                        if (width > bestAnyWidth) {
                            bestAnyWidth = width
                            bestAnyUrl = url
                        }
                        if (width == height && width > bestSquareWidth) {
                            bestSquareWidth = width
                            bestSquareUrl = url
                        }
                    }
                    element.values.forEach { walk(it) }
                }

                is JsonArray -> element.forEach { walk(it) }
                else -> {}
            }
        }

        walk(root)
        return bestSquareUrl ?: bestAnyUrl
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

    /**
     * Extracts the carousel sections of an artist page ("Albums", "Singles", "Videos", …), keeping
     * them separate. Each shelf becomes an [ArtistSection] with its album cards and/or videos.
     */
    fun extractArtistSections(jsonString: String): List<ArtistSection> {
        val shelves = mutableListOf<JsonObject>()
        collectCarouselShelves(Json.parseToJsonElement(jsonString), shelves)
        return shelves
            .mapNotNull { it.toArtistSection() }
            .filter { it.albums.isNotEmpty() || it.videos.isNotEmpty() || it.artists.isNotEmpty() }
    }

    private fun collectCarouselShelves(element: JsonElement, out: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                if (element.containsKey("musicCarouselShelfRenderer")) {
                    out.add(element)
                }
                element.values.forEach { collectCarouselShelves(it, out) }
            }

            is JsonArray -> element.forEach { collectCarouselShelves(it, out) }
            else -> {}
        }
    }

    private fun JsonObject.toArtistSection(): ArtistSection? {
        val shelf = this["musicCarouselShelfRenderer"]?.safeObject() ?: return null
        val title = shelf["header"]?.safeObject()
            ?.get("musicCarouselShelfBasicHeaderRenderer")?.safeObject()
            ?.get("title")?.safeObject()
            ?.get("runs")?.safeArray()?.firstOrNull()
            ?.safeObject()?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
        val contents = shelf["contents"]?.safeArray() ?: return null

        val albums = mutableListOf<ArtistAlbum>()
        val videos = mutableListOf<Song>()
        val artists = mutableListOf<Artist>()
        for (item in contents) {
            val obj = item.safeObject() ?: continue
            obj.toArtistAlbum()?.let { albums.add(it); continue }
            obj.toRelatedArtist()?.let { artists.add(it); continue }
            obj.toArtistVideo()?.let { videos.add(it) }
        }
        return ArtistSection(
            title = title,
            albums = albums.distinctBy { it.browseId },
            videos = videos.distinctBy { it.youtubeId },
            artists = artists.distinctBy { it.channelUrl },
        )
    }

    private fun JsonObject.toArtistVideo(): Song? {
        val renderer = this["musicTwoRowItemRenderer"]?.safeObject() ?: return null
        val videoId = renderer["navigationEndpoint"]?.safeObject()
            ?.get("watchEndpoint")?.safeObject()
            ?.get("videoId")?.jsonPrimitive?.contentOrNull ?: return null
        val title = renderer["title"]?.safeObject()
            ?.get("runs")?.safeArray()?.firstOrNull()
            ?.safeObject()?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
        val thumbnail = renderer["thumbnailRenderer"]?.safeObject()
            ?.get("musicThumbnailRenderer")?.safeObject()
            ?.get("thumbnail")?.safeObject()
            ?.get("thumbnails")?.safeArray()?.lastOrNull()
            ?.safeObject()?.get("url")?.jsonPrimitive?.contentOrNull ?: ""
        return Song(youtubeId = videoId, title = title, thumbnailHref = thumbnail)
    }

    /**
     * A "Fans might also like" card: same musicTwoRowItemRenderer shape as an album, but its
     * browseEndpoint carries a channel id (UC…) instead of an MPRE album id. These were previously
     * dropped, which silently emptied the shelf and made extractArtistSections filter it out.
     */
    private fun JsonObject.toRelatedArtist(): Artist? {
        val renderer = this["musicTwoRowItemRenderer"]?.safeObject() ?: return null
        val browseId = renderer["navigationEndpoint"]?.safeObject()
            ?.get("browseEndpoint")?.safeObject()
            ?.get("browseId")?.jsonPrimitive?.contentOrNull ?: return null
        if (!browseId.startsWith("UC")) {
            return null
        }
        val name = renderer["title"]?.safeObject()
            ?.get("runs")?.safeArray()?.firstOrNull()
            ?.safeObject()?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
        if (name.isBlank()) {
            return null
        }
        val thumbnail = renderer["thumbnailRenderer"]?.safeObject()
            ?.get("musicThumbnailRenderer")?.safeObject()
            ?.get("thumbnail")?.safeObject()
            ?.get("thumbnails")?.safeArray()?.lastOrNull()
            ?.safeObject()?.get("url")?.jsonPrimitive?.contentOrNull ?: ""
        return Artist(name = name, thumbnailHref = thumbnail, channelUrl = browseId)
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
        return ArtistAlbum(
            browseId = browseId,
            title = title,
            thumbnail = thumbnail,
            subtitle = renderer["subtitle"].runsText(),
        )
    }

    private const val MUSIC_VIDEO_TYPE_ATV = "MUSIC_VIDEO_TYPE_ATV"

    /** A "m:ss" / "h:mm:ss" display duration, as YouTube formats track lengths. */
    private val DURATION_REGEX = Regex("""\d+:\d{2}(:\d{2})?""")

    /**
     * From a watch-next response, returns the audio ("song") version of a music video, or null
     * when the item already is pure audio or has no counterpart. YouTube links both versions of a
     * track through the play panel's `counterpart` entry.
     */
    fun extractAudioCounterpart(jsonString: String): Song? {
        val wrapper = findFirstObject(
            Json.parseToJsonElement(jsonString),
            listOf("playlistPanelVideoWrapperRenderer")
        ) ?: return null

        val primaryType = wrapper["primaryRenderer"]?.safeObject()
            ?.get("playlistPanelVideoRenderer")?.safeObject()
            ?.let { musicVideoType(it) }
        if (primaryType == MUSIC_VIDEO_TYPE_ATV) {
            return null
        }

        val counterpart = wrapper["counterpart"]?.safeArray()?.firstOrNull()
            ?.safeObject()?.get("counterpartRenderer")?.safeObject()
            ?.get("playlistPanelVideoRenderer")?.safeObject()
            ?: return null

        if (musicVideoType(counterpart) != MUSIC_VIDEO_TYPE_ATV) {
            return null
        }

        return parsePanelVideo(counterpart)
    }

    /**
     * From a watch-next response, returns the music-video ("clip") variant of the playing track:
     * the panel's primary renderer when it already is a video, or its non-ATV `counterpart`
     * when the primary is the official audio. Null when no video variant is linked at all —
     * which is how the player decides whether to offer the Song|Video toggle.
     */
    fun extractVideoCounterpart(jsonString: String): Song? {
        val wrapper = findFirstObject(
            Json.parseToJsonElement(jsonString),
            listOf("playlistPanelVideoWrapperRenderer")
        ) ?: return null

        val primary = wrapper["primaryRenderer"]?.safeObject()
            ?.get("playlistPanelVideoRenderer")?.safeObject()
        val primaryType = primary?.let { musicVideoType(it) }
        if (primaryType != null && primaryType != MUSIC_VIDEO_TYPE_ATV) {
            return parsePanelVideo(primary)?.also { it.isVideo = true }
        }

        val counterpart = wrapper["counterpart"]?.safeArray()?.firstOrNull()
            ?.safeObject()?.get("counterpartRenderer")?.safeObject()
            ?.get("playlistPanelVideoRenderer")?.safeObject()
            ?: return null

        val counterpartType = musicVideoType(counterpart)
        if (counterpartType == null || counterpartType == MUSIC_VIDEO_TYPE_ATV) {
            return null
        }

        return parsePanelVideo(counterpart)?.also { it.isVideo = true }
    }

    private fun musicVideoType(renderer: JsonObject): String? {
        return renderer["navigationEndpoint"]?.safeObject()
            ?.get("watchEndpoint")?.safeObject()
            ?.get("watchEndpointMusicSupportedConfigs")?.safeObject()
            ?.get("watchEndpointMusicConfig")?.safeObject()
            ?.get("musicVideoType")?.jsonPrimitive?.contentOrNull
    }

    private fun parsePanelVideo(renderer: JsonObject): Song? {
        val videoId = renderer["videoId"]?.jsonPrimitive?.contentOrNull ?: return null
        val title = renderer["title"]?.safeObject()
            ?.get("runs")?.safeArray()?.firstOrNull()
            ?.safeObject()?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
        // longBylineText runs: [artist, " • ", album, " • ", …] — the first run is the artist.
        val artist = renderer["longBylineText"]?.safeObject()
            ?.get("runs")?.safeArray()?.firstOrNull()
            ?.safeObject()?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
        val duration = renderer["lengthText"]?.safeObject()
            ?.get("runs")?.safeArray()?.firstOrNull()
            ?.safeObject()?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
        val thumbnail = renderer["thumbnail"]?.safeObject()
            ?.get("thumbnails")?.safeArray()?.lastOrNull()
            ?.safeObject()?.get("url")?.jsonPrimitive?.contentOrNull ?: ""

        return Song(
            youtubeId = videoId,
            title = title,
            artist = artist,
            duration = duration,
            thumbnailHref = thumbnail,
        )
    }

    /**
     * From a watch-next radio response (requested with the seed's own station,
     * `playlistId = "RDAMVM" + videoId`), returns the "up next" queue: every panel video in order.
     * These stay within the seed's station, so autoplay keeps the same register instead of drifting
     * to generic recommendations.
     */
    fun extractRadioSongs(jsonString: String): List<Song> {
        val renderers = mutableListOf<JsonObject>()
        collectObjects(
            Json.parseToJsonElement(jsonString),
            "playlistPanelVideoRenderer",
            renderers
        )
        return renderers.mapNotNull { parsePanelVideo(it) }.distinctBy { it.youtubeId }
    }

    /**
     * From a watch-next response, returns the primary artist's browse id ("UC…") for the
     * currently-playing panel video ([videoId]), falling back to the first panel entry when the
     * exact id is absent (e.g. when the panel lists an audio/video counterpart instead). Null
     * when YouTube exposes no artist page link.
     */
    fun extractArtistBrowseId(jsonString: String, videoId: String): String? {
        val renderers = mutableListOf<JsonObject>()
        collectObjects(
            Json.parseToJsonElement(jsonString),
            "playlistPanelVideoRenderer",
            renderers
        )
        val renderer = renderers.firstOrNull {
            it["videoId"]?.jsonPrimitive?.contentOrNull == videoId
        } ?: renderers.firstOrNull() ?: return null

        // Byline runs: [artist, " • ", album, …] — the artist run links to a "UC…" browse page.
        for (bylineKey in listOf("longBylineText", "shortBylineText")) {
            renderer[bylineKey]?.safeObject()
                ?.get("runs")?.safeArray()
                ?.forEach { run ->
                    val browseId = run.safeObject()
                        ?.get("navigationEndpoint")?.safeObject()
                        ?.get("browseEndpoint")?.safeObject()
                        ?.get("browseId")?.jsonPrimitive?.contentOrNull
                    if (browseId != null && browseId.startsWith("UC")) {
                        return browseId
                    }
                }
        }
        return null
    }

    private fun collectObjects(element: JsonElement, key: String, into: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                element[key]?.safeObject()?.let { into.add(it) }
                element.values.forEach { collectObjects(it, key, into) }
            }

            is JsonArray -> element.forEach { collectObjects(it, key, into) }
            else -> {}
        }
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
    /** Header biography of an artist page. Null when the artist has none. */
    fun extractArtistDescription(jsonString: String): String? {
        val header = Json.parseToJsonElement(jsonString).jsonObject["header"]?.safeObject()
            ?: return null
        for (key in HEADER_RENDERER_KEYS) {
            val renderer = header[key]?.safeObject() ?: continue
            // Immersive headers put the bio in a plain "description" runs block; responsive ones
            // wrap it in a musicDescriptionShelfRenderer.
            val text = renderer["description"]?.let { description ->
                description.safeObject()
                    ?.get("musicDescriptionShelfRenderer")?.safeObject()
                    ?.get("description").runsText()
                    .takeIf { it.isNotBlank() }
                    ?: description.runsText()
            }
            if (!text.isNullOrBlank()) {
                return text
            }
        }
        return null
    }

    /** Human-readable subscriber count ("12M subscribers"), as localized by the API. */
    fun extractArtistSubscriberCount(jsonString: String): String? {
        val header = Json.parseToJsonElement(jsonString).jsonObject["header"]?.safeObject()
            ?: return null
        for (key in HEADER_RENDERER_KEYS) {
            val text = header[key]?.safeObject()
                ?.get("subscriptionButton")?.safeObject()
                ?.get("subscribeButtonRenderer")?.safeObject()
                ?.get("subscriberCountText").runsText()
            if (text.isNotBlank()) {
                return text
            }
        }
        return null
    }

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
        val thumbnails = Json.parseToJsonElement(jsonString).jsonObject["videoDetails"]
            ?.safeObject()?.get("thumbnail")
            ?.safeObject()?.get("thumbnails")
            ?.safeArray()

        // Prefer square art (music tracks) over the 16:9 clip frame; the array is sorted by size,
        // so the last matching entry is the largest.
        val squareUrl = thumbnails
            ?.lastOrNull { thumb ->
                val obj = thumb.safeObject()
                val width = obj?.get("width")?.jsonPrimitive?.intOrNull
                val height = obj?.get("height")?.jsonPrimitive?.intOrNull
                width != null && width == height
            }
            ?.safeObject()?.get("url")
            ?.jsonPrimitive?.contentOrNull

        val url = squareUrl ?: thumbnails?.lastOrNull()
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
            // A row whose watch payload declares a non-ATV musicVideoType (OMV/UGC/…) is a video
            // result: it gets the wide 16:9 thumbnail and, when exposed, a view-count subtitle.
            val videoType = listItemMusicVideoType(songContent)
            it.isVideo = videoType != null && videoType != MUSIC_VIDEO_TYPE_ATV
            if (it.isVideo) {
                it.viewCount = extractViewCount(songContent)
            }
            // Artist-page song rows carry a play count instead; it is what makes "most streamed"
            // visible, so it is read for every row, video or not.
            it.playCount = extractPlayCount(songContent)
        }

    }

    /**
     * The `musicVideoType` of a musicResponsiveListItemRenderer row, read from the title run's
     * watchEndpoint or, failing that, the play-button overlay endpoint. Null when the row does not
     * expose a watch config (e.g. album track rows navigating through playlistItemData).
     */
    private fun listItemMusicVideoType(songContent: JsonObject): String? {
        val titleRuns = songContent["flexColumns"]?.safeArray()?.getOrNull(0)
            ?.safeObject()?.get("musicResponsiveListItemFlexColumnRenderer")?.safeObject()
            ?.get("text")?.safeObject()?.get("runs")?.safeArray()
        titleRuns?.forEach { run ->
            run.safeObject()?.get("navigationEndpoint")?.safeObject()
                ?.get("watchEndpoint")?.safeObject()
                ?.let { watchEndpointMusicVideoType(it) }
                ?.let { return it }
        }

        return songContent["overlay"]?.safeObject()
            ?.get("musicItemThumbnailOverlayRenderer")?.safeObject()
            ?.get("content")?.safeObject()
            ?.get("musicPlayButtonRenderer")?.safeObject()
            ?.get("playNavigationEndpoint")?.safeObject()
            ?.get("watchEndpoint")?.safeObject()
            ?.let { watchEndpointMusicVideoType(it) }
    }

    private fun watchEndpointMusicVideoType(watchEndpoint: JsonObject): String? {
        return watchEndpoint["watchEndpointMusicSupportedConfigs"]?.safeObject()
            ?.get("watchEndpointMusicConfig")?.safeObject()
            ?.get("musicVideoType")?.jsonPrimitive?.contentOrNull
    }

    /**
     * View count of a video row, parsed from the subtitle runs (e.g.
     * "Artist • 1.2M views • 3:45", fr "1,2 M de vues"). The API localizes the wording, so the
     * match is a heuristic: a run starting with a digit and containing a view keyword. Null when
     * the subtitle does not carry one.
     */
    private fun extractViewCount(songContent: JsonObject): String? =
        extractCountLabel(songContent, VIEW_COUNT_KEYWORDS)

    /**
     * Play count of an artist-page song row ("45M plays"), which is how YouTube Music orders an
     * artist's most-streamed tracks. Separate from [extractViewCount] because the wording differs
     * and a play count is not restricted to video rows.
     */
    private fun extractPlayCount(songContent: JsonObject): String? =
        extractCountLabel(songContent, PLAY_COUNT_KEYWORDS)

    /**
     * Finds a subtitle run that reads like "<number> <unit>" — the API never tags these columns, so
     * matching on a leading digit plus a localized keyword is the only handle available. Adding a
     * language means adding its word here.
     */
    private fun extractCountLabel(songContent: JsonObject, keywords: List<String>): String? {
        val flexColumns = songContent["flexColumns"]?.safeArray() ?: return null

        // Column 0 is the title — counts live in the subtitle columns.
        for (column in flexColumns.drop(1)) {
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
                    ?.trim()
                    ?: continue

                val lower = text.lowercase()
                if (text.firstOrNull()?.isDigit() == true && keywords.any { it in lower }) {
                    return text
                }
            }
        }

        return null
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


    /**
     * Session-only cache of resolved muxed (video) streams, keyed by youtubeId. Video URLs are
     * never persisted to the DB (the clip and audio variants share the same id), so this keeps a
     * pre-flight [probeVideoStream] check and the subsequent data-source resolution from paying
     * for two round-trips. In-memory only: cleared with the process.
     */
    private val videoStreamCache = ConcurrentHashMap<String, ResolvedAudioStream>()
    private val audioStreamCache = ConcurrentHashMap<String, ResolvedAudioStream>()

    /**
     * Pre-flight check for video mode: resolves [song]'s stream with the video preference and
     * reports whether an actual muxed (audio+video) stream came back — false when YouTube only
     * exposed audio for this id. The resolved URL is kept in [videoStreamCache] so the playback
     * data source reuses it instead of resolving again. Throws when resolution fails entirely.
     */
    suspend fun probeVideoStream(song: Song): Boolean {
        videoStreamCache[song.youtubeId]?.let { cached ->
            if (isPlaybackUrlFresh(cached.url)) {
                return cached.isVideo
            }
            videoStreamCache.remove(song.youtubeId)
        }

        val resolved = getSongUrlFromYoutube(song, forceVideo = true)
        videoStreamCache[song.youtubeId] = resolved
        return resolved.isVideo
    }

    suspend fun getSongPlayerUrl(
        context: Context,
        song: Song,
        allowLocal: Boolean = false
    ): String {
        val quality = effectiveAudioQuality(context)
        invalidateStreamsIfQualityChanged(context, quality)

        val sessionCache = if (song.forceVideo) videoStreamCache else audioStreamCache
        sessionCache[song.youtubeId]?.let { cached ->
            if (isPlaybackUrlFresh(cached.url)) {
                PlaybackLoudnessStore.update(song.youtubeId, cached.loudnessDb)
                printd("${song.youtubeId} : Got url from session cache")
                return cached.url
            }
            sessionCache.remove(song.youtubeId)
        }

        val localSongRepository = AppDatabase.getInstance(context).songRepository()
        var savedSong: Song? = null
        try {
            savedSong = localSongRepository.getSong(song.youtubeId)
        } catch (ex: Exception) {
            Toast.makeText(context, "Failed to get song from local repository", Toast.LENGTH_LONG)
                .show()
            printe(ex.toString())
        }

        // The clip ("video version") stream and the default official-audio stream share the same
        // youtubeId, so the per-id cache is bypassed in video mode to avoid serving one for the other.
        if (savedSong != null && !song.forceVideo) {
            if (allowLocal && savedSong.audioFilePath != null) {
                PlaybackLoudnessStore.update(song.youtubeId, savedSong.loudnessDb)
                printd("${song.youtubeId} : Was downloaded")
                return savedSong.audioFilePath
            }

            if (savedSong.streamUrl != null) {
                if (isPlaybackUrlFresh(savedSong.streamUrl)) {
                    PlaybackLoudnessStore.update(song.youtubeId, savedSong.loudnessDb)
                    audioStreamCache[song.youtubeId] = ResolvedAudioStream(
                        url = savedSong.streamUrl,
                        loudnessDb = savedSong.loudnessDb,
                    )
                    printd("${song.youtubeId} : Got url from saved")
                    return savedSong.streamUrl
                }
                printd("${song.youtubeId} : Saved url was invalid")
            }
        }

        val resolved = getSongUrlFromYoutube(
            song,
            forceVideo = song.forceVideo,
            quality = quality,
        )
        if (!song.forceVideo) {
            audioStreamCache[song.youtubeId] = resolved
            localSongRepository.setStreamInfo(
                songId = song.youtubeId,
                streamUrl = resolved.url,
                loudnessDb = resolved.loudnessDb,
            )
        } else {
            videoStreamCache[song.youtubeId] = resolved
        }
        PlaybackLoudnessStore.update(song.youtubeId, resolved.loudnessDb)
        printd("${song.youtubeId} : Got url from YouTube and saved song")
        return resolved.url
    }

    private suspend fun effectiveAudioQuality(context: Context): AudioQuality {
        val repository = DatastoreRepository(context.applicationContext)
        val preferred = repository.audioQuality.first()
        if (!repository.dataSaverOnMetered.first()) return preferred
        return if (ConnectivityHelper.isMeteredNetwork(context)) AudioQuality.Low else preferred
    }

    /**
     * Cached signed URLs are quality-specific but do not encode the selected preference in our
     * database. Flush exactly once when the effective setting changes.
     */
    private suspend fun invalidateStreamsIfQualityChanged(
        context: Context,
        quality: AudioQuality,
    ) {
        val repository = DatastoreRepository(context.applicationContext)
        if (repository.getLastStreamQuality() == quality) return

        audioStreamCache.clear()
        videoStreamCache.clear()
        AppDatabase.getInstance(context).songRepository().clearStreamUrls()
        repository.setLastStreamQuality(quality)
        printd("Audio quality changed to $quality: flushed cached stream urls")
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
        retries: Int = Constants.YoutubeApi.RETRY_COUNT,
        forceVideo: Boolean = false,
        quality: AudioQuality = AudioQuality.High,
    ): ResolvedAudioStream {
        var lastError: Throwable? = null

        // Ordinary tracks already point at their official audio, so only video rows need the
        // watch-next counterpart lookup. This removes one full API RTT from almost every tap.
        val playbackSong = if (!forceVideo && song.isVideo) {
            resolvePreferredAudioSong(song)
        } else {
            song
        }

        val fastUrl = resolveAndroidVrStreamUrl(
            playbackSong.youtubeId,
            preferVideo = forceVideo,
            quality = quality,
        )

        if (fastUrl != null) {
            return fastUrl
        }

        printd("${song.youtubeId} : Falling back to NewPipe")
        repeat(retries) { attempt ->
            try {
                return withContext(Dispatchers.IO) {
                    resolveNewPipeStreamUrl(
                        playbackSong,
                        preferVideo = forceVideo,
                        quality = quality,
                    )
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

    /**
     * Returns the song whose stream should actually be played: the ATV ("official audio")
     * counterpart when [song] is a music video that has one, otherwise [song] itself.
     * Any failure while looking up the counterpart silently falls back to the original song.
     */
    private suspend fun resolvePreferredAudioSong(song: Song): Song {
        val counterpart = try {
            extractAudioCounterpart(YoutubeApiClient.next(song.youtubeId))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            printe(
                "${song.youtubeId} : Audio counterpart lookup failed, using original: " +
                        "${e::class.simpleName}: ${e.message ?: "no message"}"
            )
            null
        } ?: return song

        printd("${song.youtubeId} : Playing audio counterpart ${counterpart.youtubeId}")
        return counterpart
    }

    private suspend fun resolveAndroidVrStreamUrl(
        videoId: String,
        retries: Int = Constants.YoutubeApi.RETRY_COUNT,
        preferVideo: Boolean = false,
        quality: AudioQuality = AudioQuality.High,
    ): ResolvedAudioStream? = withContext(Dispatchers.IO) {
        suspend fun executeRequest(): ResolvedAudioStream? {
            val response = YoutubeApiClient.getPlayerInfo(
                videoId = videoId,
                client = Constants.YoutubeApi.Client.ANDROID_VR,
                visitorData = visitorData,
                //   fields = Constants.YoutubeApi.PlayerInfo.Fields.STREAM,
            )

            return extractStreamFromAndroidVrResponse(response, preferVideo, quality)
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

    private fun resolveNewPipeStreamUrl(
        song: Song,
        preferVideo: Boolean = false,
        quality: AudioQuality = AudioQuality.High,
    ): ResolvedAudioStream {
        YoutubeExtractor.ensureInitialized()
        val service = ServiceList.YouTube
        val extractor = service.getStreamExtractor(song.youtubeUrl)

        extractor.fetchPage()

        if (preferVideo) {
            // Muxed (audio+video) streams only: video-only streams would need a second audio URI
            // merged in, which the single-URI resolving data source cannot carry.
            val bestVideoStream = extractor.videoStreams
                .filter { it.content.isNotBlank() && !it.isVideoOnly }
                .maxByOrNull { stream ->
                    stream.resolution.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
                }

            if (bestVideoStream != null) {
                return ResolvedAudioStream(
                    url = bestVideoStream.content,
                    loudnessDb = null,
                    isVideo = true,
                )
            }
        }

        val bestAudioStream = selectAudioStream(
            streams = extractor.audioStreams.filter { it.content.isNotBlank() },
            quality = quality,
        ) { it.averageBitrate }
            ?: error("No valid audio streams found")

        return ResolvedAudioStream(
            url = bestAudioStream.content,
            loudnessDb = null,
        )
    }

    private fun extractStreamFromAndroidVrResponse(
        text: String,
        preferVideo: Boolean = false,
        quality: AudioQuality = AudioQuality.High,
    ): ResolvedAudioStream? {
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

        // Video mode: prefer a muxed (progressive audio+video) entry from `streamingData.formats`
        // — a single URL playable by ProgressiveMediaSource, unlike video-only adaptive formats.
        // When none is exposed, fall through to the audio-only branch below (safe fallback).
        val muxedUrl = if (preferVideo) {
            root["streamingData"]
                ?.safeObject()
                ?.get("formats")
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
                        ?.startsWith("video/", ignoreCase = true) == true
                }
                ?.maxByOrNull {
                    it["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
                }
                ?.get("url")
                ?.jsonPrimitive
                ?.contentOrNull
        } else {
            null
        }

        val directUrl = selectAudioStreamUrl(
            adaptiveFormats = root["streamingData"]
                ?.safeObject()
                ?.get("adaptiveFormats")
                ?.safeArray(),
            quality = quality,
        )

        val loudnessDb = root["playerConfig"]
            ?.safeObject()
            ?.get("audioConfig")
            ?.safeObject()
            ?.get("loudnessDb")
            ?.jsonPrimitive
            ?.doubleOrNull

        if (reason != null) {
            printe("ANDROID_VR Failed ($status). Reason : $reason ")
        }

        if (muxedUrl != null) {
            return ResolvedAudioStream(muxedUrl, loudnessDb, isVideo = true)
        }

        return directUrl?.let { ResolvedAudioStream(it, loudnessDb) }
    }

}

private data class ResolvedAudioStream(
    val url: String,
    val loudnessDb: Double?,
    // True when [url] is a muxed (audio+video) stream, false for an audio-only stream. Lets
    // callers detect that a video request silently fell back to audio.
    val isVideo: Boolean = false,
)


enum class SongInfoType(val index: Int) {
    TITLE(0),
    ARTIST(1),
}
