package ca.ilianokokoro.umihi.music.ui.screens.playlist.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.ui.components.PageGutter
import ca.ilianokokoro.umihi.music.ui.components.playlist.PlaylistInfo
import ca.ilianokokoro.umihi.music.ui.components.studio.ActionPill
import ca.ilianokokoro.umihi.music.ui.components.studio.CircleIconButton

@Composable
fun PlaylistHeader(
    modifier: Modifier = Modifier,
    isDownloading: Boolean,
    onOpenPlayer: () -> Unit,
    onDownloadPlaylist: () -> Unit,
    onDeleteDownloadPlaylist: () -> Unit,
    onDeletePlaylist: () -> Unit,
    onPlayPlaylist: () -> Unit,
    onCancelDownload: () -> Unit,
    onShufflePlaylist: () -> Unit,
    playlist: Playlist
) {
    Column(
        modifier = modifier.padding(horizontal = PageGutter).padding(top = 8.dp, bottom = 12.dp),
    ) {
        PlaylistInfo(
            playlist = playlist,
            isDownloading = isDownloading,
            onDownloadPressed = onDownloadPlaylist,
            onDeleteDownloadPressed = onDeleteDownloadPlaylist,
            onDeletePressed = onDeletePlaylist,
            onCancelDownload = onCancelDownload,
        onStartRadio = if (playlist.songs.isNotEmpty()) {
            {
                onOpenPlayer()
                ca.ilianokokoro.umihi.music.core.managers.PlayerManager.startRadio(playlist.songs.first())
            }
        } else {
            null
        },
        leadingActions = {
            if (playlist.songs.isNotEmpty()) {
                CircleIconButton(
                    icon = Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.shuffle),
                    onClick = {
                        onOpenPlayer()
                        onShufflePlaylist()
                    },
                )
                ActionPill(
                    icon = Icons.Rounded.PlayArrow,
                    label = stringResource(R.string.play),
                    onClick = {
                        onOpenPlayer()
                        onPlayPlaylist()
                    },
                    filled = true,
                    modifier = Modifier.weight(1f),
                )
            }
        },
        )
    }
}
