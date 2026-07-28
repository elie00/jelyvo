package ca.ilianokokoro.umihi.music.ui.screens.player.components

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicVideo
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.helpers.ShareHelper
import ca.ilianokokoro.umihi.music.core.managers.AbLoop
import ca.ilianokokoro.umihi.music.core.managers.NotInterestedStore
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.LocalPlaylist
import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong
import ca.ilianokokoro.umihi.music.data.repositories.LocalPlaylistRepository
import ca.ilianokokoro.umihi.music.ui.screens.album.components.LocalPlaylistPickerDialog
import kotlinx.coroutines.launch

/**
 * Overflow menu for the full-screen player. Only exposes actions that are not
 * already reachable from the player screen itself (radio, album, share, equalizer).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMenuBottomSheet(
    changeVisibility: (visible: Boolean) -> Unit,
    song: Song,
    onOpenAlbum: () -> Unit,
    onOpenArtist: () -> Unit,
    onShowDetails: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onPlayVideoVersion: () -> Unit,
    abLoop: AbLoop?,
    onToggleAbLoop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val maxMenuHeight = LocalConfiguration.current.screenHeightDp.dp * 0.58f
    val defaultPlaylistName = stringResource(R.string.playlist_default_name)
    val application = context.applicationContext as Application
    val playlistRepository = remember(application) { LocalPlaylistRepository(application) }
    val scope = rememberCoroutineScope()
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var localPlaylists by remember { mutableStateOf<List<LocalPlaylist>>(emptyList()) }

    ModalBottomSheet(
        onDismissRequest = { changeVisibility(false) },
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
        ),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = maxMenuHeight)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 2.dp),
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
            )

            PlayerMenuItem(
                icon = Icons.Rounded.Radio,
                text = stringResource(R.string.start_radio),
                onClick = {
                    PlayerManager.startRadio(song, context)
                    changeVisibility(false)
                },
            )
            PlayerMenuItem(
                icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
                text = stringResource(R.string.album_add_to_playlist),
                onClick = {
                    scope.launch {
                        localPlaylists = playlistRepository.getAll()
                        showPlaylistPicker = true
                    }
                },
            )

            if (!song.isLocal) {
                PlayerMenuItem(
                    icon = Icons.Rounded.Person,
                    text = stringResource(R.string.view_artist),
                    onClick = {
                        changeVisibility(false)
                        onOpenArtist()
                    },
                )
                PlayerMenuItem(
                    icon = Icons.Rounded.NotInterested,
                    text = stringResource(R.string.not_interested),
                    onClick = {
                        NotInterestedStore.hide(song, context)
                        changeVisibility(false)
                    },
                )
                PlayerMenuItem(
                    icon = Icons.Rounded.Album,
                    text = stringResource(R.string.view_album),
                    onClick = {
                        changeVisibility(false)
                        onOpenAlbum()
                    },
                )
                PlayerMenuItem(
                    icon = Icons.Rounded.MusicVideo,
                    text = stringResource(R.string.play_video_version),
                    onClick = {
                        onPlayVideoVersion()
                        changeVisibility(false)
                    },
                )
            }

            PlayerMenuItem(
                icon = Icons.Rounded.Info,
                text = stringResource(R.string.about_this_song),
                onClick = {
                    changeVisibility(false)
                    onShowDetails()
                },
            )

            if (!song.isLocal) {
                PlayerMenuItem(
                    icon = Icons.Rounded.Share,
                    text = stringResource(R.string.share),
                    onClick = {
                        ShareHelper.shareSong(context, song)
                        changeVisibility(false)
                    },
                )
            }

            PlayerMenuItem(
                icon = Icons.Rounded.Repeat,
                text = when {
                    abLoop == null -> stringResource(R.string.ab_loop)
                    abLoop.pointBMs == null -> stringResource(R.string.ab_loop_a_set)
                    else -> stringResource(R.string.ab_loop_active)
                },
                tint = if (abLoop != null) MaterialTheme.colorScheme.primary else null,
                onClick = onToggleAbLoop,
            )

            PlayerMenuItem(
                icon = Icons.Rounded.GraphicEq,
                text = stringResource(R.string.equalizer_title),
                onClick = {
                    changeVisibility(false)
                    onOpenEqualizer()
                },
            )
        }
    }

    if (showPlaylistPicker) {
        LocalPlaylistPickerDialog(
            playlists = localPlaylists,
            onPick = { playlist ->
                showPlaylistPicker = false
                scope.launch {
                    val added = playlistRepository.addSong(playlist.id, song)
                    Toast.makeText(
                        context,
                        if (added) R.string.song_added_to_playlist
                        else R.string.song_already_in_playlist,
                        Toast.LENGTH_SHORT,
                    ).show()
                    changeVisibility(false)
                }
            },
            onCreateNew = {
                showPlaylistPicker = false
                scope.launch {
                    val name = defaultPlaylistName
                    playlistRepository.create(
                        name,
                        listOf(LocalPlaylistSong.fromSong(song)),
                    )
                    Toast.makeText(
                        context,
                        R.string.song_added_to_playlist,
                        Toast.LENGTH_SHORT,
                    ).show()
                    changeVisibility(false)
                }
            },
            onDismiss = { showPlaylistPicker = false },
        )
    }
}

@Composable
private fun PlayerMenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    ListItem(
        headlineContent = { Text(text, color = tint ?: Color.Unspecified) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint ?: LocalContentColor.current,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
