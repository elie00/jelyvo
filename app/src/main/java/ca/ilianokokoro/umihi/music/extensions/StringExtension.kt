package ca.ilianokokoro.umihi.music.extensions

import java.text.Normalizer

/** Matches the combining diacritical marks left over after NFD decomposition. */
private val COMBINING_MARKS_REGEX = Regex("\\p{Mn}+")

/** Lowercased copy with diacritics stripped, for accent- and case-insensitive matching. */
fun String.normalizedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS_REGEX, "")
        .lowercase()

/** Accent- and case-insensitive containment check (e.g. "melodie" matches "Mélodie"). */
fun String.containsNormalized(other: String): Boolean =
    normalizedForSearch().contains(other.normalizedForSearch())

/**
 * Whole seconds parsed from a display duration ("s", "m:ss" or "h:mm:ss"). Returns 0 when the
 * string is empty or unparseable, so a row with no duration sorts first instead of vanishing.
 */
fun String.durationToSeconds(): Int {
    if (isBlank()) return 0

    val parts = split(":").map { it.trim().toIntOrNull() ?: return 0 }
    return when (parts.size) {
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0
    }
}
