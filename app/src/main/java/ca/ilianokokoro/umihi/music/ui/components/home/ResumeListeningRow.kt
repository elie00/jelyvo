package ca.ilianokokoro.umihi.music.ui.components.home

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
import ca.ilianokokoro.umihi.music.ui.components.onSecondaryClick

@Composable
fun ResumeListeningRow(
    items: List<ResumeItem>,
    onResume: (ResumeItem) -> Unit,
    onRemoveFromHistory: (ResumeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.resume_listening),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(items = items, key = { it.song.youtubeId }) { item ->
                ResumeCard(
                    item = item,
                    onClick = { onResume(item) },
                    onRemoveFromHistory = { onRemoveFromHistory(item) },
                )
            }
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
        Column(
            modifier = Modifier
                .width(136.dp)
                .keyboardFocusOutline(RoundedCornerShape(20.dp))
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
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
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
