/*
 * Batching, the line mapping and the timing rule adapted from BitChord's LyricsTranslation
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.core.lyrics

import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.models.LyricLine
import ca.ilianokokoro.umihi.music.models.LyricWord
import ca.ilianokokoro.umihi.music.models.Lyrics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Translates lyrics on demand, keeping every line's timing. Nothing leaves the device until someone
 * asks for a translation, and then only the lyric text does.
 *
 * This goes through a web endpoint of Google's that is not a published API, so it can stop working
 * at any time. It is kept behind [translate] so a provider can replace it in one place.
 */
object LyricsTranslation {
    sealed interface Result {
        data class Translated(val lyrics: Lyrics, val sourceLanguage: String) : Result
        data class SameLanguage(val language: String) : Result
        data object Unavailable : Result
    }

    internal data class Slot(val lineIndex: Int, val text: String)
    internal data class Batch(val slots: List<Slot>, val payload: String)
    internal data class Answer(val texts: List<String>, val sourceLanguage: String)

    private const val ENDPOINT = "https://translate.googleapis.com/translate_a/single"
    private const val MAX_BATCH_CHARS = 3_500
    private const val MAX_PARALLEL_REQUESTS = 2
    private const val MEMORY_ENTRIES = 12

    /** Survives whatever a translation does to the text around it, unlike ordinary punctuation. */
    private val MARKER = Regex("\\uE000[^\\uE001]*\\uE001")

    private val json = Json { ignoreUnknownKeys = true }

    private val client by lazy {
        UmihiHttpClient.client.newBuilder()
            .callTimeout(12, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val memory = object : LinkedHashMap<String, Result>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Result>) = size > MEMORY_ENTRIES
    }

    suspend fun translate(lyrics: Lyrics, targetLanguageTag: String): Result {
        val target = targetLanguageTag.trim()
        val slots = slotsOf(lyrics.lines)
        if (target.isBlank() || slots.isEmpty()) return Result.Unavailable

        val key = "$target:${slots.hashCode()}"
        synchronized(memory) { memory[key] }?.let { return it }

        val answers = coroutineScope {
            // Two short requests at a time keeps a long lyric quick without taking every connection
            // in the pool away from the music.
            batchesOf(slots).chunked(MAX_PARALLEL_REQUESTS).flatMap { group ->
                group.map { batch -> async { request(batch, target) } }.awaitAll()
            }
        }
        val result = combine(lyrics, slots, answers, target)
        // A failure is not remembered: the next tap may well have a connection.
        if (result !is Result.Unavailable) synchronized(memory) { memory[key] = result }
        return result
    }

    internal fun combine(
        lyrics: Lyrics,
        slots: List<Slot>,
        answers: List<Answer?>,
        target: String,
    ): Result {
        if (answers.isEmpty() || answers.any { it == null }) return Result.Unavailable
        val complete = answers.filterNotNull()
        val texts = complete.flatMap { it.texts }
        if (texts.size != slots.size) return Result.Unavailable
        val source = complete
            .groupingBy { canonicalLanguage(it.sourceLanguage) }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
            .orEmpty()
        if (source.isBlank()) return Result.Unavailable
        return if (sameLanguage(source, target)) {
            Result.SameLanguage(source)
        } else {
            Result.Translated(rebuild(lyrics, slots, texts), source)
        }
    }

    /** Instrumental gaps stay in place and are never sent. */
    internal fun slotsOf(lines: List<LyricLine>): List<Slot> =
        lines.mapIndexedNotNull { index, line ->
            line.text.takeIf(String::isNotBlank)?.let { Slot(index, it) }
        }

    internal fun batchesOf(slots: List<Slot>): List<Batch> {
        val result = mutableListOf<Batch>()
        var current = mutableListOf<Slot>()
        var length = 0
        slots.forEach { slot ->
            val added = slot.text.length + if (current.isEmpty()) 0 else MARKER_LENGTH
            if (current.isNotEmpty() && length + added > MAX_BATCH_CHARS) {
                result += Batch(current, payloadOf(current))
                current = mutableListOf()
                length = 0
            }
            current += slot
            length += slot.text.length + if (current.size == 1) 0 else MARKER_LENGTH
        }
        if (current.isNotEmpty()) result += Batch(current, payloadOf(current))
        return result
    }

    private const val MARKER_LENGTH = 8

    private fun payloadOf(slots: List<Slot>): String = buildString {
        slots.forEachIndexed { index, slot ->
            if (index > 0) append('\n').append(markerOf(index)).append('\n')
            append(slot.text)
        }
    }

    private fun markerOf(index: Int) = "${index.toString().padStart(4, '0')}"

    /** Null unless every line came back: a shifted answer would show under the wrong timestamps. */
    internal fun parseAnswer(body: String, expected: Int): Answer? = runCatching {
        val root = json.parseToJsonElement(body).jsonArray
        val translated = root[0].jsonArray.joinToString(separator = "") { segment ->
            segment.jsonArray.getOrNull(0)?.jsonPrimitive?.contentOrNull.orEmpty()
        }
        val parts = translated.split(MARKER).map(String::trim)
        if (parts.size != expected || parts.any(String::isBlank)) return@runCatching null
        Answer(parts, root.getOrNull(2)?.jsonPrimitive?.contentOrNull.orEmpty())
    }.getOrNull()

    /**
     * Word order and word count change between languages, so a line's words cannot keep their own
     * timings. The whole translated line takes the span the original was sung over, and the sweep
     * reads as a guide to the pace rather than an alignment.
     */
    internal fun rebuild(lyrics: Lyrics, slots: List<Slot>, texts: List<String>): Lyrics {
        val byLine = slots.zip(texts).associate { (slot, text) -> slot.lineIndex to text }
        return lyrics.copy(
            lines = lyrics.lines.mapIndexed { index, line ->
                val text = byLine[index] ?: return@mapIndexed line
                line.copy(
                    text = text,
                    words = if (line.words.isEmpty()) {
                        emptyList()
                    } else {
                        listOf(LyricWord(line.words.first().startMs, line.words.last().endMs, text))
                    },
                )
            }
        )
    }

    internal fun canonicalLanguage(tag: String): String {
        val language = Locale.forLanguageTag(tag.replace('_', '-')).language.lowercase(Locale.ROOT)
        return when (language) {
            "iw" -> "he"
            "in" -> "id"
            "ji" -> "yi"
            else -> language
        }
    }

    internal fun sameLanguage(first: String, second: String): Boolean =
        canonicalLanguage(first) == canonicalLanguage(second)

    private suspend fun request(batch: Batch, target: String): Answer? = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client", "dict-chrome-ex")
            .add("sl", "auto")
            .add("tl", target)
            .add("dt", "t")
            .add("q", batch.payload)
            .build()
        val request = Request.Builder()
            .url(ENDPOINT)
            .header("User-Agent", "Jelyvo")
            .header("Accept", "application/json")
            .post(body)
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()?.let { parseAnswer(it, batch.slots.size) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            null
        }
    }
}
