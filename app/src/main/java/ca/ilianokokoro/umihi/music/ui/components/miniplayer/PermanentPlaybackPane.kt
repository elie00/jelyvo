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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.extensions.toSong
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.song.SongThumbnail
import ca.ilianokokoro.umihi.music.ui.components.song.songSubtitle
import ca.ilianokokoro.umihi.music.ui.components.songDragAndDropTarget
import kotlinx.coroutines.delay

private const val PLAYBACK_PANE_PROGRESS_REFRESH_MS = 500L

/**
 * Lightweight app-wide playback companion for desktop-sized windows.
 *
 * It intentionally observes Media3 directly instead of creating the full player ViewModel, so
 * browsing does not trigger lyrics, video-variant or stream-prewarming work in the background.
 */
@Composable
fun PermanentPlaybackPane(
    visible: Boolean,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player by PlayerManager.controllerState.collectAsStateWithLifecycle()
    var currentSong by remember(player) {
        mutableStateOf(player?.currentMediaItem?.toSong())
    }
    var queue by remember(player) { mutableStateOf(PlayerManager.getQueue()) }
    var currentIndex by remember(player) {
        mutableIntStateOf(player?.currentMediaItemIndex ?: -1)
    }
    var isPlaying by remember(player) { mutableStateOf(player?.isPlaying == true) }
    var isLoading by remember(player) {
        mutableStateOf(player?.playbackState == Player.STATE_BUFFERING)
    }
    var progressFraction by remember(player) { mutableFloatStateOf(0f) }
    var isDropHovered by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        fun refreshTimeline() {
            queue = PlayerManager.getQueue()
            currentIndex = player?.currentMediaItemIndex ?: -1
            currentSong = player?.currentMediaItem?.toSong()
        }

        refreshTimeline()
        isPlaying = player?.isPlaying == true
        isLoading = player?.playbackState == Player.STATE_BUFFERING

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                refreshTimeline()
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                refreshTimeline()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                currentSong = player?.currentMediaItem?.toSong()
            }

            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = playbackState == Player.STATE_BUFFERING
            }

            override fun onPlayerError(error: PlaybackException) {
                isPlaying = false
                isLoading = false
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    LaunchedEffect(player, currentSong?.youtubeId, isPlaying, visible) {
        while (true) {
            val duration = player?.duration ?: 0L
            progressFraction = if (duration > 0L) {
                ((player?.currentPosition ?: 0L).toFloat() / duration.toFloat())
                    .coerceIn(0f, 1f)
            } else {
                0f
            }
            if (!visible || !isPlaying) break
            delay(PLAYBACK_PANE_PROGRESS_REFRESH_MS)
        }
    }

    val upNext = remember(queue, currentIndex) {
        upNextQueueEntries(queue, currentIndex)
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
                color = lerp(
                    MaterialTheme.colorScheme.surfaceContainer,
                    MaterialTheme.colorScheme.primaryContainer,
                    0.08f,
                ),
                tonalElevation = 2.dp,
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
                            onClick = PlayerManager::skipToPrevious,
                            enabled = !isLoading,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = stringResource(R.string.previous),
                            )
                        }
                        FilledIconButton(
                            onClick = {
                                if (player?.isPlaying == true) player?.pause() else player?.play()
                            },
                            enabled = !isLoading,
                            modifier = Modifier.size(56.dp),
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
                            onClick = PlayerManager::skipToNext,
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
                                key = { it.value.uid },
                            ) { entry ->
                                ListItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            PlayerManager.seekToIndex(entry.index)
                                            player?.play()
                                        },
                                    leadingContent = {
                                        SongThumbnail(song = entry.value, size = 44.dp)
                                    },
                                    supportingContent = {
                                        Text(
                                            text = songSubtitle(entry.value),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = Color.Transparent
                                    ),
                                ) {
                                    Text(
                                        text = entry.value.title,
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
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    tonalElevation = 6.dp,
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

/** Queue entries after the current item, retaining their controller indices for seeking. */
internal fun upNextQueueEntries(
    queue: List<Song>,
    currentIndex: Int,
): List<IndexedValue<Song>> {
    if (currentIndex !in queue.indices) return emptyList()
    return queue.withIndex().drop(currentIndex + 1)
}
