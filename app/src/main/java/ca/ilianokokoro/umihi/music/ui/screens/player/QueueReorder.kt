package ca.ilianokokoro.umihi.music.ui.screens.player

import ca.ilianokokoro.umihi.music.ui.screens.localplaylist.moveByFlatIndex

/**
 * Moves a queue item by drag-and-drop, constrained to the user part of the queue.
 *
 * Autoplay suggestions (contiguous tail marked `isSuggestion`) are deliberately NOT reorderable:
 * their order belongs to autoplay, and dragging one above the boundary would silently promote it
 * to the user queue while [ca.ilianokokoro.umihi.music.core.managers.PlayerManager] still counts
 * it as a suggestion. So a drag starting on a suggestion is a no-op, and a user item dropped past
 * the boundary is clamped to the last user slot instead of landing between suggestions.
 *
 * Returns the SAME list instance when nothing moves, so callers can cheaply detect a no-op
 * (e.g. to skip haptics and the MediaController move).
 */
internal fun <T> moveWithinUserQueue(
    queue: List<T>,
    fromIndex: Int,
    toIndex: Int,
    isSuggestion: (T) -> Boolean,
): List<T> {
    val firstSuggestionIndex = queue.indexOfFirst(isSuggestion)
    val userQueueSize = if (firstSuggestionIndex >= 0) firstSuggestionIndex else queue.size

    if (fromIndex !in 0 until userQueueSize) {
        return queue
    }

    // Reuses the tested local-playlist move; the queue LazyColumn has no header rows (section
    // titles live inside item content), hence headerItems = 0.
    val clampedTo = toIndex.coerceIn(0, userQueueSize - 1)
    if (clampedTo == fromIndex) {
        return queue
    }

    return moveByFlatIndex(queue, fromIndex, clampedTo, headerItems = 0)
}
