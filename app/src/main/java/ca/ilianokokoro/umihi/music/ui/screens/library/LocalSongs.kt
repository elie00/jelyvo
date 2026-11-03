package ca.ilianokokoro.umihi.music.ui.screens.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.components.PlayShuffleRow
import ca.ilianokokoro.umihi.music.ui.components.studio.ListItem
import ca.ilianokokoro.umihi.music.ui.components.studio.PillSegmentedControl
import java.util.Locale

/** The tracks of one directory on the device, under its last path segment. */
internal data class LocalFolder(
    val path: String,
    val name: String,
    val songs: List<Song>,
)

/** "Music/Albums/Discovery/" and "/storage/emulated/0/Music/a.mp3" both name a directory. */
internal fun folderPathOf(relativePath: String?, filePath: String?): String =
    relativePath?.takeIf(String::isNotBlank)?.trimEnd('/')
        ?: filePath?.substringBeforeLast('/', "").orEmpty()

/** Folders by name, the tracks inside each in the order they came. Tracks with no path stay together. */
internal fun groupByFolder(songs: List<Song>): List<LocalFolder> =
    songs.groupBy { it.localFolder.orEmpty() }
        .map { (path, tracks) -> LocalFolder(path, path.substringAfterLast('/'), tracks) }
        .sortedBy { it.name.lowercase(Locale.ROOT) }

/** A folder comes back from its path, so reopening survives a rescan that rebuilt the list. */
internal fun folderAt(folders: List<LocalFolder>, path: String?): LocalFolder? =
    path?.let { wanted -> folders.firstOrNull { it.path == wanted } }

@Composable
internal fun LocalSongsContent(
    songs: List<Song>,
    onOpenPlayer: () -> Unit,
    topPadding: Dp,
    scrollEffect: @Composable (LazyListState) -> Unit = {},
) {
    val folders = remember(songs) { groupByFolder(songs) }
    var byFolder by rememberSaveable { mutableStateOf(false) }
    var openPath by rememberSaveable { mutableStateOf<String?>(null) }
    val open = folderAt(folders, openPath)
    // One folder would make the choice an empty one.
    val offersFolders = folders.size > 1

    BackHandler(enabled = open != null) { openPath = null }

    val sourceName = open?.name?.ifBlank { null } ?: stringResource(R.string.on_device)
    val shown = open?.songs ?: songs
    val listState = rememberLazyListState()
    scrollEffect(listState)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            top = topPadding,
            bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
        ),
    ) {
        if (offersFolders && open == null) {
            item(key = "view") {
                PillSegmentedControl(
                    options = listOf(
                        stringResource(R.string.local_view_songs),
                        stringResource(R.string.local_view_folders),
                    ),
                    selectedIndex = if (byFolder) 1 else 0,
                    onSelect = { byFolder = it == 1 },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (open != null) {
            item(key = "folder-header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BackButton(onBack = { openPath = null })
                    Text(
                        text = sourceName,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (byFolder && open == null && offersFolders) {
            items(items = folders, key = { "folder:${it.path}" }) { folder ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = folder.name.ifBlank { stringResource(R.string.on_device) },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    supportingContent = {
                        Text(
                            pluralStringResource(R.plurals.song_count, folder.songs.size, folder.songs.size)
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Rounded.Folder, contentDescription = null)
                    },
                    modifier = Modifier.clickable { openPath = folder.path },
                )
            }
        } else {
            item(key = "play-shuffle") {
                PlayShuffleRow(
                    onPlay = {
                        onOpenPlayer()
                        PlayerManager.playQueue(shown.map { it.mediaItem }, sourceName = sourceName)
                    },
                    onShuffle = {
                        onOpenPlayer()
                        PlayerManager.playQueue(
                            shown.shuffled().map { it.mediaItem },
                            sourceName = sourceName,
                        )
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            items(items = shown, key = { it.youtubeId }) { song ->
                LocalSongListItem(
                    song = song,
                    songs = shown,
                    onOpenPlayer = onOpenPlayer,
                    sourceName = sourceName,
                )
            }
        }
    }
}
