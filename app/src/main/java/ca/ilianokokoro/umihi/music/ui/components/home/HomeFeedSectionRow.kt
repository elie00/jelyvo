@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.models.HomeFeedItem
import ca.ilianokokoro.umihi.music.models.HomeFeedSection
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.SquareImage

@Composable
fun HomeFeedSectionRow(
    section: HomeFeedSection,
    onPlay: (Song) -> Unit,
    onOpenPlaylist: (HomeFeedItem.Playlist) -> Unit,
    onOpenAlbum: (HomeFeedItem.Album) -> Unit,
    onOpenArtist: (HomeFeedItem.Artist) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (section.title.isNotBlank()) {
            Text(
                text = section.title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        ) {
            itemsIndexed(
                items = section.items,
                key = { index, item -> "${section.title}-$index-$item" },
            ) { _, item ->
                val title: String
                val subtitle: String
                val thumbnail: String
                val onClick: () -> Unit
                when (item) {
                    is HomeFeedItem.Track -> {
                        title = item.song.title
                        subtitle = item.song.artist
                        thumbnail = item.song.thumbnailPath ?: item.song.thumbnailHref
                        onClick = { onPlay(item.song) }
                    }
                    is HomeFeedItem.Playlist -> {
                        title = item.playlist.title
                        subtitle = item.subtitle
                        thumbnail = item.playlist.coverPath ?: item.playlist.coverHref
                        onClick = { onOpenPlaylist(item) }
                    }
                    is HomeFeedItem.Album -> {
                        title = item.title
                        subtitle = item.subtitle
                        thumbnail = item.thumbnail
                        onClick = { onOpenAlbum(item) }
                    }
                    is HomeFeedItem.Artist -> {
                        title = item.name
                        subtitle = ""
                        thumbnail = item.thumbnail
                        onClick = { onOpenArtist(item) }
                    }
                }

                Column(
                    modifier = Modifier.width(152.dp).clickable(onClick = onClick),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SquareImage(
                        uri = thumbnail,
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = if (item is HomeFeedItem.Artist) 76.dp else 12.dp,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
