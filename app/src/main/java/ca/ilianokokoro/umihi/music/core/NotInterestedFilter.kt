package ca.ilianokokoro.umihi.music.core

import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Pure rules behind "don't recommend this song". Android and persistence stay outside this object
 * so queue safety, ordering and corrupt-storage recovery remain covered by JVM tests.
 */
object NotInterestedFilter {

    fun add(current: List<String>, youtubeId: String, max: Int): List<String> {
        if (youtubeId.isBlank()) return current
        return (listOf(youtubeId) + current.filterNot { it == youtubeId }).take(max)
    }

    fun remove(current: List<String>, youtubeId: String): List<String> =
        current.filterNot { it == youtubeId }

    fun decode(stored: String?): List<String> {
        if (stored.isNullOrEmpty()) return emptyList()
        return runCatching { Json.decodeFromString<List<String>>(stored) }
            .getOrDefault(emptyList())
    }

    fun encode(ids: List<String>): String = Json.encodeToString(ids)

    fun excludeHidden(songs: List<Song>, hidden: Set<String>): List<Song> {
        if (hidden.isEmpty()) return songs
        return songs.filterNot { it.youtubeId in hidden }
    }

    /**
     * Only autoplay suggestions may be pruned. The item playing at [currentIndex] always survives,
     * preventing a rejection from interrupting playback or emptying a one-item queue.
     */
    fun hiddenSuggestionIndices(
        queue: List<Song>,
        hidden: Set<String>,
        currentIndex: Int,
    ): List<Int> {
        if (hidden.isEmpty()) return emptyList()
        return queue.indices.filter { index ->
            index != currentIndex &&
                queue[index].isSuggestion &&
                queue[index].youtubeId in hidden
        }
    }

    fun indexAfterRemovals(currentIndex: Int, removedIndices: Collection<Int>): Int =
        currentIndex - removedIndices.count { it < currentIndex }
}
