package ca.ilianokokoro.umihi.music.ui.components.song

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.managers.LikeManager
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.materialu.dropdown.MaterialUDropdown
import ca.ilianokokoro.umihi.music.ui.components.materialu.dropdown.MaterialUDropdownItem

@Composable
fun SongListItem(
    song: Song,
    onPress: () -> Unit,
    playNext: () -> Unit,
    addToQueue: () -> Unit,
    startRadio: () -> Unit,
    modifier: Modifier = Modifier,
    onViewArtist: (() -> Unit)? = null,
    onViewAlbum: (() -> Unit)? = null,
    download: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(song.youtubeId) {
        PlayerManager.prewarmStream(context, song)
    }
    val isLoggedIn by remember(context) { LikeManager.isLoggedIn(context) }
        .collectAsStateWithLifecycle(initialValue = false)

    ListItem(
        modifier = modifier
            .combinedClickable(onClick = onPress, onLongClick = { expanded = true }),
        leadingContent = {
            SongThumbnail(song = song)
        },
        trailingContent = {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more)
                )
            }
            MaterialUDropdown(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                MaterialUDropdownItem(
                    leadingIcon = Icons.Rounded.PlayCircleOutline,
                    text = stringResource(R.string.play_next),
                    onClick = {
                        playNext()
                        expanded = false
                    }
                )
                MaterialUDropdownItem(
                    leadingIcon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                    text = stringResource(R.string.add_to_queue),
                    onClick = {
                        addToQueue()
                        expanded = false
                    }
                )
                MaterialUDropdownItem(
                    leadingIcon = Icons.Rounded.Radio,
                    text = stringResource(R.string.start_radio),
                    onClick = {
                        startRadio()
                        expanded = false
                    }
                )
                if (isLoggedIn) {
                    MaterialUDropdownItem(
                        leadingIcon = Icons.Rounded.FavoriteBorder,
                        text = stringResource(R.string.add_to_liked),
                        onClick = {
                            LikeManager.addToLiked(song, context)
                            expanded = false
                        }
                    )
                }
                if (onViewArtist != null && song.artistId != null) {
                    MaterialUDropdownItem(
                        leadingIcon = Icons.Rounded.Person,
                        text = stringResource(R.string.view_artist),
                        onClick = {
                            onViewArtist()
                            expanded = false
                        }
                    )
                }
                if (onViewAlbum != null && song.albumId != null) {
                    MaterialUDropdownItem(
                        leadingIcon = Icons.Rounded.Album,
                        text = stringResource(R.string.view_album),
                        onClick = {
                            onViewAlbum()
                            expanded = false
                        }
                    )
                }
                if (download != null && !song.downloaded) {
                    MaterialUDropdownItem(
                        leadingIcon = Icons.Rounded.Download,
                        text = stringResource(R.string.download),
                        onClick = {
                            download()
                            expanded = false
                        }
                    )
                }
            }
        },
        supportingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (song.downloaded) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = Icons.Rounded.DownloadForOffline,
                        contentDescription = null
                    )
                }

                Text(
                    text = songSubtitle(song),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee()
                )
            }
        },
        colors = ListItemDefaults.colors(),
        verticalAlignment = Alignment.CenterVertically,
        content = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = song.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .basicMarquee()
                )
                if (song.isExplicit) {
                    ExplicitBadge()
                }
            }
        },
    )
}

/**
 * Subtitle line of a track row: "Artist • 3:45" for songs, "Artist • 1.2M views • 3:45" for
 * video rows. Blank/absent parts are skipped so the separators never dangle.
 */
@Composable
internal fun songSubtitle(song: Song): String {
    val separator = stringResource(R.string.dot)
    return listOfNotNull(
        song.artist.takeIf(String::isNotBlank),
        song.viewCount.takeIf { song.isVideo }?.takeIf(String::isNotBlank),
        song.duration.takeIf(String::isNotBlank),
    ).joinToString(" $separator ")
}
