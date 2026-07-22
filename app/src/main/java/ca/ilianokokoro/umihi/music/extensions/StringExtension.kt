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
