package ca.ilianokokoro.umihi.music.ui.components.miniplayer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import ca.ilianokokoro.umihi.music.ui.components.studio.FilledIconButton
import ca.ilianokokoro.umihi.music.ui.components.studio.HorizontalDivider
import androidx.compose.material3.Icon
import ca.ilianokokoro.umihi.music.ui.components.studio.ListItem
import ca.ilianokokoro.umihi.music.ui.components.studio.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.ui.components.studio.CircleButton
import ca.ilianokokoro.umihi.music.core.domain.PlaybackStatus
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.core.playback.Media3PlaybackEngine
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.adapters.toSong
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.song.SongThumbnail
import ca.ilianokokoro.umihi.music.ui.components.song.songSubtitle
import ca.ilianokokoro.umihi.music.ui.components.songDragAndDropSource
import ca.ilianokokoro.umihi.music.ui.components.songDragAndDropTarget
import kotlinx.coroutines.delay

private const val PLAYBACK_PANE_PROGRESS_REFRESH_MS = 500L

/**
 * Lightweight app-wide playback companion for desktop-sized windows.
 *
 * It intentionally observes the playback engine directly instead of creating the full player
 * ViewModel, so browsing does not trigger lyrics, video-variant or stream-prewarming work in the
 * background.
 */
@Composable
fun PermanentPlaybackPane(
    visible: Boolean,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playbackState by Media3PlaybackEngine.state.collectAsStateWithLifecycle()
    val playbackQueue by Media3PlaybackEngine.queue.collectAsStateWithLifecycle()
    val currentSong = remember(playbackState.currentTrack) {
        playbackState.currentTrack?.toSong()
    }
    val isPlaying = playbackState.isPlaying
    val isLoading = playbackState.status == PlaybackStatus.BUFFERING
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var isDropHovered by remember { mutableStateOf(false) }

    LaunchedEffect(playbackState.currentTrack?.id, isPlaying, visible) {
        while (true) {
            progressFraction = Media3PlaybackEngine.progress()?.fraction ?: 0f
            if (!visible || !isPlaying) break
            delay(PLAYBACK_PANE_PROGRESS_REFRESH_MS)
        }
    }

    val upNext = remember(playbackQueue) {
        playbackQueue.upNext().map { (index, entry) ->
            UpNextRow(index = index, entryId = entry.entryId, song = entry.track.toSong())
        }
    }

    AnimatedVisibility(
        visible = visible && currentSong != null,
        enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
        exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
        modifier = modifier,
    ) {
        val song = currentSong ?: return@AnimatedVisibility

        Box(
            modifier = Modifier
                .width(332.dp)
                .fillMaxHeight()
                .padding(top = 12.dp, end = 12.dp, bottom = 12.dp)
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Vertical)
                )
                .songDragAndDropTarget(
                    onSongDropped = { droppedSong ->
                        PlayerManager.addToQueue(droppedSong, context)
                        true
                    },
                    onHoverChanged = { isDropHovered = it },
                ),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SquareImage(
                        uri = song.thumbnailPath ?: song.thumbnailHref,
                        contentDescription = stringResource(R.string.album_artwork),
                        cornerRadius = 22.dp,
                        requestSize = 320.dp,
                        modifier = artworkModifier
                            .fillMaxWidth()
                            .songDragAndDropSource(song)
                            .clickable(onClick = onOpenPlayer),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenPlayer),
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
                    }

                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(
                            12.dp,
                            Alignment.CenterHorizontally,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledIconButton(
                            onClick = Media3PlaybackEngine::skipPrevious,
                            enabled = !isLoading,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = stringResource(R.string.previous),
                            )
                        }
                        CircleButton(
                            onClick = {
                                if (isPlaying) {
                                    Media3PlaybackEngine.pause()
                                } else {
                                    Media3PlaybackEngine.play()
                                }
                            },
                            size = 56.dp,
                            enabled = !isLoading,
                            filled = true,
                        ) {
                            Icon(
                                imageVector = if (isPlaying) {
                                    Icons.Rounded.Pause
                                } else {
                                    Icons.Rounded.PlayArrow
                                },
                                contentDescription = stringResource(
                                    if (isPlaying) R.string.pause else R.string.play
                                ),
                            )
                        }
                        FilledIconButton(
                            onClick = Media3PlaybackEngine::skipNext,
                            enabled = !isLoading,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = stringResource(R.string.next),
                            )
                        }
                    }

                    HorizontalDivider()

                    Text(
                        text = stringResource(R.string.queue_section_up_next),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    if (upNext.isEmpty()) {
                        Text(
                            text = stringResource(R.string.queue_up_next_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(
                                items = upNext,
                                key = UpNextRow::entryId,
                            ) { entry ->
                                ListItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .songDragAndDropSource(entry.song)
                                        .clickable {
                                            Media3PlaybackEngine.seekToIndex(entry.index)
                                            Media3PlaybackEngine.play()
                                        },
                                    leadingContent = {
                                        SongThumbnail(song = entry.song, size = 44.dp)
                                    },
                                    supportingContent = {
                                        Text(
                                            text = songSubtitle(entry.song),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                ) {
                                    Text(
                                        text = entry.song.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isDropHovered) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            12.dp,
                            Alignment.CenterVertically,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                        )
                        Text(
                            text = stringResource(R.string.drop_to_queue),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
            }
        }
    }
}

/** Up-next row with its queue index for seeking and a display [Song] bridged from the track. */
private data class UpNextRow(
    val index: Int,
    val entryId: String,
    val song: Song,
)
