package ca.ilianokokoro.umihi.music.ui.screens.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.song.QueueSongListItem
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    changeVisibility: (visible: Boolean) -> Unit,
    songs: List<Song>,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current

    var mutableSongList by remember(songs) { mutableStateOf(songs) }
    var startIndex by remember { mutableIntStateOf(0) }

    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        mutableSongList = mutableSongList.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }

        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    LaunchedEffect(Unit) {
        this.launch {
            val indexToScroll =
                PlayerManager.currentController?.currentMediaItemIndex ?: return@launch

            lazyListState.animateScrollToItem(index = indexToScroll)
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            changeVisibility(false)
        },
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Expanded, SheetValue.Hidden),
        ),
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
        ) {
            val sourceName by PlayerManager.queueSourceName.collectAsStateWithLifecycle()
            val isRadio by PlayerManager.isRadioQueue.collectAsStateWithLifecycle()

            Column(modifier = modifier.padding(start = 8.dp, bottom = 12.dp)) {
                Text(
                    text = stringResource(R.string.playing_now),
                    style = MaterialTheme.typography.titleLarge
                )
                // Queue provenance, mirroring YT Music: the source collection's name when the
                // queue was built from one, "Radio" for a radio queue, nothing otherwise.
                val provenance = when {
                    sourceName != null -> stringResource(R.string.playing_from, sourceName!!)
                    isRadio -> stringResource(R.string.radio)
                    else -> null
                }
                if (provenance != null) {
                    Text(
                        text = provenance,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = lazyListState,
                contentPadding = PaddingValues(
                    start = 8.dp,
                    top = 8.dp,
                    end = 8.dp,
                    bottom = Constants.Ui.SCROLLABLE_BOTTOM_PADDING
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (mutableSongList.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.queue_empty),
                            textAlign = TextAlign.Center,
                            modifier = modifier
                                .fillMaxSize()
                        )
                    }
                } else {
                    val firstSuggestionIndex = mutableSongList.indexOfFirst { it.isSuggestion }
                    val hasSuggestions = firstSuggestionIndex >= 0
                    itemsIndexed(
                        items = mutableSongList, key = { _, song -> song.uid }
                    ) { index, song ->
                        val itemScope = this
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
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
                            ) { _ ->
                                QueueSongListItem(
                                song = song,
                                isCurrentSong = song.uid == songs.getOrNull(currentIndex)?.uid,
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
    }
}

@Composable
private fun QueueSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}