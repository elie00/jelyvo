package ca.ilianokokoro.umihi.music.ui.components.home

import ca.ilianokokoro.umihi.music.ui.components.ShelfCard
import ca.ilianokokoro.umihi.music.ui.components.Shelf
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.studio.StudioMenu
import ca.ilianokokoro.umihi.music.ui.components.studio.StudioMenuItem
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.ui.components.keyboardFocusOutline
import ca.ilianokokoro.umihi.music.ui.components.onKeyboardContextMenu
import ca.ilianokokoro.umihi.music.ui.components.onSecondaryClick
import ca.ilianokokoro.umihi.music.ui.components.songDragAndDropSource

@Composable
fun RecentlyPlayedRow(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onRemoveFromHistory: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    Shelf(title = stringResource(R.string.recently_played), modifier = modifier) {
            items(items = songs, key = { it.youtubeId }) { song ->
                RecentlyPlayedCard(
                    song = song,
                    onClick = { onSongClick(song) },
                    onRemoveFromHistory = { onRemoveFromHistory(song) },
                )
            }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentlyPlayedCard(
    song: Song,
    onClick: () -> Unit,
    onRemoveFromHistory: () -> Unit,
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(song.youtubeId) {
        PlayerManager.prewarmStream(context, song)
    }
    Box {
        ShelfCard(
            modifier = Modifier
                .keyboardFocusOutline(RoundedCornerShape(16.dp))
                .songDragAndDropSource(song)
                .onKeyboardContextMenu { menuExpanded = true }
                .onSecondaryClick { menuExpanded = true },
            title = song.title,
            subtitle = song.artist,
            artworkUri = song.thumbnailPath ?: song.thumbnailHref,
            onClick = onClick,
            onLongClick = { menuExpanded = true },
        )
        HistoryCardMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onRemoveFromHistory = {
                menuExpanded = false
                onRemoveFromHistory()
            },
            song = song,
        )
    }
}

/** Menu d'appui long partagé par les cartes issues de l'historique local. */
@Composable
internal fun HistoryCardMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    song: Song? = null,
) {
    val context = LocalContext.current
    StudioMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        song?.let { historySong ->
            StudioMenuItem(
                text = stringResource(R.string.play_next),
                leadingIcon = Icons.Rounded.PlayCircleOutline,
                onClick = {
                    PlayerManager.addNext(historySong, context)
                    onDismiss()
                },
            )
            StudioMenuItem(
                text = stringResource(R.string.add_to_queue),
                leadingIcon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                onClick = {
                    PlayerManager.addToQueue(historySong, context)
                    onDismiss()
                },
            )
        }
        StudioMenuItem(
            text = stringResource(R.string.remove_from_history),
            leadingIcon = Icons.Rounded.Delete,
            onClick = onRemoveFromHistory,
        )
    }
}
