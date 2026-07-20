package ca.ilianokokoro.umihi.music.ui.screens.localplaylist.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import sh.calvin.reorderable.ReorderableCollectionItemScope

@Composable
fun EditableSongItem(
    song: LocalPlaylistSong,
    scope: ReorderableCollectionItemScope,
    onPress: () -> Unit,
    onRemove: () -> Unit,
    onDragStarted: () -> Unit,
    onDragStopped: () -> Unit,
) {
    val innerHeight = 60.dp

    ListItem(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onPress),
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(innerHeight)
                    .aspectRatio(1f)
            ) {
                SquareImage(
                    uri = song.thumbnailHref,
                    modifier = Modifier.matchParentSize()
                )
            }
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.remove_from_playlist)
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = stringResource(R.string.reorder),
                    modifier = with(scope) {
                        Modifier
                            .height(innerHeight)
                            .draggableHandle(
                                onDragStarted = { onDragStarted() },
                                onDragStopped = onDragStopped,
                            )
                    },
                )
            }
        },
        supportingContent = {
            Text(
                text = song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee()
            )
        },
        content = { Text(song.title, modifier = Modifier.basicMarquee()) },
    )
}
