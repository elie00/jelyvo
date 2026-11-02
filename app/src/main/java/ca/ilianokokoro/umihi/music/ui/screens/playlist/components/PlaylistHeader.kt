package ca.ilianokokoro.umihi.music.ui.screens.playlist.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.ui.components.PageGutter
import ca.ilianokokoro.umihi.music.ui.components.playlist.PlaylistInfo

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
        )
        if (playlist.songs.isNotEmpty()) {
            ActionButtons(
                onPlayClicked = {
                    onOpenPlayer()
                    onPlayPlaylist()
                },
                onShuffleClicked = {
                    onOpenPlayer()
                    onShufflePlaylist()
                },
                onRadioClicked = {
                    onOpenPlayer()
                    ca.ilianokokoro.umihi.music.core.managers.PlayerManager.startRadio(
                        playlist.songs.first(),
                    )
                },
                onDownloadClicked = onDownloadPlaylist,
            )
        }
    }
}
