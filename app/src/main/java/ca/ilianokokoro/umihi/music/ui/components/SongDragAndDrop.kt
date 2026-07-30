package ca.ilianokokoro.umihi.music.ui.components

import android.content.ClipData
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode.Companion.Touch
import androidx.compose.ui.platform.LocalInputModeManager
import ca.ilianokokoro.umihi.music.models.Song

const val JELYVO_SONG_DRAG_LABEL = "Jelyvo song"

/**
 * Enables platform drag-and-drop for pointer/keyboard input while preserving touch long-press and
 * swipe gestures. The full [Song] remains local to the app; ClipData only exposes its YouTube id.
 */
@Composable
fun Modifier.songDragAndDropSource(song: Song): Modifier {
    val inputModeManager = LocalInputModeManager.current
    if (inputModeManager.inputMode == Touch) return this

    return dragAndDropSource { _: Offset ->
        DragAndDropTransferData(
            clipData = ClipData.newPlainText(JELYVO_SONG_DRAG_LABEL, song.youtubeId),
            localState = song,
        )
    }
}

internal fun songFromDragLocalState(localState: Any?): Song? = localState as? Song
