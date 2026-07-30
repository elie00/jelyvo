package ca.ilianokokoro.umihi.music.ui.components.song

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.data.repositories.LocalPlaylistRepository
import ca.ilianokokoro.umihi.music.models.LocalPlaylist
import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.screens.album.components.LocalPlaylistPickerDialog
import kotlinx.coroutines.launch

/**
 * Floating contextual actions for an ordered desktop song selection.
 *
 * Playlist persistence lives here so album and artist pages share the same duplicate-safe path and
 * neither screen has to grow a second picker implementation.
 */
@Composable
fun SongSelectionActionBar(
    songs: List<Song>,
    newPlaylistName: String,
    onPlay: () -> Unit,
    onAddToQueue: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val repository = remember(application) { LocalPlaylistRepository(application) }
    val scope = rememberCoroutineScope()
    val resolvedNewPlaylistName =
        newPlaylistName.ifBlank { stringResource(R.string.playlist_default_name) }
    var playlists by remember { mutableStateOf<List<LocalPlaylist>>(emptyList()) }
    var showPlaylistPicker by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = songs.isNotEmpty(),
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.94f),
        exit = fadeOut() + scaleOut(targetScale = 0.94f),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.songs_selected,
                        songs.size,
                        songs.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(
                    onClick = {
                        onPlay()
                        onClear()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.play_selection),
                    )
                }
                IconButton(
                    onClick = {
                        onAddToQueue()
                        onClear()
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        contentDescription = stringResource(R.string.add_selection_to_queue),
                    )
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            playlists = repository.getAll()
                            showPlaylistPicker = true
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = stringResource(R.string.add_selection_to_playlist),
                    )
                }
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.clear_selection),
                    )
                }
            }
        }
    }

    if (showPlaylistPicker) {
        LocalPlaylistPickerDialog(
            playlists = playlists,
            onPick = { playlist ->
                showPlaylistPicker = false
                scope.launch {
                    val added = repository.addSongs(playlist.id, songs)
                    Toast.makeText(
                        context,
                        if (added == 0) {
                            context.getString(R.string.selection_already_in_playlist)
                        } else {
                            context.resources.getQuantityString(
                                R.plurals.selected_added_to_playlist,
                                added,
                                added,
                                playlist.name,
                            )
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                    onClear()
                }
            },
            onCreateNew = {
                showPlaylistPicker = false
                scope.launch {
                    val distinctSongs = songs.distinctBy(Song::youtubeId)
                    repository.create(
                        resolvedNewPlaylistName,
                        distinctSongs.map(LocalPlaylistSong::fromSong),
                    )
                    Toast.makeText(
                        context,
                        context.resources.getQuantityString(
                            R.plurals.selected_added_to_playlist,
                            distinctSongs.size,
                            distinctSongs.size,
                            resolvedNewPlaylistName,
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                    onClear()
                }
            },
            onDismiss = { showPlaylistPicker = false },
        )
    }
}
