/*
 * Measures adapted from BitChord's InlineQueueRow
 * (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
package ca.ilianokokoro.umihi.music.ui.components.song

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.managers.NotInterestedStore
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.keyboardFocusOutline
import ca.ilianokokoro.umihi.music.ui.components.studio.StudioMenu
import ca.ilianokokoro.umihi.music.ui.components.studio.StudioMenuItem
import ca.ilianokokoro.umihi.music.ui.components.onKeyboardContextMenu
import ca.ilianokokoro.umihi.music.ui.components.onSecondaryClick
import sh.calvin.reorderable.ReorderableCollectionItemScope

private val RowShape = RoundedCornerShape(8.dp)

/** The row's height stays at BitChord's 56dp, while the two controls keep a full 48dp to aim at. */
private val ControlHeight = 48.dp

@Composable
fun QueueSongListItem(
    song: Song,
    isCurrentSong: Boolean,
    onPress: () -> Unit,
    onDelete: () -> Unit,
    scope: ReorderableCollectionItemScope,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
    isDraggable: Boolean = true,
    /** Rows are see-through at rest, so the one in the air needs a fill of its own to pass over others. */
    lifted: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(if (lifted) colors.surfaceContainerHighest else Color.Transparent)
            .keyboardFocusOutline(RowShape)
            .onKeyboardContextMenu { expanded = true }
            .onSecondaryClick { expanded = true }
            .clickable(onClick = onPress)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Suggestions are not reorderable (autoplay owns their order), so their rows
        // simply have no drag handle.
        if (isDraggable) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = with(scope) {
                    Modifier
                        .size(width = 32.dp, height = ControlHeight)
                        .draggableHandle(
                            onDragStarted = { onDragStarted() },
                            onDragStopped = onDragStopped,
                        )
                },
            ) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = stringResource(R.string.reorder),
                    tint = colors.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        SongThumbnail(song = song, size = 44.dp, cornerRadius = 6.dp)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isCurrentSong) colors.onSurface else colors.onSurface.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        // Only the one playing moves: a whole list of titles sliding at once is noise.
                        .then(if (isCurrentSong) Modifier.basicMarquee() else Modifier),
                )
                if (song.isExplicit) {
                    ExplicitBadge()
                }
            }
            Text(
                songSubtitle(song),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (isCurrentSong) {
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = stringResource(R.string.playing_now),
                tint = colors.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }

        // BitChord has a bare cross here. A menu instead, because the row also starts a radio and
        // rejects a suggestion, and a cross one slip from the drag handle would cost a track.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = 40.dp, height = ControlHeight)
                .clickable { expanded = true },
        ) {
            Icon(
                Icons.Rounded.MoreHoriz,
                contentDescription = stringResource(R.string.more),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )

            StudioMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                StudioMenuItem(
                    leadingIcon = Icons.Rounded.Radio,
                    text = stringResource(R.string.start_radio),
                    onClick = {
                        PlayerManager.startRadio(song, context)
                        expanded = false
                    },
                )

                // Rejecting a track is only actionable where it was actually suggested:
                // hide() drops it from the queue's autoplay tail and from later mixes.
                if (song.isSuggestion) {
                    StudioMenuItem(
                        leadingIcon = Icons.Rounded.NotInterested,
                        text = stringResource(R.string.not_interested),
                        onClick = {
                            NotInterestedStore.hide(song, context)
                            expanded = false
                        },
                    )
                }

                StudioMenuItem(
                    leadingIcon = Icons.Rounded.Remove,
                    text = stringResource(R.string.remove_from_queue),
                    onClick = {
                        onDelete()
                        expanded = false
                    },
                )
            }
        }
    }
}
