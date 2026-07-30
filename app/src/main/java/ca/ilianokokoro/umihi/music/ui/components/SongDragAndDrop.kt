package ca.ilianokokoro.umihi.music.ui.components

import android.content.ClipData
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.InputMode.Companion.Touch
import androidx.compose.ui.platform.LocalHapticFeedback
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

/**
 * Accepts only in-app [Song] payloads and centralizes hover cleanup and pointer haptics for every
 * drop destination. [onSongDropped] returns whether the destination accepted the track.
 */
@Composable
fun Modifier.songDragAndDropTarget(
    onSongDropped: (Song) -> Boolean,
    onHoverChanged: (Boolean) -> Unit,
): Modifier {
    val hapticFeedback = LocalHapticFeedback.current
    val currentOnSongDropped = rememberUpdatedState(onSongDropped)
    val currentOnHoverChanged = rememberUpdatedState(onHoverChanged)
    val target = remember(hapticFeedback) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                currentOnHoverChanged.value(true)
                hapticFeedback.performHapticFeedback(
                    HapticFeedbackType.GestureThresholdActivate
                )
            }

            override fun onExited(event: DragAndDropEvent) {
                currentOnHoverChanged.value(false)
            }

            override fun onEnded(event: DragAndDropEvent) {
                currentOnHoverChanged.value(false)
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val song = songFromDragLocalState(
                    event.toAndroidDragEvent().localState
                ) ?: return false

                currentOnHoverChanged.value(false)
                return currentOnSongDropped.value(song).also { accepted ->
                    if (accepted) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                    }
                }
            }
        }
    }

    return dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            songFromDragLocalState(event.toAndroidDragEvent().localState) != null
        },
        target = target,
    )
}

internal fun songFromDragLocalState(localState: Any?): Song? = localState as? Song
