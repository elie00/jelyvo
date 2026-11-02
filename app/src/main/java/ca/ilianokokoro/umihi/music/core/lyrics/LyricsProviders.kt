/*
 * Search cleaning, reply reading and lrc.red matching adapted from BitChord
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.core.lyrics

import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.data.datasources.LyricsDataSource
import ca.ilianokokoro.umihi.music.models.LyricLine
import ca.ilianokokoro.umihi.music.models.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** What a lyrics database is asked for. */
data class LyricsQuery(
    val title: String,
    val artist: String,
    val durationSeconds: Int?,
    val album: String? = null,
)

/** One lyrics database. Null means it has nothing usable, for whatever reason. */
fun interface LyricsProvider {
    suspend fun fetch(query: LyricsQuery): Lyrics?
}

private val CREDITS = listOf(
    Regex("""\s*[(\[]\s*(feat|ft|featuring|with)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE),
    Regex("""\s+(feat|ft|featuring)\.?\s+.*$""", RegexOption.IGNORE_CASE),
    Regex(
        """\s*[(\[]\s*(official\s*)?(music\s*)?""" +
            """(video|audio|visuali[sz]er|lyrics?\s*video|lyrics?|m/?v|hd|hq|4k|full\s*song)""" +
            """\s*[)\]]""",
        RegexOption.IGNORE_CASE,
    ),
    Regex("""\s*[(\[]\s*official\s*[)\]]""", RegexOption.IGNORE_CASE),
)
private val WHITESPACE = Regex("""\s+""")

/**
 * A YouTube title as a lyrics database would have indexed it. Only credits and packaging come off:
 * "(Remix)" or "(Live)" name a different recording, and stripping them would return the wrong
 * words instead of no words.
 */
fun String.forLyricsSearch(): String {
    var name = this
    CREDITS.forEach { pattern -> name = pattern.replace(name, " ") }
    return name.replace(WHITESPACE, " ").trim().trimEnd(',', '-', '–', '—').trim().ifBlank { trim() }
}

/** Drops the " - Topic" of YouTube's auto-generated artist channels. */
fun String.artistForLyricsSearch(): String = removeSuffix(" - Topic").trim().ifBlank { trim() }

private val lyricsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

// A lyric that arrives after the second chorus is useless, and the other sources are the fallback.
private val lyricsClient by lazy {
    UmihiHttpClient.client.newBuilder()
        .callTimeout(6, TimeUnit.SECONDS)
        .connectTimeout(3, TimeUnit.SECONDS)
        .build()
}

private suspend fun lyricsGet(url: String): String? = runInterruptible(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "Jelyvo (https://github.com/elie00/umihi-music)")
        .header("Accept", "application/json")
        .build()
    runCatching {
        lyricsClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()
}

private fun linesToLyrics(lines: List<LyricLine>): Lyrics? =
    lines.takeIf { found -> found.any { it.text.isNotBlank() } }
        ?.let { Lyrics(lines = it, isSynced = it.any { line -> line.timeMs != null }) }

/** Reads a lyric out of the JSON envelopes, TTML, enhanced LRC or LRC a host may answer with. */
internal object ProviderReply {
    private val CONTENT_KEYS = listOf(
        "ttml", "ttmlContent", "lyrics", "lrc", "content", "text",
        "plainLyrics", "syncedLyrics", "lyric", "data", "result", "response",
    )

    fun parse(raw: String): Lyrics? {
        val content = unescapeTtml(unwrap(raw) ?: return null)
        val isTtml = content.contains(Regex("""<tt(?:\s|>)""", RegexOption.IGNORE_CASE))
        return linesToLyrics(
            when {
                isTtml -> TtmlLyrics.parse(content)
                content.trimStart().startsWith("<") -> emptyList()
                // Untimed text from these hosts may just be an error message, so it does not count.
                else -> EnhancedLrc.parse(content).ifEmpty {
                    Lyrics.parseLrc(content).filter { it.timeMs != null }
                }
            }
        )
    }

    fun unwrap(raw: String): String? {
        val value = raw.replace("\uFEFF", "").trim()
        if (value.isBlank()) return null
        val json = runCatching { lyricsJson.parseToJsonElement(value) }.getOrNull() ?: return value
        return extract(json)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extract(element: JsonElement): String? = when (element) {
        JsonNull -> null

        is JsonPrimitive -> if (element.isString) {
            val text = element.content.trim()
            val nested = runCatching { lyricsJson.parseToJsonElement(text) }.getOrNull()
            if (nested != null && nested !is JsonPrimitive) extract(nested) else text
        } else {
            null
        }

        is JsonArray -> element.mapNotNull(::extract).joinToString("\n").takeIf { it.isNotBlank() }

        is JsonObject -> {
            val failed = element["isError"]?.toString() == "true" ||
                element["ok"]?.toString() == "false" ||
                element["error"]?.let { it !is JsonNull && it.toString() !in setOf("false", "\"\"") } == true
            if (failed) {
                null
            } else {
                CONTENT_KEYS.asSequence().mapNotNull { key -> element[key]?.let(::extract) }.firstOrNull()
            }
        }
    }

    private fun unescapeTtml(value: String): String =
        if (value.contains("&lt;tt", ignoreCase = true)) {
            value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
        } else {
            value
        }
}

/** Apple-style TTML keyed on title, artist and duration, from the BetterLyrics backend. */
object BetterLyricsProvider : LyricsProvider {
    private const val BASE = "https://lyrics-api.boidu.dev/getLyrics"

    override suspend fun fetch(query: LyricsQuery): Lyrics? {
        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("s", query.title)
            .addQueryParameter("a", query.artist)
            .apply {
                query.durationSeconds?.takeIf { it > 0 }
                    ?.let { addQueryParameter("d", it.toString()) }
                query.album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("al", it) }
            }
            .build()
        return lyricsGet(url.toString())?.let(ProviderReply::parse)
    }
}

/** Community-submitted lyrics, as TTML, LRC or plain text. */
object UnisonProvider : LyricsProvider {
    private const val BASE = "https://unison.boidu.dev/lyrics"

    @Serializable
    data class Response(val success: Boolean? = null, val data: Entry? = null)

    @Serializable
    data class Entry(
        val lyrics: String? = null,
        val format: String? = null,
        val syncType: String? = null,
    )

    override suspend fun fetch(query: LyricsQuery): Lyrics? {
        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("song", query.title)
            .addQueryParameter("artist", query.artist)
            .apply {
                query.album?.takeIf { it.isNotBlank() }?.let { addQueryParameter("album", it) }
                query.durationSeconds?.takeIf { it > 0 }
                    ?.let { addQueryParameter("duration", it.toString()) }
            }
            .build()
        val body = lyricsGet(url.toString()) ?: return null
        val response = runCatching { lyricsJson.decodeFromString<Response>(body) }.getOrNull()
        return response?.takeIf { it.success == true }?.data?.let(::lyricsOf)
    }

    internal fun lyricsOf(entry: Entry): Lyrics? {
        val text = entry.lyrics?.takeIf { it.isNotBlank() } ?: return null
        return linesToLyrics(
            when {
                entry.format.equals("ttml", ignoreCase = true) -> TtmlLyrics.parse(text)
                entry.syncType.equals("plain", ignoreCase = true) -> Lyrics.parsePlain(text)
                else -> EnhancedLrc.parse(text).ifEmpty { Lyrics.parseLrc(text) }
            }
        )
    }
}

/** Per-syllable TTML filed by recording code, found through a free-text search. */
object LrcRedProvider : LyricsProvider {
    private const val BASE = "https://lrc.red/"
    private const val DURATION_TOLERANCE_SECONDS = 3.0
    private val ISRC = Regex("""[A-Z]{2}[A-Z0-9]{3}\d{7}""")
    private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")
    private val COMBINING_MARKS = Regex("""\p{Mn}+""")
    private val ARTIST_SEPARATORS = Regex(
        """\s*(?:,|&|;|/|\s+and\s+|\s+x\s+|\s+with\s+|\s+feat\.?\s+|\s+ft\.?\s+)\s*""",
        RegexOption.IGNORE_CASE,
    )

    /** Words that make it a different recording rather than a different label. */
    private val VERSION_WORDS = setOf(
        "live", "remix", "remixed", "mix", "acoustic", "unplugged", "instrumental",
        "karaoke", "cappella", "acapella", "demo", "edit", "version", "cover",
        "sped", "slowed", "reverb", "nightcore", "lofi", "orchestral", "extended",
    )

    @Serializable
    data class Response(val hits: List<Hit>? = null)

    @Serializable
    data class Hit(
        val isrc: String? = null,
        val title: String? = null,
        val artist: String? = null,
        val duration: Double? = null,
    )

    override suspend fun fetch(query: LyricsQuery): Lyrics? {
        val searchUrl = "${BASE}search.json".toHttpUrl().newBuilder()
            .addQueryParameter("q", "${query.title} ${query.artist}".trim())
            .build()
        val body = lyricsGet(searchUrl.toString()) ?: return null
        val hits = runCatching { lyricsJson.decodeFromString<Response>(body) }.getOrNull()?.hits
            ?: return null
        val hit = best(hits, query.title, query.artist, query.durationSeconds) ?: return null
        val document = lyricsGet(documentUrl(hit.isrc ?: return null) ?: return null) ?: return null
        return linesToLyrics(TtmlLyrics.parse(document))
    }

    /** Only a checked code ever reaches the URL path. */
    internal fun documentUrl(isrc: String): String? =
        isrc.trim().uppercase(Locale.ROOT).takeIf { ISRC.matches(it) }?.let { "${BASE}s/$it.ttml" }

    /**
     * The hit that is surely this recording, or null. A miss lets the next source answer, while
     * the wrong words scrolling over the right song cannot be recovered from, so every test is a
     * requirement rather than a score. Of those that pass, the closest in length wins.
     */
    internal fun best(hits: List<Hit>, title: String, artist: String, seconds: Int?): Hit? {
        val wantedTitle = coreOf(title)
        val wantedVersion = versionOf(title)
        val wantedArtists = artistsOf(artist)

        fun distance(hit: Hit): Double =
            if (seconds != null && seconds > 0 && hit.duration != null) {
                abs(hit.duration - seconds)
            } else {
                0.0
            }

        return hits.filter { hit ->
            val name = hit.title ?: return@filter false
            hit.isrc?.let(::documentUrl) != null &&
                coreOf(name) == wantedTitle &&
                versionOf(name) == wantedVersion &&
                (wantedArtists.isEmpty() ||
                    artistsOf(hit.artist.orEmpty()).any { it in wantedArtists }) &&
                distance(hit) <= DURATION_TOLERANCE_SECONDS
        }.minByOrNull(::distance)
    }

    private fun coreOf(title: String): String =
        normalized(title.replace(BRACKETED, " ").substringBefore(" - ")).ifEmpty { normalized(title) }

    private fun versionOf(title: String): Set<String> {
        val extras = BRACKETED.findAll(title).joinToString(" ") { it.value } +
            " " + title.substringAfter(" - ", "")
        return normalized(extras).split(' ').filter { it in VERSION_WORDS }.toSet()
    }

    private fun artistsOf(artist: String): Set<String> =
        artist.split(ARTIST_SEPARATORS).map(::normalized).filter { it.isNotEmpty() }.toSet()

    private fun normalized(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .replace(WHITESPACE, " ")
            .trim()
}

/** LRCLIB, Jelyvo's original source: line timing at best, but free and dependable. */
class LrcLibProvider(private val source: LyricsDataSource = LyricsDataSource()) : LyricsProvider {
    override suspend fun fetch(query: LyricsQuery): Lyrics? =
        runCatching { source.getLyrics(query.title, query.artist, query.durationSeconds) }
            .getOrNull()
}
