package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** A "Moods & genres" hub section, e.g. "Moods & moments" or "Genres". */
@Serializable
@Immutable
data class MoodCategorySection(
    val title: String,
    val categories: List<MoodCategory>,
)

/** A single mood/genre chip; [browseId] + [params] open its category page. */
@Serializable
@Immutable
data class MoodCategory(
    val title: String,
    val browseId: String,
    val params: String,
)
