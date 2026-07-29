package ca.ilianokokoro.umihi.music.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ca.ilianokokoro.umihi.music.R
import java.util.Locale

/**
 * Localizes stable YouTube Music shelf labels when the upstream API unexpectedly answers in
 * English. Unknown and editorial labels stay untouched, so artist names and campaign titles are
 * never guessed or rewritten.
 */
@Composable
fun localizedApiSectionTitle(rawTitle: String): String {
    val title = rawTitle.trim()
    val normalized = title.lowercase(Locale.ROOT)

    apiSectionStringResource(normalized)?.let { return stringResource(it) }

    val moreFrom = title.removePrefixIgnoringCase("More from ")
    if (moreFrom != null) return stringResource(R.string.api_more_from, moreFrom)

    val similarTo = title.removePrefixIgnoringCase("Similar to ")
    if (similarTo != null) return stringResource(R.string.api_similar_to, similarTo)

    return title
}

@StringRes
private fun apiSectionStringResource(normalized: String): Int? = when (normalized) {
    "popular" -> R.string.popular
    "songs" -> R.string.search_songs
    "albums" -> R.string.albums
    "singles" -> R.string.singles
    "videos" -> R.string.search_videos
    "fans might also like" -> R.string.fans_might_also_like
    "related artists" -> R.string.related_artists
    "other versions" -> R.string.other_versions
    "quick picks" -> R.string.quick_picks
    "listen again" -> R.string.listen_again
    "recommended albums" -> R.string.recommended_albums
    "new releases" -> R.string.new_releases_summary_title
    "mixed for you" -> R.string.mixed_for_you
    "music videos" -> R.string.music_videos
    "live performances" -> R.string.live_performances
    "moods & moments" -> R.string.moods_and_moments
    "music by decade" -> R.string.music_by_decade
    "charts" -> R.string.charts
    else -> null
}

private fun String.removePrefixIgnoringCase(prefix: String): String? {
    if (!startsWith(prefix, ignoreCase = true)) return null
    return drop(prefix.length).trim().takeIf(String::isNotEmpty)
}
