package ca.ilianokokoro.umihi.music.ui.components.home

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.models.ResumeItem
import ca.ilianokokoro.umihi.music.ui.components.SquareImage

@Composable
fun ResumeListeningRow(
    items: List<ResumeItem>,
    onResume: (ResumeItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.resume_listening),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
        ) {
            items(items = items, key = { it.song.youtubeId }) { item ->
                ResumeCard(item = item, onClick = { onResume(item) })
            }
        }
    }
}

@Composable
private fun ResumeCard(
    item: ResumeItem,
    onClick: () -> Unit,
) {
    val song = item.song
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SquareImage(
            uri = song.thumbnailPath ?: song.thumbnailHref,
            modifier = Modifier.fillMaxWidth()
        )
        LinearProgressIndicator(
            progress = { item.progress },
            modifier = Modifier.fillMaxWidth()
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
}
