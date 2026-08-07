package ca.ilianokokoro.umihi.music.core.youtube

import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper.safeArray
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper.safeObject
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.SpellCorrection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Owns the complete JSON grammar for search-like YouTube Music responses.
 *
 * Song decoding stays injected because playlist, album, artist and search rows share that richer
 * domain conversion. Everything specific to search response traversal and entity classification is
 * kept local here, behind a small stable interface.
 */
internal class SearchResponseParser(
    private val songExtractor: (JsonObject) -> Song?,
    private val thumbnailExtractor: (JsonElement) -> String,
    private val viewCountExtractor: (JsonObject) -> String?,
) {

    fun extractItems(jsonString: String, filter: SearchFilter): List<SearchItem> {
        val root = Json.parseToJsonElement(jsonString)
        return when (filter) {
            SearchFilter.Songs, SearchFilter.Videos -> {
                collectResponsiveListItems(root).mapNotNull { item ->
                    songExtractor(item)?.also { song ->
                        // Scoped video rows do not always expose a musicVideoType.
                        if (filter == SearchFilter.Videos && !song.isVideo) {
                            song.isVideo = true
                            song.viewCount = item.renderer()?.let(viewCountExtractor)
                        }
                    }?.let(SearchItem::Track)
                }
            }

            SearchFilter.Albums, SearchFilter.Artists -> {
                val items = collectResponsiveListItems(root).mapNotNull(::mixedSearchItem)
                when (filter) {
                    SearchFilter.Albums -> items.filterIsInstance<SearchItem.Album>()
                    else -> items.filterIsInstance<SearchItem.Artist>()
                }
            }

            SearchFilter.All -> {
                collectResponsiveListItems(root).mapNotNull(::mixedSearchItem) +
                    collectTwoRowItems(root).mapNotNull(::twoRowSearchItem)
            }

            // The library search tab is resolved locally and never reaches this parser.
            SearchFilter.Library -> emptyList()
        }.distinctBy(SearchItem::id)
    }

    fun extractLibraryAlbums(jsonString: String): List<SearchItem.Album> =
        collectTwoRowItems(Json.parseToJsonElement(jsonString))
            .mapNotNull { searchItem(it, SearchFilter.Albums) as? SearchItem.Album }
            .distinctBy(SearchItem.Album::id)

    fun extractLibraryArtists(jsonString: String): List<SearchItem.Artist> {
        val root = Json.parseToJsonElement(jsonString)
        return (
            collectResponsiveListItems(root)
                .mapNotNull { mixedSearchItem(it) as? SearchItem.Artist } +
                collectTwoRowItems(root)
                    .mapNotNull { searchItem(it, SearchFilter.Artists) as? SearchItem.Artist }
            ).distinctBy(SearchItem.Artist::id)
    }

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

    fun extractSuggestions(jsonString: String): List<String> {
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
        return suggestions.distinct().take(MAX_SUGGESTIONS)
    }

    fun extractTopResult(jsonString: String): SearchItem? =
        collectCardShelves(Json.parseToJsonElement(jsonString))
            .firstNotNullOfOrNull(::cardSearchItem)

    private fun cardSearchItem(card: JsonObject): SearchItem? {
        val titleRun = card["title"]?.safeObject()?.get("runs")?.safeArray()
            ?.firstOrNull()?.safeObject()
        val endpoint = titleRun?.get("navigationEndpoint")?.safeObject()
        val browseId = endpoint?.get("browseEndpoint")?.safeObject()
            ?.get("browseId")?.jsonPrimitive?.contentOrNull
        val title = card["title"].runsText()
        val thumbnail = card["thumbnail"]?.let(thumbnailExtractor).orEmpty()
        return when {
            browseId?.startsWith("UC") == true -> SearchItem.Artist(
                id = browseId,
                name = title,
                thumbnail = thumbnail,
            )

            browseId?.startsWith("MPRE") == true -> SearchItem.Album(
                id = browseId,
                title = title,
                subtitle = card["subtitle"].runsText(),
                thumbnail = thumbnail,
            )

            else -> cardTrack(card, titleRun, title, thumbnail)?.let(SearchItem::Track)
        }
    }

    private fun cardTrack(
        card: JsonObject,
        titleRun: JsonObject?,
        title: String,
        thumbnail: String,
    ): Song? {
        val videoId = titleRun?.get("navigationEndpoint")?.safeObject()
            ?.get("watchEndpoint")?.safeObject()
            ?.get("videoId")?.jsonPrimitive?.contentOrNull ?: return null

        val subtitleParts = card["subtitle"]?.safeObject()
            ?.get("runs")?.safeArray()
            ?.mapNotNull { it.safeObject()?.get("text")?.jsonPrimitive?.contentOrNull?.trim() }
            ?.filter { it.isNotEmpty() && it != RUN_SEPARATOR }
            .orEmpty()
        val artist = subtitleParts.getOrNull(1).orEmpty()
        val duration = subtitleParts.lastOrNull { it.matches(DURATION_REGEX) }.orEmpty()
        val isExplicit = card["subtitleBadges"]?.safeArray()?.any { badge ->
            badge.safeObject()
                ?.get("musicInlineBadgeRenderer")?.safeObject()
                ?.get("icon")?.safeObject()
                ?.get("iconType")?.jsonPrimitive?.contentOrNull == "MUSIC_EXPLICIT_BADGE"
        } ?: false

        return Song(
            youtubeId = videoId,
            title = title,
            artist = artist,
            duration = duration,
            thumbnailHref = thumbnail,
            isExplicit = isExplicit,
        ).also { song ->
            val videoType = musicVideoType(titleRun)
            song.isVideo = videoType != null && videoType != MUSIC_VIDEO_TYPE_ATV
            if (song.isVideo) {
                song.viewCount = subtitleParts.drop(2)
                    .firstOrNull { !it.matches(DURATION_REGEX) }
            }
        }
    }

    private fun mixedSearchItem(wrapper: JsonObject): SearchItem? {
        val renderer = wrapper.renderer() ?: return null
        val browseId = renderer["navigationEndpoint"]?.safeObject()
            ?.get("browseEndpoint")?.safeObject()
            ?.get("browseId")?.jsonPrimitive?.contentOrNull
        val thumbnail = renderer["thumbnail"]?.let(thumbnailExtractor).orEmpty()
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

            else -> songExtractor(wrapper)?.let(SearchItem::Track)
        }
    }

    private fun searchItem(renderer: JsonObject, filter: SearchFilter): SearchItem? {
        val endpoint = renderer["navigationEndpoint"]?.safeObject()
            ?: renderer["title"]?.safeObject()?.get("runs")?.safeArray()
                ?.firstOrNull()?.safeObject()?.get("navigationEndpoint")?.safeObject()
            ?: return null
        val browseId = endpoint["browseEndpoint"]?.safeObject()
            ?.get("browseId")?.jsonPrimitive?.contentOrNull ?: return null
        val title = renderer["title"].runsText()
        val thumbnail = (renderer["thumbnailRenderer"] ?: renderer["thumbnail"])
            ?.let(thumbnailExtractor).orEmpty()
        return when (filter) {
            SearchFilter.Albums -> browseId.takeIf { it.startsWith("MPRE") }?.let {
                SearchItem.Album(
                    id = it,
                    title = title,
                    subtitle = renderer["subtitle"].runsText(),
                    thumbnail = thumbnail,
                )
            }

            SearchFilter.Artists -> browseId.takeIf { it.startsWith("UC") }?.let {
                SearchItem.Artist(id = it, name = title, thumbnail = thumbnail)
            }

            SearchFilter.Songs, SearchFilter.Videos, SearchFilter.All,
            SearchFilter.Library -> null
        }
    }

    private fun twoRowSearchItem(wrapper: JsonObject): SearchItem? {
        val renderer = wrapper["musicTwoRowItemRenderer"]?.safeObject() ?: wrapper
        val endpoint = renderer["navigationEndpoint"]?.safeObject()
            ?: renderer["title"]?.safeObject()?.get("runs")?.safeArray()
                ?.firstOrNull()?.safeObject()?.get("navigationEndpoint")?.safeObject()
            ?: return null
        val browseId = endpoint["browseEndpoint"]?.safeObject()
            ?.get("browseId")?.jsonPrimitive?.contentOrNull ?: return null
        val title = renderer["title"].runsText()
        val thumbnail = (renderer["thumbnailRenderer"] ?: renderer["thumbnail"])
            ?.let(thumbnailExtractor).orEmpty()
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

    /**
     * YouTube's own search rows. Their grammar differs from YouTube Music's: plain `videoRenderer`
     * objects carrying `videoId`, `title.runs`, `ownerText.runs`, a duration overlay and a view
     * count, instead of `musicResponsiveListItemRenderer` column flexes.
     */
    fun extractYoutubeVideos(jsonString: String): List<SearchItem> {
        val root = Json.parseToJsonElement(jsonString)
        return collectVideoRenderers(root).mapNotNull { renderer ->
            val videoId = renderer["videoId"]?.jsonPrimitive?.contentOrNull
                ?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            val title = renderer["title"].runsText().takeIf(String::isNotBlank)
                ?: renderer["title"]?.safeObject()?.get("simpleText")
                    ?.jsonPrimitive?.contentOrNull.orEmpty()
            if (title.isBlank()) return@mapNotNull null

            val duration = renderer["lengthText"]?.safeObject()?.get("simpleText")
                ?.jsonPrimitive?.contentOrNull.orEmpty()
            val views = renderer["shortViewCountText"]?.safeObject()?.get("simpleText")
                ?.jsonPrimitive?.contentOrNull
                ?: renderer["viewCountText"]?.safeObject()?.get("simpleText")
                    ?.jsonPrimitive?.contentOrNull

            SearchItem.Track(
                Song(
                    youtubeId = videoId,
                    title = title,
                    artist = renderer["ownerText"].runsText(),
                    duration = duration,
                    // YouTube nests its art under thumbnail.thumbnails[], not the music
                    // grammar the shared extractor knows; take the widest entry.
                    thumbnailHref = renderer["thumbnail"]?.safeObject()
                        ?.get("thumbnails")?.safeArray()
                        ?.lastOrNull()?.safeObject()
                        ?.get("url")?.jsonPrimitive?.contentOrNull.orEmpty(),
                ).also { song ->
                    song.isVideo = true
                    song.viewCount = views
                    // The owning channel, so a video row can reach its channel the way a music
                    // row reaches its artist. Same "UC…" shape the subscribe endpoints take.
                    song.artistId = renderer["ownerText"]?.safeObject()
                        ?.get("runs")?.safeArray()
                        ?.firstOrNull()?.safeObject()
                        ?.get("navigationEndpoint")?.safeObject()
                        ?.get("browseEndpoint")?.safeObject()
                        ?.get("browseId")?.jsonPrimitive?.contentOrNull
                        ?.takeIf(String::isNotBlank)
                }
            )
        }
    }

    private fun collectVideoRenderers(root: JsonElement): List<JsonObject> =
        buildList { collectVideoRenderers(root, this) }

    private fun collectVideoRenderers(element: JsonElement, output: MutableList<JsonObject>) {
        when (element) {
            is JsonObject -> {
                element["videoRenderer"]?.safeObject()?.let { output += it }
                element.values.forEach { collectVideoRenderers(it, output) }
            }

            is JsonArray -> element.forEach { collectVideoRenderers(it, output) }
            else -> Unit
        }
    }

    private fun collectResponsiveListItems(root: JsonElement): List<JsonObject> =
        buildList { collectResponsiveListItems(root, this) }

    private fun collectResponsiveListItems(
        element: JsonElement,
        output: MutableList<JsonObject>,
    ) {
        when (element) {
            is JsonObject -> {
                if ("musicResponsiveListItemRenderer" in element) output += element
                element.values.forEach { collectResponsiveListItems(it, output) }
            }

            is JsonArray -> element.forEach { collectResponsiveListItems(it, output) }
            else -> Unit
        }
    }

    private fun collectTwoRowItems(root: JsonElement): List<JsonObject> =
        buildList { collectTwoRowItems(root, this) }

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

    private fun collectCardShelves(root: JsonElement): List<JsonObject> =
        buildList { collectCardShelves(root, this) }

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

    private fun JsonObject.renderer(): JsonObject? =
        this["musicResponsiveListItemRenderer"]?.safeObject()

    private fun flexColumnText(renderer: JsonObject, index: Int): String =
        renderer["flexColumns"]?.safeArray()?.getOrNull(index)
            ?.safeObject()?.get("musicResponsiveListItemFlexColumnRenderer")
            ?.safeObject()?.get("text").runsText()

    private fun musicVideoType(titleRun: JsonObject): String? =
        titleRun["navigationEndpoint"]?.safeObject()
            ?.get("watchEndpoint")?.safeObject()
            ?.get("watchEndpointMusicSupportedConfigs")?.safeObject()
            ?.get("watchEndpointMusicConfig")?.safeObject()
            ?.get("musicVideoType")?.jsonPrimitive?.contentOrNull

    private fun JsonElement?.runsText(): String = this?.safeObject()
        ?.get("runs")?.safeArray()
        ?.joinToString(separator = "") { run ->
            run.safeObject()?.get("text")?.jsonPrimitive?.contentOrNull.orEmpty()
        }.orEmpty()

    companion object {
        private const val MAX_SUGGESTIONS = 8
        private const val MUSIC_VIDEO_TYPE_ATV = "MUSIC_VIDEO_TYPE_ATV"
        private const val RUN_SEPARATOR = "•"
        private val DURATION_REGEX = Regex("""\d+:\d{2}(:\d{2})?""")
    }
}
