package ca.ilianokokoro.umihi.music.ui.components.playlist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownloadOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import ca.ilianokokoro.umihi.music.R
import androidx.compose.ui.graphics.Color
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.studio.StudioMenu
import ca.ilianokokoro.umihi.music.ui.components.studio.StudioMenuItem
import ca.ilianokokoro.umihi.music.ui.components.studio.AlertDialog
import ca.ilianokokoro.umihi.music.ui.components.studio.CircleButton
import ca.ilianokokoro.umihi.music.ui.components.studio.RELEASE_ACTION_GAP
import ca.ilianokokoro.umihi.music.ui.components.studio.TextButton

private const val SLEEVE_FRACTION = 0.76f

@Composable
fun PlaylistInfo(
    playlist: Playlist,
    isDownloading: Boolean,
    onDownloadPressed: () -> Unit,
    onDeleteDownloadPressed: () -> Unit,
    onDeletePressed: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier,
    onUpdateCoverPath: (String) -> Unit = {},
    /** What starts the music, placed first in the row of actions. */
    leadingActions: @Composable RowScope.() -> Unit = {},
    /** Offered in the menu when the playlist can start one. */
    onStartRadio: (() -> Unit)? = null,
) {
    val songsCount = playlist.songs.count()
    var animatedCount by remember { mutableStateOf<Int?>(null) }
    val showDeleteDownloadDialog = remember { mutableStateOf(false) }
    val showDeleteDialog = remember { mutableStateOf(false) }
    val showCancelDialog = remember { mutableStateOf(false) }
    var optionsExtended by remember { mutableStateOf(false) }

    LaunchedEffect(songsCount) {
        animatedCount = songsCount
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val specialCover = specialPlaylistCover(playlist.info.id)
        when {
            specialCover != null -> JelyvoPlaylistCover(
                cover = specialCover,
                contentDescription = stringResource(R.string.playlist_cover),
                modifier = Modifier.fillMaxWidth(SLEEVE_FRACTION)
                        .aspectRatio(1f),
            )
            !playlist.info.isDownloadedPlaylist -> {
                val launcher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri: Uri? ->
                    uri?.let { onUpdateCoverPath(it.toString()) }
                }
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .then(
                            if (playlist.info.isEditable) {
                                Modifier.clickable { launcher.launch("image/*") }
                            } else Modifier
                        ),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    SquareImage(
                        uri = playlist.info.coverPath ?: playlist.info.coverHref,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (playlist.info.isEditable) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.padding(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.edit),
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                }
            }
            else -> {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.download),
                    modifier = Modifier.size(220.dp)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val displayTitle = if (specialCover == SpecialPlaylistCover.Liked) {
                    stringResource(R.string.liked_songs)
                } else {
                    playlist.info.title
                }
                Text(
                    modifier = modifier
                        .fillMaxWidth(),
                    text = displayTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                val alpha by animateFloatAsState(
                    targetValue = if (animatedCount == null || animatedCount == 0) {
                        0f
                    } else {
                        1f
                    },
                    animationSpec = tween()
                )

                Text(
                    text = if (songsCount > 0) {
                        stringResource(R.string.songs, songsCount)
                    } else {
                        ""
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(alpha)
                )


                Row(
                        horizontalArrangement = Arrangement.spacedBy(RELEASE_ACTION_GAP, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                    leadingActions()
                    if (!playlist.info.isDownloadedPlaylist) {

                        CircleButton(
                            onClick = {
                                if (playlist.downloaded) {
                                    showDeleteDownloadDialog.value = true
                                } else if (isDownloading) {
                                    showCancelDialog.value = true
                                } else {
                                    onDownloadPressed()
                                }
                            },
                            enabled = alpha != 0F
                        ) {
                            if (playlist.downloaded) {
                                Icon(
                                    imageVector = Icons.Rounded.DownloadDone,
                                    contentDescription = null,
                                )
                            } else if (isDownloading) {
                                CircularWavyProgressIndicator(modifier = Modifier.size(25.dp))
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = stringResource(R.string.download),
                                )
                            }
                        }


            CircleButton(onClick = { optionsExtended = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = stringResource(R.string.actions)
                            )

                            StudioMenu(
                                expanded = optionsExtended,
                                onDismissRequest = { optionsExtended = false },
                            ) {
                                onStartRadio?.let { startRadio ->
                                    StudioMenuItem(
                                        leadingIcon = Icons.Rounded.Radio,
                                        text = stringResource(R.string.start_radio),
                                        onClick = {
                                            startRadio()
                                            optionsExtended = false
                                        }
                                    )
                                }

                                if (isDownloading) {
                                    StudioMenuItem(
                                        leadingIcon = Icons.Rounded.Cancel,
                                        text = stringResource(R.string.cancel_download),
                                        onClick = {
                                            showCancelDialog.value = true
                                            optionsExtended = false
                                        }
                                    )
                                } else if (!playlist.downloaded) {
                                    StudioMenuItem(
                                        leadingIcon = Icons.Rounded.Download,
                                        text = stringResource(R.string.download),
                                        onClick = {
                                            onDownloadPressed()
                                            optionsExtended = false
                                        }
                                    )
                                }

                                // Nothing to remove when no track is stored locally, and the
                                // account playlists we do not own reject deletion outright.
                                if (playlist.songs.any { it.downloaded }) {
                                    StudioMenuItem(
                                        leadingIcon = Icons.Rounded.FileDownloadOff,
                                        text = stringResource(R.string.remove_download),
                                        onClick = {
                                            showDeleteDownloadDialog.value = true
                                            optionsExtended = false
                                        }
                                    )
                                }

                                if (playlist.info.isEditable) {
                                    StudioMenuItem(
                                        leadingIcon = Icons.Rounded.Delete,
                                        text = stringResource(R.string.delete_playlist),
                                        onClick = {
                                            showDeleteDialog.value = true
                                            optionsExtended = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteDownloadDialog.value) {
        AlertDialog(
            onDismissRequest = { showDeleteDownloadDialog.value = false },
            title = { Text(stringResource(R.string.remove_local_playlist)) },
            text = { Text(stringResource(R.string.remove_local_confirm_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDownloadDialog.value = false
                        onDeleteDownloadPressed()
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDownloadDialog.value = false }
                ) { Text(stringResource(R.string.cancel)) }
            },
            properties = DialogProperties(dismissOnClickOutside = false)
        )
    }

    if (showCancelDialog.value) {
        AlertDialog(
            onDismissRequest = { showCancelDialog.value = false },
            title = { Text(stringResource(R.string.cancel_playlist_download)) },
            text = { Text(stringResource(R.string.cancel_playlist_download_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog.value = false
                        onCancelDownload()
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCancelDialog.value = false }
                ) { Text(stringResource(R.string.cancel)) }
            },
            properties = DialogProperties(dismissOnClickOutside = false)
        )
    }

    if (showDeleteDialog.value) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog.value = false },
            title = { Text(stringResource(R.string.delete_playlist)) },
            text = { Text(stringResource(R.string.delete_playlist_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog.value = false
                        onDeletePressed()
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog.value = false }
                ) { Text(stringResource(R.string.cancel)) }
            },
            properties = DialogProperties(dismissOnClickOutside = false)
        )
    }


}
