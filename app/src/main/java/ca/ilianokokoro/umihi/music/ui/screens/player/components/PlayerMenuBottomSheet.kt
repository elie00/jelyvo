package ca.ilianokokoro.umihi.music.ui.screens.player.components

import android.app.Application
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicVideo
import androidx.compose.material.icons.rounded.NotInterested
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.helpers.ShareHelper
import ca.ilianokokoro.umihi.music.core.managers.AbLoop
import ca.ilianokokoro.umihi.music.core.managers.NotInterestedStore
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.data.repositories.LocalPlaylistRepository
import ca.ilianokokoro.umihi.music.models.LocalPlaylist
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.adapters.toTrack
import ca.ilianokokoro.umihi.music.ui.components.song.SongThumbnail
import ca.ilianokokoro.umihi.music.ui.components.studio.ModalBottomSheet
import ca.ilianokokoro.umihi.music.ui.components.studio.SheetActionRow
import ca.ilianokokoro.umihi.music.ui.components.studio.SheetHeader
import ca.ilianokokoro.umihi.music.ui.screens.album.components.LocalPlaylistPickerDialog
import kotlinx.coroutines.launch

/**
 * Overflow menu for the full-screen player. Only exposes actions that are not
 * already reachable from the player screen itself (radio, album, share, equalizer).
 */
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
    val defaultPlaylistName = stringResource(R.string.playlist_default_name)
    val application = context.applicationContext as Application
    val playlistRepository = remember(application) { LocalPlaylistRepository(application) }
    val scope = rememberCoroutineScope()
    var showPlaylistPicker by remember { mutableStateOf(false) }
    var localPlaylists by remember { mutableStateOf<List<LocalPlaylist>>(emptyList()) }

    ModalBottomSheet(onDismissRequest = { changeVisibility(false) }) {
        PlayerMenuContent(
            song = song,
            abLoop = abLoop,
            onStartRadio = {
                PlayerManager.startRadio(song, context)
                changeVisibility(false)
            },
            onAddToPlaylist = {
                scope.launch {
                    localPlaylists = playlistRepository.getAll()
                    showPlaylistPicker = true
                }
            },
            onOpenArtist = {
                changeVisibility(false)
                onOpenArtist()
            },
            onNotInterested = {
                NotInterestedStore.hide(song, context)
                changeVisibility(false)
            },
            onOpenAlbum = {
                changeVisibility(false)
                onOpenAlbum()
            },
            onPlayVideoVersion = {
                onPlayVideoVersion()
                changeVisibility(false)
            },
            onShowDetails = {
                changeVisibility(false)
                onShowDetails()
            },
            onShare = {
                ShareHelper.shareSong(context, song)
                changeVisibility(false)
            },
            onToggleAbLoop = onToggleAbLoop,
            onOpenEqualizer = {
                changeVisibility(false)
                onOpenEqualizer()
            },
            modifier = modifier,
        )
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
                    playlistRepository.createFromTracks(
                        name,
                        listOf(song.toTrack()),
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
internal fun PlayerMenuContent(
    song: Song,
    abLoop: AbLoop?,
    onStartRadio: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenArtist: () -> Unit,
    onNotInterested: () -> Unit,
    onOpenAlbum: () -> Unit,
    onPlayVideoVersion: () -> Unit,
    onShowDetails: () -> Unit,
    onShare: () -> Unit,
    onToggleAbLoop: () -> Unit,
    onOpenEqualizer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // BitChord caps its drawers at 560dp. This menu is longer than that, and at that height its last
    // row sits wholly below the edge with nothing to show there is more to scroll to.
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.8f
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        SheetHeader(
            title = song.title,
            subtitle = song.artist,
            artwork = { SongThumbnail(song = song, size = 52.dp) },
            modifier = Modifier.padding(bottom = 6.dp),
        )

        SheetActionRow(
            icon = Icons.Rounded.Radio,
            label = stringResource(R.string.start_radio),
            onClick = onStartRadio,
        )
        SheetActionRow(
            icon = Icons.AutoMirrored.Rounded.PlaylistAdd,
            label = stringResource(R.string.album_add_to_playlist),
            onClick = onAddToPlaylist,
        )

        if (!song.isLocal) {
            SheetActionRow(
                icon = Icons.Rounded.Person,
                label = stringResource(R.string.view_artist),
                onClick = onOpenArtist,
            )
            SheetActionRow(
                icon = Icons.Rounded.NotInterested,
                label = stringResource(R.string.not_interested),
                onClick = onNotInterested,
            )
            SheetActionRow(
                icon = Icons.Rounded.Album,
                label = stringResource(R.string.view_album),
                onClick = onOpenAlbum,
            )
            SheetActionRow(
                icon = Icons.Rounded.MusicVideo,
                label = stringResource(R.string.play_video_version),
                onClick = onPlayVideoVersion,
            )
        }

        SheetActionRow(
            icon = Icons.Rounded.Info,
            label = stringResource(R.string.about_this_song),
            onClick = onShowDetails,
        )

        if (!song.isLocal) {
            SheetActionRow(
                icon = Icons.Rounded.Share,
                label = stringResource(R.string.share),
                onClick = onShare,
            )
        }

        SheetActionRow(
            icon = Icons.Rounded.Repeat,
            label = when {
                abLoop == null -> stringResource(R.string.ab_loop)
                abLoop.pointBMs == null -> stringResource(R.string.ab_loop_a_set)
                else -> stringResource(R.string.ab_loop_active)
            },
            tint = if (abLoop != null) MaterialTheme.colorScheme.primary else null,
            onClick = onToggleAbLoop,
        )

        SheetActionRow(
            icon = Icons.Rounded.GraphicEq,
            label = stringResource(R.string.equalizer_title),
            onClick = onOpenEqualizer,
        )
    }
}
