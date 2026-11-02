package ca.ilianokokoro.umihi.music.ui.components.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.songDragAndDropTarget

@Composable
fun PlaylistCard(
    playlistInfo: PlaylistInfo,
    onClicked: () -> Unit,
    modifier: Modifier = Modifier,
    /** Artwork of the first tracks; on-device playlists have no cover of their own. */
    coverUris: List<String> = emptyList(),
    /** Only editable, on-device playlists provide this callback and become drop destinations. */
    onSongDropped: ((Song) -> Unit)? = null,
) {
    var isDropHovered by remember { mutableStateOf(false) }
    val dropTargetModifier = if (onSongDropped == null) {
        Modifier
    } else {
        Modifier.songDragAndDropTarget(
            onSongDropped = { song ->
                onSongDropped(song)
                true
            },
            onHoverChanged = { isDropHovered = it },
        )
    }

    Box(
        modifier = modifier
            .then(dropTargetModifier)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClicked),
    ) {
        Box {
            Column(modifier = Modifier.fillMaxWidth()) {
                val specialCover = specialPlaylistCover(playlistInfo.id)
                when {
                    // Les playlists automatiques portent la pochette de marque plutot que le visuel
                    // generique servi par YouTube (ou aucun, pour les telechargements).
                    specialCover != null -> JelyvoPlaylistCover(
                        cover = specialCover,
                        contentDescription = stringResource(R.string.playlist_cover),
                    )

                    coverUris.isNotEmpty() -> PlaylistMosaicCover(
                        uris = coverUris,
                        contentDescription = stringResource(R.string.playlist_cover),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    else -> SquareImage(
                        uri = playlistInfo.coverPath ?: playlistInfo.coverHref,
                        contentDescription = stringResource(R.string.playlist_cover),
                    )
                }

                val displayTitle = if (specialCover == SpecialPlaylistCover.Liked) {
                    stringResource(R.string.liked_songs)
                } else {
                    playlistInfo.title
                }

                Text(
                    text = displayTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            if (isDropHovered) {
                Surface(
                    modifier = Modifier.matchParentSize(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            8.dp,
                            Alignment.CenterVertically,
                        ),
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                        )
                        Text(
                            text = stringResource(R.string.drop_to_playlist),
                            style = MaterialTheme.typography.titleSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
