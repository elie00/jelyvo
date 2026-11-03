package ca.ilianokokoro.umihi.music.ui.screens.album.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import ca.ilianokokoro.umihi.music.ui.components.studio.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.models.LocalPlaylist
import ca.ilianokokoro.umihi.music.ui.components.studio.AlertDialog
import ca.ilianokokoro.umihi.music.ui.components.studio.ModalBottomSheet
import ca.ilianokokoro.umihi.music.ui.components.studio.SheetActionRow
import ca.ilianokokoro.umihi.music.ui.components.studio.SheetHeader
import ca.ilianokokoro.umihi.music.ui.components.studio.TextButton

/**
 * Overflow menu of the album header, mirroring the player's bottom sheet: actions that don't
 * deserve a dedicated header button (share the album link, add every track to a local playlist).
 */
@Composable
fun AlbumMenuBottomSheet(
    albumName: String,
    albumArtist: String,
    onShare: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            SheetHeader(
                title = albumName,
                subtitle = albumArtist,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            SheetActionRow(
                icon = Icons.Rounded.Share,
                label = stringResource(R.string.share),
                onClick = onShare,
            )

            SheetActionRow(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                label = stringResource(R.string.album_add_to_playlist),
                onClick = onAddToPlaylist,
            )
        }
    }
}

/**
 * Picker for the "add to playlist" action: every local playlist plus a "new playlist" entry
 * that creates one named after the album.
 */
@Composable
fun LocalPlaylistPickerDialog(
    playlists: List<LocalPlaylist>,
    onPick: (LocalPlaylist) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.album_add_to_playlist)) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
            ) {
                item(key = "new-playlist") {
                    PickerRow(
                        icon = Icons.Rounded.Add,
                        text = stringResource(R.string.album_new_playlist),
                        onClick = onCreateNew,
                    )
                }
                items(items = playlists, key = { it.id }) { playlist ->
                    PickerRow(
                        icon = Icons.Rounded.QueueMusic,
                        text = playlist.name,
                        onClick = { onPick(playlist) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            Icon(imageVector = icon, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
