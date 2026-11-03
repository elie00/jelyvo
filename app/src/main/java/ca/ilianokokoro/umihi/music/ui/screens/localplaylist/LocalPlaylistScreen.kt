package ca.ilianokokoro.umihi.music.ui.screens.localplaylist

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import ca.ilianokokoro.umihi.music.ui.components.studio.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.models.mosaicCoverUris
import ca.ilianokokoro.umihi.music.ui.components.BackButton
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeleton
import ca.ilianokokoro.umihi.music.ui.components.MusicSkeletonLayout
import ca.ilianokokoro.umihi.music.ui.components.PageGutter
import ca.ilianokokoro.umihi.music.ui.components.PlayShuffleRow
import ca.ilianokokoro.umihi.music.ui.components.SongSortButton
import ca.ilianokokoro.umihi.music.ui.components.frosted
import ca.ilianokokoro.umihi.music.ui.components.frostedTopAppBarColors
import ca.ilianokokoro.umihi.music.ui.components.studio.StudioMenu
import ca.ilianokokoro.umihi.music.ui.components.studio.StudioMenuItem
import ca.ilianokokoro.umihi.music.ui.components.playlist.PlaylistMosaicCover
import ca.ilianokokoro.umihi.music.ui.components.studio.AlertDialog
import ca.ilianokokoro.umihi.music.ui.components.studio.OutlinedTextField
import ca.ilianokokoro.umihi.music.ui.components.studio.TextButton
import ca.ilianokokoro.umihi.music.ui.screens.localplaylist.components.EditableSongItem
import ca.ilianokokoro.umihi.music.ui.theme.ArtworkPageTheme
import ca.ilianokokoro.umihi.music.ui.theme.rememberArtworkPalette
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    playlistId: String,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    application: Application,
    modifier: Modifier = Modifier,
    viewModel: LocalPlaylistViewModel = viewModel(
        factory = LocalPlaylistViewModel.Factory(playlistId, application)
    ),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDescriptionDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.export(context, it) }
    }

    // Local, mutable copy so drag gestures stay smooth; persisted when a drag finishes.
    var items by remember(uiState.songs) { mutableStateOf(uiState.songs) }
    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // from/to are flat LazyColumn indices, and three header rows sit above the songs.
        items = moveByFlatIndex(items, from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    val coverUris = remember(items) { items.mosaicCoverUris() }

    // What the list draws. In manual order that is `items`, the live drag copy; otherwise the
    // filtered/sorted view of the stored list, which no drag can reach.
    val visibleSongs = remember(items, uiState.searchQuery, uiState.sort) {
        if (uiState.isManualOrder) items else uiState.visibleSongs
    }

    ArtworkPageTheme(rememberArtworkPalette(coverUris.firstOrNull())) {
    Box(Modifier.fillMaxSize()) {
        val hazeState = remember { HazeState() }
        Scaffold(topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.frosted(hazeState),
                colors = frostedTopAppBarColors(),
                title = {
                    Text(
                        uiState.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = { BackButton(onBack = onBack) },
                actions = {
                    IconButton(
                        onClick = { menuExpanded = true },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = stringResource(R.string.actions)
                        )

                        StudioMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            StudioMenuItem(
                                leadingIcon = Icons.Rounded.DriveFileRenameOutline,
                                text = stringResource(R.string.rename_playlist),
                                onClick = {
                                    menuExpanded = false
                                    showRenameDialog = true
                                }
                            )
                            StudioMenuItem(
                                leadingIcon = Icons.Rounded.Description,
                                text = stringResource(R.string.edit_description),
                                onClick = {
                                    menuExpanded = false
                                    showDescriptionDialog = true
                                }
                            )
                            StudioMenuItem(
                                leadingIcon = Icons.Rounded.Upload,
                                text = stringResource(R.string.export_playlist),
                                onClick = {
                                    menuExpanded = false
                                    exportLauncher.launch("${uiState.name}.json")
                                }
                            )
                            StudioMenuItem(
                                leadingIcon = Icons.Rounded.Delete,
                                text = stringResource(R.string.delete_playlist),
                                onClick = {
                                    menuExpanded = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }) { paddingValues ->
            // "Empty" is only true once the store has answered: until then the same message would
            // describe a playlist that is merely still loading, or one deleted from another screen.
            if (uiState.loading || uiState.notFound || items.isEmpty()) {
                Column(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (uiState.loading) {
                        MusicSkeleton(MusicSkeletonLayout.SongList)
                    } else {
                        Text(
                            stringResource(
                                if (uiState.notFound) {
                                    R.string.local_playlist_not_found
                                } else {
                                    R.string.empty_playlist
                                }
                            ),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .hazeSource(hazeState),
                    state = lazyListState,
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 8.dp,
                        bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "header") {
                        LocalPlaylistHeader(
                            description = uiState.description,
                            coverUris = coverUris,
                        )
                    }

                    // Unconditional like the other header rows, so SONG_LIST_HEADER_ITEMS stays
                    // exact: a header that came and went would silently shift every drag index.
                    item(key = "filter-sort") {
                        Column(modifier = Modifier.padding(horizontal = PageGutter)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedTextField(
                                    value = uiState.searchQuery,
                                    onValueChange = viewModel::onSearchQueryChange,
                                    singleLine = true,
                                    label = { Text(stringResource(R.string.filter_songs)) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Search,
                                            contentDescription = null,
                                        )
                                    },
                                    trailingIcon = if (uiState.searchQuery.isNotBlank()) {
                                        {
                                            IconButton(
                                                onClick = { viewModel.onSearchQueryChange("") }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = stringResource(
                                                        R.string.clear_filter
                                                    ),
                                                )
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SongSortButton(
                                    sort = uiState.sort,
                                    onSortChange = viewModel::onSortChange,
                                )
                            }
                            if (!uiState.isManualOrder) {
                                Text(
                                    text = stringResource(R.string.reorder_disabled_while_sorted),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }

                    item(key = "play-shuffle") {
                        PlayShuffleRow(
                            onPlay = {
                                onOpenPlayer()
                                viewModel.play()
                            },
                            onShuffle = {
                                onOpenPlayer()
                                viewModel.shuffle()
                            },
                            modifier = Modifier.padding(start = PageGutter, end = PageGutter, bottom = 8.dp),
                        )
                    }

                    if (uiState.searchQuery.isNotBlank() && visibleSongs.isEmpty()) {
                        item(key = "no-results") {
                            Text(
                                text = stringResource(R.string.no_results),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                            )
                        }
                    }

                    itemsIndexed(
                        items = visibleSongs,
                        key = { _, song -> song.uid },
                    ) { index, song ->
                        ReorderableItem(reorderableState, key = song.uid) { _ ->
                            EditableSongItem(
                                song = song,
                                scope = this,
                                onPress = {
                                    onOpenPlayer()
                                    viewModel.play(index)
                                },
                                onRemove = { viewModel.removeSong(song.uid) },
                                dragEnabled = uiState.isManualOrder,
                                onDragStarted = {
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.GestureThresholdActivate
                                    )
                                },
                                onDragStopped = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    viewModel.persistOrder(items)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(uiState.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_playlist)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.playlist_name)) },
                )
            },
            confirmButton = {
                TextButton(
                    // rename() drops a blank name, so confirming one would close the dialog and
                    // silently change nothing.
                    enabled = newName.isNotBlank(),
                    onClick = {
                        viewModel.rename(newName)
                        showRenameDialog = false
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDescriptionDialog) {
        var newDescription by remember { mutableStateOf(uiState.description) }
        AlertDialog(
            onDismissRequest = { showDescriptionDialog = false },
            title = { Text(stringResource(R.string.edit_description)) },
            text = {
                OutlinedTextField(
                    value = newDescription,
                    onValueChange = { newDescription = it },
                    label = { Text(stringResource(R.string.playlist_description)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Blank is accepted here, unlike rename: it clears the description.
                        viewModel.updateDescription(newDescription)
                        showDescriptionDialog = false
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDescriptionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_playlist)) },
            text = { Text(stringResource(R.string.delete_playlist_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete(onDeleted = onBack)
                    }
                ) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * Cover + description block above the play/shuffle row. Both parts are optional: a playlist with no
 * artwork and no note renders nothing at all rather than an empty gap.
 */
@Composable
private fun LocalPlaylistHeader(
    description: String,
    coverUris: List<String>,
) {
    if (coverUris.isEmpty() && description.isBlank()) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PageGutter, end = PageGutter, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (coverUris.isNotEmpty()) {
            PlaylistMosaicCover(
                uris = coverUris,
                modifier = Modifier.size(220.dp),
            )
        }
        if (description.isNotBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, start = 16.dp, end = 16.dp),
            )
        }
    }
}
