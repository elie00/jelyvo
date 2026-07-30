package ca.ilianokokoro.umihi.music.ui.components.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.keyboardFocusOutline
import ca.ilianokokoro.umihi.music.ui.components.onKeyboardContextMenu
import ca.ilianokokoro.umihi.music.ui.components.onSecondaryClick

@Composable
fun RecentlyPlayedRow(
    songs: List<Song>,
    onSongClick: (Song) -> Unit,
    onRemoveFromHistory: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.recently_played),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(items = songs, key = { it.youtubeId }) { song ->
                RecentlyPlayedCard(
                    song = song,
                    onClick = { onSongClick(song) },
                    onRemoveFromHistory = { onRemoveFromHistory(song) },
                )
            }
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
        Column(
            modifier = Modifier
                .width(136.dp)
                .keyboardFocusOutline(RoundedCornerShape(20.dp))
                .onKeyboardContextMenu { menuExpanded = true }
                .onSecondaryClick { menuExpanded = true }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuExpanded = true },
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SquareImage(
                uri = song.thumbnailPath ?: song.thumbnailHref,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
            )
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee()
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        HistoryCardMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onRemoveFromHistory = {
                menuExpanded = false
                onRemoveFromHistory()
            },
        )
    }
}

/** Menu d'appui long partagé par les cartes issues de l'historique local. */
@Composable
internal fun HistoryCardMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRemoveFromHistory: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.remove_from_history)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                )
            },
            onClick = onRemoveFromHistory,
        )
    }
}
