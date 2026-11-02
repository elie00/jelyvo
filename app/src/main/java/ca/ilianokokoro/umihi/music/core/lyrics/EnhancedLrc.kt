/*
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.core.lyrics

import ca.ilianokokoro.umihi.music.models.LyricLine
import ca.ilianokokoro.umihi.music.models.LyricWord

/**
 * Enhanced LRC, a normal LRC line with a stamp in front of each word:
 * `[00:27.39]<00:27.39>I <00:27.54>been <00:27.74>tryna`.
 * Only starts are written down, so a word runs until the next one begins.
 */
object EnhancedLrc {
    private const val LAST_WORD_TAIL_MS = 1_000L
    private val LINE = Regex("""^\[(\d{1,3}):(\d{2})[.:](\d{2,3})](.*)$""")
    private val WORD = Regex("""<(\d{1,3}):(\d{2})[.:](\d{2,3})>([^<]*)""")

    private class Row(val timeMs: Long, val stamps: List<MatchResult>, val plain: String)

    /** Empty when [lrc] carries no word stamps, so the caller can read it as ordinary LRC. */
    fun parse(lrc: String): List<LyricLine> {
        val rows = lrc.lineSequence()
            .mapNotNull { LINE.matchEntire(it.trim()) }
            .map { match ->
                val (minutes, seconds, fraction, body) = match.destructured
                Row(stamp(minutes, seconds, fraction), WORD.findAll(body).toList(), body.trim())
            }
            .sortedBy { it.timeMs }
            .toList()
        if (rows.none { it.stamps.isNotEmpty() }) return emptyList()

        return rows.mapIndexedNotNull { index, row ->
            if (row.stamps.isEmpty()) {
                return@mapIndexedNotNull row.plain.takeIf { it.isNotEmpty() }
                    ?.let { LyricLine(row.timeMs, it) }
            }
            val lineEnd = rows.getOrNull(index + 1)?.timeMs
                ?: (startOf(row.stamps.last()) + LAST_WORD_TAIL_MS)
            val words = row.stamps.mapIndexedNotNull { wordIndex, match ->
                match.groupValues[4].trim().takeIf { it.isNotEmpty() }?.let { text ->
                    val end = row.stamps.getOrNull(wordIndex + 1)?.let(::startOf) ?: lineEnd
                    LyricWord(startOf(match), end, text)
                }
            }
            if (words.isEmpty()) return@mapIndexedNotNull null
            LyricLine(
                timeMs = minOf(row.timeMs, words.first().startMs),
                text = words.joinToString(" ") { it.text },
                words = words,
            )
        }
    }

    private fun startOf(match: MatchResult): Long =
        stamp(match.groupValues[1], match.groupValues[2], match.groupValues[3])

    private fun stamp(minutes: String, seconds: String, fraction: String): Long {
        // Two digits mean centiseconds, three mean milliseconds.
        val fractionMs = if (fraction.length == 3) fraction.toLong() else fraction.toLong() * 10
        return minutes.toLong() * 60_000 + seconds.toLong() * 1_000 + fractionMs
    }
}
