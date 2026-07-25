package ca.ilianokokoro.umihi.music.core.cast

import ca.ilianokokoro.umihi.music.models.Song
import kotlin.math.min

data class CastHandoverPlan(
    val songs: List<Song>,
    val startIndex: Int,
    val startPositionMs: Long,
    val skippedLocalCount: Int,
)

/**
 * Removes receiver-inaccessible local files while keeping queue order and remapping playback.
 */
fun planCastHandover(
    songs: List<Song>,
    currentIndex: Int,
    positionMs: Long,
): CastHandoverPlan {
    val castable = songs.withIndex().filter { it.value.isCastable() }
    if (castable.isEmpty()) {
        return CastHandoverPlan(
            songs = emptyList(),
            startIndex = -1,
            startPositionMs = 0L,
            skippedLocalCount = songs.size,
        )
    }

    val currentCastIndex = castable.indexOfFirst { it.index == currentIndex }
    val startIndex: Int
    val startPositionMs: Long
    if (currentCastIndex >= 0) {
        startIndex = currentCastIndex
        startPositionMs = positionMs.coerceAtLeast(0L)
    } else {
        val nextCastIndex = castable.indexOfFirst { it.index > currentIndex }
        // If a trailing local item was current, continue from the closest preceding stream.
        startIndex = if (nextCastIndex >= 0) nextCastIndex else castable.lastIndex
        startPositionMs = 0L
    }

    return CastHandoverPlan(
        songs = castable.map { it.value },
        startIndex = startIndex,
        startPositionMs = startPositionMs,
        skippedLocalCount = songs.size - castable.size,
    )
}

/**
 * The receiver may preload the next item. Resolve the current entry plus [lookahead] successors.
 */
fun resolutionWindow(
    currentIndex: Int,
    size: Int,
    lookahead: Int = 2,
): IntRange {
    if (size <= 0) return IntRange.EMPTY
    val start = currentIndex.coerceIn(0, size - 1)
    return start..min(size - 1, start + lookahead.coerceAtLeast(0))
}
