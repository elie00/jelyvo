package ca.ilianokokoro.umihi.music.ui.screens.search.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.song.ExplicitBadge

/**
 * The "All" search tab's top-result card, YT Music style: a large thumbnail next to the entity
 * type, title and subtitle, with an inline Play button. Tapping the card itself plays a track or
 * opens the album/artist page.
 */
@Composable
fun TopResultCard(
    item: SearchItem,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val typeLabel = stringResource(
        when (item) {
            is SearchItem.Track ->
                if (item.song.isVideo) R.string.top_result_video else R.string.top_result_song

            is SearchItem.Album -> R.string.top_result_album
            is SearchItem.Artist -> R.string.top_result_artist
        }
    )
    val title = when (item) {
        is SearchItem.Track -> item.song.title
        is SearchItem.Album -> item.title
        is SearchItem.Artist -> item.name
    }
    val subtitle = when (item) {
        is SearchItem.Track -> {
            val song = item.song
            val detail = if (song.isVideo) song.viewCount.orEmpty() else song.duration
            listOf(song.artist, detail)
                .filter(String::isNotBlank)
                .joinToString(" ${stringResource(R.string.dot)} ")
        }

        is SearchItem.Album -> item.subtitle
        is SearchItem.Artist -> ""
    }
    val thumbnail = when (item) {
        is SearchItem.Track -> item.song.thumbnailHref
        is SearchItem.Album -> item.thumbnail
        is SearchItem.Artist -> item.thumbnail
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.top_result),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SquareImage(
                    uri = thumbnail,
                    modifier = Modifier.size(88.dp),
                    cornerRadius = if (item is SearchItem.Artist) 44.dp else 12.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item is SearchItem.Track && item.song.isExplicit) {
                            ExplicitBadge()
                        }
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onPlay,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.play))
                    }
                }
            }
        }
    }
}
