package ca.ilianokokoro.umihi.music.ui.components.library

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.data.repositories.LocalPlaylistRepository
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.mosaicCoverUris
import ca.ilianokokoro.umihi.music.ui.components.keyboardFocusOutline
import ca.ilianokokoro.umihi.music.ui.components.playlist.PlaylistMosaicCover
import ca.ilianokokoro.umihi.music.ui.components.songDragAndDropTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class LibraryPanePlaylist(
    val id: String,
    val name: String,
    val songCount: Int,
    val coverUris: List<String>,
)

/**
 * App-wide quick access to editable, on-device playlists for very wide desktop windows.
 *
 * Every row is also a drop destination. Account playlists stay out of this pane because they do
 * not expose the same reliable local mutation contract.
 */
@Composable
fun PermanentLibraryPane(
    application: Application,
    visible: Boolean,
    onOpenLibrary: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = application.applicationContext
    val repository = remember(application) { LocalPlaylistRepository(application) }
    val scope = rememberCoroutineScope()
    val playlistItemsFlow = remember(repository) {
        repository.observeAll().map { playlists ->
            withContext(Dispatchers.Default) {
                playlists.map { playlist ->
                    val songs = repository.songsOf(playlist)
                    LibraryPanePlaylist(
                        id = playlist.id,
                        name = playlist.name,
                        songCount = songs.size,
                        coverUris = songs.mosaicCoverUris(),
                    )
                }
            }
        }
    }
    val playlists by playlistItemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .width(284.dp)
                .fillMaxHeight()
                .padding(start = 8.dp, top = 12.dp, bottom = 12.dp)
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Vertical)
                ),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                ) {
                    LibraryHeader(onClick = onOpenLibrary)

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )

                    Text(
                        text = stringResource(R.string.on_device_playlists),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )

                    if (playlists.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.no_playlists),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(
                                items = playlists,
                                key = { playlist -> playlist.id },
                            ) { playlist ->
                                LibraryPlaylistRow(
                                    playlist = playlist,
                                    onClick = { onOpenPlaylist(playlist.id) },
                                    onSongDropped = { song ->
                                        scope.launch {
                                            val message = try {
                                                if (repository.addSong(playlist.id, song)) {
                                                    R.string.song_added_to_playlist
                                                } else {
                                                    R.string.song_already_in_playlist
                                                }
                                            } catch (_: Exception) {
                                                R.string.playlist_update_failed
                                            }
                                            Toast.makeText(
                                                context,
                                                context.getString(message),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .keyboardFocusOutline(shape),
        shape = shape,
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Rounded.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                )
            }
            Text(
                text = stringResource(R.string.library),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryPlaylistRow(
    playlist: LibraryPanePlaylist,
    onClick: () -> Unit,
    onSongDropped: (Song) -> Unit,
) {
    var isDropHovered by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val containerColor = if (isDropHovered) {
        lerp(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            0.18f,
        )
    } else {
        Color.Transparent
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .songDragAndDropTarget(
                onSongDropped = { song ->
                    onSongDropped(song)
                    true
                },
                onHoverChanged = { isDropHovered = it },
            )
            .keyboardFocusOutline(shape),
        shape = shape,
        color = containerColor,
        border = if (isDropHovered) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(52.dp),
                contentAlignment = Alignment.Center,
            ) {
                PlaylistMosaicCover(
                    uris = playlist.coverUris,
                    contentDescription = stringResource(R.string.playlist_cover),
                    cornerRadius = 12.dp,
                    modifier = Modifier.fillMaxSize(),
                )
                if (playlist.coverUris.isEmpty()) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (isDropHovered) {
                        stringResource(R.string.drop_to_playlist)
                    } else {
                        pluralStringResource(
                            R.plurals.song_count,
                            playlist.songCount,
                            playlist.songCount,
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDropHovered) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isDropHovered) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Spacer(Modifier.size(24.dp))
            }
        }
    }
}
