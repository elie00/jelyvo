package ca.ilianokokoro.umihi.music.ui.components.song

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.keyboardFocusOutline
import ca.ilianokokoro.umihi.music.ui.components.onKeyboardContextMenu
import ca.ilianokokoro.umihi.music.ui.components.materialu.dropdown.MaterialUDropdown
import ca.ilianokokoro.umihi.music.ui.components.materialu.dropdown.MaterialUDropdownItem
import ca.ilianokokoro.umihi.music.ui.components.onSecondaryClick
import sh.calvin.reorderable.ReorderableCollectionItemScope


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
) {
    var expanded by remember { mutableStateOf(false) }
    val innerHeight = 60.dp
    val containerColor by animateColorAsState(
        targetValue = if (isCurrentSong) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 180),
        label = "queueItemContainer",
    )

    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .keyboardFocusOutline(RoundedCornerShape(18.dp))
            .onKeyboardContextMenu { expanded = true }
            .onSecondaryClick { expanded = true }
            .clickable(onClick = onPress),
        leadingContent = {
            SongThumbnail(song = song, size = innerHeight)
        },
        trailingContent = {

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {


                IconButton(onClick = { expanded = true }) {
                    Icon(
                        Icons.Rounded.MoreVert, contentDescription = stringResource(
                            R.string.more
                        )
                    )

                    MaterialUDropdown(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {

                        MaterialUDropdownItem(
                            leadingIcon = Icons.Rounded.Remove,
                            text = stringResource(R.string.remove_from_queue),
                            onClick = {
                                onDelete()
                                expanded = false
                            }
                        )
                    }
                }

                // Suggestions are not reorderable (autoplay owns their order), so their rows
                // simply have no drag handle.
                if (isDraggable) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = with(scope) {
                            Modifier
                                .minimumInteractiveComponentSize()
                                .size(48.dp)
                                .draggableHandle(
                                    onDragStarted = { onDragStarted() },
                                    onDragStopped = onDragStopped,
                                )
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = stringResource(R.string.reorder),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        },
        supportingContent = {
            Text(
                songSubtitle(song),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee()
            )
        },
        colors = ListItemDefaults.colors(containerColor = containerColor),
        verticalAlignment = Alignment.CenterVertically,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isCurrentSong) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = stringResource(R.string.playing_now),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    song.title,
                    color = if (isCurrentSong) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .basicMarquee()
                )
                if (song.isExplicit) {
                    ExplicitBadge()
                }
            }
        },
    )

}
