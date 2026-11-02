/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 * Reduced to word-level timing and adapted to Jelyvo's lyrics model.
 */
package ca.ilianokokoro.umihi.music.core.lyrics

import ca.ilianokokoro.umihi.music.models.LyricLine
import ca.ilianokokoro.umihi.music.models.LyricWord
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Word-timed TTML: one `<p>` per sung line, holding one timed `<span>` per syllable. Syllables of
 * a word are adjacent spans with no whitespace between them, so whitespace, not the span boundary,
 * is what separates words.
 */
object TtmlLyrics {
    /** Alternate renderings of the same words, or a second voice: none belong in the main line. */
    private val SKIPPED_ROLES = setOf("x-translation", "x-roman", "x-bg")

    fun parse(ttml: String): List<LyricLine> = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // Documents come from third-party hosts, so nothing they reference may be fetched.
            // Each feature is optional: Android's parser throws on names it does not know, and one
            // rejected feature must not abort the parse.
            harden("http://apache.org/xml/features/disallow-doctype-decl")
            harden("http://xml.org/sax/features/external-general-entities", false)
            harden("http://xml.org/sax/features/external-parameter-entities", false)
            harden(XMLConstants.FEATURE_SECURE_PROCESSING)
            runCatching { isExpandEntityReferences = false }
        }
        val paragraphs = factory.newDocumentBuilder()
            .parse(InputSource(StringReader(ttml)))
            .getElementsByTagName("p")
        (0 until paragraphs.length)
            .mapNotNull { index -> (paragraphs.item(index) as? Element)?.let(::lineFrom) }
            .sortedBy { it.timeMs }
    }.getOrDefault(emptyList())

    private fun DocumentBuilderFactory.harden(feature: String, value: Boolean = true) {
        runCatching { setFeature(feature, value) }
    }

    /**
     * A prefixed attribute, read the same way on both DOM implementations: the JVM keeps
     * `ttm:role` whole, while Android files it under its local name.
     */
    private fun Element.qualified(name: String): String {
        getAttribute(name).takeIf { it.isNotEmpty() }?.let { return it }
        val local = name.substringAfter(':')
        val found = attributes ?: return ""
        for (index in 0 until found.length) {
            val attribute = found.item(index) ?: continue
            if (attribute.nodeName == name || attribute.nodeName == local ||
                attribute.localName == local
            ) {
                return attribute.nodeValue.orEmpty()
            }
        }
        return ""
    }

    private fun lineFrom(paragraph: Element): LyricLine? {
        val pieces = mutableListOf<Piece>()
        collect(paragraph, pieces)
        val words = mergeIntoWords(pieces)
        val begin = time(paragraph.getAttribute("begin"))

        if (words.isEmpty()) {
            // Line-synced TTML: a stamped paragraph with bare text and no spans.
            val text = paragraph.textContent?.trim().orEmpty()
            if (text.isEmpty() || begin == null) return null
            return LyricLine(timeMs = begin, text = text)
        }
        return LyricLine(
            // The paragraph's stamp can sit a hair before the first word: keep that lead-in.
            timeMs = minOf(begin ?: words.first().startMs, words.first().startMs),
            text = words.joinToString(" ") { it.text },
            words = words,
        )
    }

    /** Flattens a paragraph into its innermost timed spans and the whitespace between them. */
    private fun collect(node: Node, out: MutableList<Piece>) {
        val children = node.childNodes
        for (index in 0 until children.length) {
            when (val child = children.item(index)) {
                is Element -> {
                    if (child.qualified("ttm:role") in SKIPPED_ROLES) continue
                    val begin = time(child.getAttribute("begin"))
                    val end = time(child.getAttribute("end"))
                    if (begin != null && end != null && !hasTimedChild(child)) {
                        out += Piece.Timed(child.textContent.orEmpty(), begin, end)
                    } else {
                        collect(child, out)
                    }
                }

                else -> if (child.nodeType == Node.TEXT_NODE) {
                    child.textContent?.takeIf { it.isNotEmpty() }?.let { out += Piece.Text(it) }
                }
            }
        }
    }

    private fun hasTimedChild(element: Element): Boolean {
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.getAttribute("begin").isNotEmpty() || hasTimedChild(child)) return true
        }
        return false
    }

    /** Glues syllables into words: a word runs from its first syllable to its last. */
    private fun mergeIntoWords(pieces: List<Piece>): List<LyricWord> {
        val words = mutableListOf<LyricWord>()
        val current = StringBuilder()
        var start = 0L
        var end = 0L
        var timed = false

        fun flush() {
            val text = current.toString().trim()
            current.setLength(0)
            if (text.isNotEmpty() && timed) words += LyricWord(start, end, text)
            timed = false
        }

        pieces.forEach { piece ->
            when (piece) {
                is Piece.Text -> when {
                    piece.text.isBlank() -> flush()
                    // Trailing punctuation joins the word before it; text with no word before it
                    // has no timing to borrow.
                    timed -> current.append(piece.text)
                    else -> Unit
                }

                is Piece.Timed -> {
                    if (piece.text.isBlank()) return@forEach
                    if (piece.text.first().isWhitespace()) flush()
                    if (current.isEmpty()) start = piece.start
                    current.append(piece.text.trim())
                    end = piece.end
                    timed = true
                    if (piece.text.last().isWhitespace()) flush()
                }
            }
        }
        flush()
        return words
    }

    /** TTML clock values: `27.395`, `1:05.20`, `1:02:03.4`, `27s` or `27395ms`, in milliseconds. */
    fun time(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.endsWith("ms")) return raw.dropLast(2).toDoubleOrNull()?.toLong()
        val parts = raw.removeSuffix("s").split(':').map { it.toDoubleOrNull() ?: return null }
        val seconds = when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> return null
        }
        return Math.round(seconds * 1000)
    }

    private sealed interface Piece {
        data class Text(val text: String) : Piece
        data class Timed(val text: String, val start: Long, val end: Long) : Piece
    }
}
