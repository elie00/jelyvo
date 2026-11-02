package ca.ilianokokoro.umihi.music.ui.components.home

import ca.ilianokokoro.umihi.music.ui.components.ShelfCard
import ca.ilianokokoro.umihi.music.ui.components.Shelf
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.ResumeItem
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.keyboardFocusOutline
import ca.ilianokokoro.umihi.music.ui.components.onKeyboardContextMenu
import ca.ilianokokoro.umihi.music.ui.components.onSecondaryClick
import ca.ilianokokoro.umihi.music.ui.components.songDragAndDropSource

@Composable
fun ResumeListeningRow(
    items: List<ResumeItem>,
    onResume: (ResumeItem) -> Unit,
    onRemoveFromHistory: (ResumeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Shelf(title = stringResource(R.string.resume_listening), modifier = modifier) {
            items(items = items, key = { it.song.youtubeId }) { item ->
                ResumeCard(
                    item = item,
                    onClick = { onResume(item) },
                    onRemoveFromHistory = { onRemoveFromHistory(item) },
                )
            }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ResumeCard(
    item: ResumeItem,
    onClick: () -> Unit,
    onRemoveFromHistory: () -> Unit,
) {
    val song = item.song
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
            belowArtwork = {
                LinearProgressIndicator(
                    progress = { item.progress },
                    drawStopIndicator = {},
                    gapSize = 0.dp,
                    color = MaterialTheme.colorScheme.onSurface,
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            },
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
