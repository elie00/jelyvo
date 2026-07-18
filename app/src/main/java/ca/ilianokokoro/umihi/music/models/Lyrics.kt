package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.models.dto.LrclibResponse

/**
 * A single line of lyrics.
 *
 * @param timeMs Timestamp of the line in milliseconds, or `null` when the line
 *  comes from unsynchronized (plain) lyrics.
 * @param text The lyric text for this line (may be empty for musical interludes).
 */
@Immutable
data class LyricLine(
    val timeMs: Long?,
    val text: String,
)

/**
 * Parsed lyrics for a song.
 *
 * @param lines Ordered list of lyric lines.
 * @param isSynced True when the lines carry timestamps (LRC synced lyrics),
 *  false when they are plain text.
 */
@Immutable
data class Lyrics(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
) {
    val isEmpty: Boolean
        get() = lines.isEmpty()

    companion object {
        // Matches one or more leading LRC timestamps, e.g. "[01:23.45]" or "[1:23]".
        // Group 1: minutes, group 2: seconds, group 3 (optional): fractional part.
        private val LRC_TIMESTAMP = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

        /**
         * Parses a raw LRC string into timestamped [LyricLine]s.
         *
         * A single LRC line may carry several timestamps (repeated lyrics); each
         * produces its own [LyricLine]. Lines without a valid timestamp are kept
         * with a `null` [LyricLine.timeMs]. The result is sorted by timestamp,
         * with untimed lines preserved in their original relative order at the end.
         */
        fun parseLrc(lrc: String): List<LyricLine> {
            val timed = mutableListOf<LyricLine>()
            val untimed = mutableListOf<LyricLine>()

            for (rawLine in lrc.lineSequence()) {
                val matches = LRC_TIMESTAMP.findAll(rawLine).toList()
                val text = rawLine.replace(LRC_TIMESTAMP, "").trim()

                if (matches.isEmpty()) {
                    if (text.isNotEmpty()) {
                        untimed.add(LyricLine(timeMs = null, text = text))
                    }
                    continue
                }

                for (match in matches) {
                    timed.add(LyricLine(timeMs = timestampToMs(match), text = text))
                }
            }

            return timed.sortedBy { it.timeMs ?: Long.MAX_VALUE } + untimed
        }

        /**
         * Splits plain (unsynchronized) lyrics into [LyricLine]s, each with a
         * `null` timestamp.
         */
        fun parsePlain(plain: String): List<LyricLine> {
            return plain.lineSequence()
                .map { it.trim() }
                .map { LyricLine(timeMs = null, text = it) }
                .toList()
                // Drop leading/trailing blank lines but keep interior blanks
                // (they act as verse separators).
                .dropWhile { it.text.isEmpty() }
                .dropLastWhile { it.text.isEmpty() }
        }

        /**
         * Builds a [Lyrics] from an LRCLIB response, preferring synced lyrics
         * when available, then falling back to plain lyrics. Returns `null` when
         * neither is present.
         */
        fun fromResponse(response: LrclibResponse): Lyrics? {
            val synced = response.syncedLyrics
            if (!synced.isNullOrBlank()) {
                val lines = parseLrc(synced)
                if (lines.isNotEmpty()) {
                    return Lyrics(lines = lines, isSynced = true)
                }
            }

            val plain = response.plainLyrics
            if (!plain.isNullOrBlank()) {
                val lines = parsePlain(plain)
                if (lines.isNotEmpty()) {
                    return Lyrics(lines = lines, isSynced = false)
                }
            }

            return null
        }

        private fun timestampToMs(match: MatchResult): Long {
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fractionRaw = match.groupValues.getOrNull(3).orEmpty()

            // Normalize fractional part to milliseconds (".4" -> 400, ".45" -> 450, ".456" -> 456).
            val fractionMs = when (fractionRaw.length) {
                0 -> 0L
                1 -> (fractionRaw.toLong()) * 100
                2 -> (fractionRaw.toLong()) * 10
                else -> fractionRaw.take(3).toLong()
            }

            return (minutes * 60 + seconds) * 1000 + fractionMs
        }
    }
}
