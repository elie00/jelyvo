package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

internal fun isKeyboardContextMenuShortcut(
    key: Key,
    isShiftPressed: Boolean,
): Boolean = key == Key.Menu || (key == Key.F10 && isShiftPressed)

/**
 * Opens the same contextual actions as a long-press or secondary click from a hardware keyboard.
 */
fun Modifier.onKeyboardContextMenu(onOpen: () -> Unit): Modifier = onPreviewKeyEvent { event ->
    if (
        event.type == KeyEventType.KeyDown &&
        isKeyboardContextMenuShortcut(event.key, event.isShiftPressed)
    ) {
        onOpen()
        true
    } else {
        false
    }
}
