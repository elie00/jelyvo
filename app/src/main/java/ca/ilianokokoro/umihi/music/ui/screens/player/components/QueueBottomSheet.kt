package ca.ilianokokoro.umihi.music.ui.screens.player.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.core.managers.QueueProvenance
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.song.QueueSongListItem
import ca.ilianokokoro.umihi.music.ui.components.studio.AlertDialog
import ca.ilianokokoro.umihi.music.ui.components.studio.CircleButton
import ca.ilianokokoro.umihi.music.ui.components.studio.ModalBottomSheet
import ca.ilianokokoro.umihi.music.ui.components.studio.OutlinedTextField
import ca.ilianokokoro.umihi.music.ui.components.studio.Switch
import ca.ilianokokoro.umihi.music.ui.components.studio.TextButton
import ca.ilianokokoro.umihi.music.ui.components.studio.sheetTitleStyle
import ca.ilianokokoro.umihi.music.ui.screens.player.moveWithinUserQueue
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState


@Composable
fun QueueBottomSheet(
    changeVisibility: (visible: Boolean) -> Unit,
    songs: List<Song>,
    currentIndex: Int,
    isSavingQueue: Boolean = false,
    smartTransitions: Boolean = true,
    onSmartTransitionsChange: (Boolean) -> Unit = {},
    onOpenSource: (QueueProvenance) -> Unit = {},
    onSaveQueue: (name: String, songs: List<Song>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(onDismissRequest = { changeVisibility(false) }) {
        QueuePanel(
            songs = songs,
            currentIndex = currentIndex,
            isSavingQueue = isSavingQueue,
            smartTransitions = smartTransitions,
            onSmartTransitionsChange = onSmartTransitionsChange,
            onOpenSource = onOpenSource,
            onSaveQueue = onSaveQueue,
            modifier = modifier,
        )
    }
}

/**
 * Queue content shared by the compact modal sheet and the permanent expanded player pane.
 */
@Composable
fun QueuePanel(
    songs: List<Song>,
    currentIndex: Int,
    isSavingQueue: Boolean = false,
    smartTransitions: Boolean = true,
    onSmartTransitionsChange: (Boolean) -> Unit = {},
    onOpenSource: (QueueProvenance) -> Unit = {},
    onSaveQueue: (name: String, songs: List<Song>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current

    var mutableSongList by remember(songs) { mutableStateOf(songs) }
    var startIndex by remember { mutableIntStateOf(0) }
    var isSaveDialogShown by remember { mutableStateOf(false) }
    val sourceName by PlayerManager.queueSourceName.collectAsStateWithLifecycle()
    val isRadio by PlayerManager.isRadioQueue.collectAsStateWithLifecycle()
    val source by PlayerManager.queueProvenance.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()
    // Reorder is limited to the user part of the queue: autoplay suggestions keep autoplay's
    // order, so they are neither draggable (no handle) nor valid drop targets (clamped move).
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val moved = moveWithinUserQueue(mutableSongList, from.index, to.index) { it.isSuggestion }
        if (moved !== mutableSongList) {
            mutableSongList = moved
            hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }

    LaunchedEffect(Unit) {
        this.launch {
            val indexToScroll =
                PlayerManager.currentController?.currentMediaItemIndex ?: return@launch

            lazyListState.animateScrollToItem(index = indexToScroll)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.queue),
                        style = sheetTitleStyle(),
                    )
                    // Queue provenance, mirroring YT Music: the source collection's name when the
                    // queue was built from one, "Radio" for a radio queue, nothing otherwise.
                    val provenance = when {
                        sourceName != null -> stringResource(R.string.playing_from, sourceName!!)
                        isRadio -> stringResource(R.string.radio)
                        else -> stringResource(R.string.playing_now)
                    }
                    Text(
                        text = provenance,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (source?.id != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.clickable(
                            enabled = source?.id != null,
                            onClick = { source?.let(onOpenSource) },
                        ),
                    )
                }
                Text(
                    text = pluralStringResource(
                        R.plurals.song_count,
                        mutableSongList.size,
                        mutableSongList.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CircleButton(
                    onClick = { isSaveDialogShown = true },
                    size = 40.dp,
                    enabled = mutableSongList.isNotEmpty() && !isSavingQueue,
                ) {
                    if (isSavingQueue) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = stringResource(R.string.save_queue),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.smart_transitions_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.smart_transitions_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = smartTransitions,
                    onCheckedChange = onSmartTransitionsChange,
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING
                ),
            ) {
                if (mutableSongList.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.queue_empty),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp)
                        )
                    }
                } else {
                    val firstSuggestionIndex = mutableSongList.indexOfFirst { it.isSuggestion }
                    val hasSuggestions = firstSuggestionIndex >= 0
                    itemsIndexed(
                        items = mutableSongList, key = { _, song -> song.uid }
                    ) { index, song ->
                        val itemScope = this
                        Column {
                            // Only split into sections once suggestions exist, and only show the
                            // "up next" header when there is at least one user-added item above them.
                            if (hasSuggestions && index == 0 && firstSuggestionIndex > 0) {
                                QueueSectionHeader(stringResource(R.string.queue_section_up_next))
                            }
                            if (hasSuggestions && index == firstSuggestionIndex) {
                                QueueSectionHeader(
                                    stringResource(R.string.queue_section_suggestions)
                                )
                            }
                            itemScope.ReorderableItem(
                                reorderableLazyListState,
                                key = song.uid
                            ) { isDragging ->
                                QueueSongListItem(
                                song = song,
                                isCurrentSong = song.uid == songs.getOrNull(currentIndex)?.uid,
                                isDraggable = !song.isSuggestion,
                                lifted = isDragging,
                                onPress = {
                                    PlayerManager.seekToIndex(index)
                                },
                                scope = this,
                                onDragStarted = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                    startIndex = mutableSongList.indexOf(song)
                                },
                                onDelete = {
                                    mutableSongList = mutableSongList.toMutableList().apply {
                                        removeAt(index)
                                    }
                                    PlayerManager.removeMediaItem(index)
                                },
                                onDragStopped = {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                    PlayerManager.currentController?.moveMediaItem(
                                        startIndex,
                                        mutableSongList.indexOf(song)
                                    )
                                }
                                )
                            }
                        }
                    }
                }
            }
        }

    if (isSaveDialogShown) {
        SaveQueueDialog(
            initialName = sourceName?.takeIf(String::isNotBlank)
                ?: stringResource(R.string.my_queue),
            songCount = mutableSongList.size,
            onDismiss = { isSaveDialogShown = false },
            onConfirm = { name ->
                isSaveDialogShown = false
                onSaveQueue(name, mutableSongList)
            },
        )
    }
}

@Composable
private fun QueueSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp)
    )
}

@Composable
private fun SaveQueueDialog(
    initialName: String,
    songCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.save_queue_title),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.queue_save_description, songCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.save_queue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
