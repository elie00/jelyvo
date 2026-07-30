package ca.ilianokokoro.umihi.music.ui.components.song

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput

/** Keyboard modifiers captured with a primary pointer click on a song row. */
data class SongSelectionModifiers(
    val toggle: Boolean = false,
    val range: Boolean = false,
)

/** Immutable result of applying one desktop selection click. */
data class SongSelectionUpdate(
    val selectedIds: Set<String>,
    val anchorId: String?,
)

/**
 * Applies desktop list-selection conventions to stable song ids.
 *
 * - Ctrl/Command toggles one item.
 * - Shift selects the inclusive range from the last anchor.
 * - A plain pointer click while selection mode is already active toggles one item.
 */
fun updateSongSelection(
    orderedIds: List<String>,
    selectedIds: Set<String>,
    anchorId: String?,
    clickedId: String,
    modifiers: SongSelectionModifiers,
): SongSelectionUpdate {
    if (clickedId !in orderedIds) {
        return SongSelectionUpdate(selectedIds, anchorId)
    }

    if (modifiers.range) {
        val anchorIndex = orderedIds.indexOf(anchorId).takeIf { it >= 0 }
        val clickedIndex = orderedIds.indexOf(clickedId)
        val rangeIds = if (anchorIndex == null) {
            setOf(clickedId)
        } else {
            val bounds = minOf(anchorIndex, clickedIndex)..maxOf(anchorIndex, clickedIndex)
            bounds.mapTo(linkedSetOf()) { orderedIds[it] }
        }
        return SongSelectionUpdate(
            selectedIds = if (modifiers.toggle) selectedIds + rangeIds else rangeIds,
            anchorId = clickedId,
        )
    }

    val updated = if (clickedId in selectedIds) {
        selectedIds - clickedId
    } else {
        selectedIds + clickedId
    }
    return SongSelectionUpdate(
        selectedIds = updated,
        anchorId = clickedId.takeIf { updated.isNotEmpty() },
    )
}

/**
 * Consumes only primary mouse/stylus presses used for selection. Ordinary clicks and every touch
 * gesture continue to the row's existing combinedClickable/swipe handlers.
 */
fun Modifier.onSongSelectionClick(
    enabled: Boolean,
    selectionActive: Boolean,
    onSelectionClick: (SongSelectionModifiers) -> Unit,
): Modifier {
    if (!enabled) return this

    return pointerInput(selectionActive, onSelectionClick) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.type != PointerEventType.Press || !event.buttons.isPrimaryPressed) {
                    continue
                }

                val keyboard = event.keyboardModifiers
                val modifiers = SongSelectionModifiers(
                    toggle = keyboard.isCtrlPressed || keyboard.isMetaPressed,
                    range = keyboard.isShiftPressed,
                )
                if (selectionActive || modifiers.toggle || modifiers.range) {
                    event.changes.forEach { it.consume() }
                    onSelectionClick(modifiers)
                }
            }
        }
    }
}

