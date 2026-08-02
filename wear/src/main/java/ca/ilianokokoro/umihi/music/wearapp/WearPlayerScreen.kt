package ca.ilianokokoro.umihi.music.wearapp

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import ca.ilianokokoro.umihi.music.wear.WearPlaybackSnapshot
import ca.ilianokokoro.umihi.music.wear.WearQueueItem
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class WearPlayerActions(
    val previous: () -> Unit,
    val togglePlayback: () -> Unit,
    val next: () -> Unit,
    val seekBack: () -> Unit,
    val seekForward: () -> Unit,
    val toggleLike: () -> Unit,
    val toggleShuffle: () -> Unit,
    val cycleRepeat: () -> Unit,
    val volumeDown: () -> Unit,
    val volumeUp: () -> Unit,
    val playQueueIndex: (Int) -> Unit,
    val clearError: () -> Unit,
)

@Composable
fun JelyvoWearApp(
    state: WearUiState,
    isAmbient: Boolean,
    actions: WearPlayerActions,
) {
    val playback = state.playback
    Box(Modifier.fillMaxSize().background(Background)) {
        if (isAmbient && playback.hasMedia) {
            AmbientPlayback(playback)
        } else if (playback.hasMedia) {
            PlayerPager(state, actions)
        } else {
            EmptyContent(state.phoneConnected, state.loading)
        }

        AnimatedVisibility(
            visible = state.commandError != null,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            ErrorPill(state.commandError)
        }
        LaunchedEffect(state.commandError) {
            if (state.commandError != null) {
                delay(3_500)
                actions.clearError()
            }
        }
    }
}

@Composable
private fun PlayerPager(state: WearUiState, actions: WearPlayerActions) {
    val playback = state.playback
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    Box(Modifier.fillMaxSize()) {
        if (playback.artworkUri.isNotBlank()) {
            AsyncImage(
                model = playback.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().drawWithContent {
                    drawContent()
                    drawRect(Color.Black.copy(alpha = 0.78f))
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.18f),
                            1f to Color.Black.copy(alpha = 0.94f),
                        ),
                    )
                },
            )
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                PAGE_PLAYER -> NowPlayingPage(state, actions)
                PAGE_CONTROLS -> ControlsPage(state, actions)
                else -> QueuePage(playback, state.phoneConnected, actions.playQueueIndex)
            }
        }

        PageIndicator(
            page = pagerState.currentPage,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp),
        )
    }
}

@Composable
private fun NowPlayingPage(state: WearUiState, actions: WearPlayerActions) {
    val playback = state.playback
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConnectionLabel(state.phoneConnected)
        Spacer(Modifier.height(4.dp))
        ArtworkWithProgress(playback)
        Spacer(Modifier.height(5.dp))
        Text(
            text = playback.title.ifBlank { stringResource(R.string.unknown_title) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = playback.artist.ifBlank { stringResource(R.string.unknown_artist) },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MutedText,
            fontSize = 11.sp,
            lineHeight = 13.sp,
        )
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundActionButton(
                enabled = state.phoneConnected && playback.hasPrevious,
                onClick = actions.previous,
                size = 40,
                description = stringResource(R.string.previous_track),
            ) { Icon(Icons.Filled.SkipPrevious, null, tint = Color.White) }
            Spacer(Modifier.width(10.dp))
            RoundActionButton(
                enabled = state.phoneConnected,
                onClick = actions.togglePlayback,
                size = 54,
                emphasized = true,
                description = stringResource(if (playback.isPlaying) R.string.pause else R.string.play),
            ) {
                AnimatedContent(playback.isPlaying, label = "play-pause") { playing ->
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null,
                        tint = BrandPlum,
                        modifier = Modifier.size(29.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            RoundActionButton(
                enabled = state.phoneConnected && playback.hasNext,
                onClick = actions.next,
                size = 40,
                description = stringResource(R.string.next_track),
            ) { Icon(Icons.Filled.SkipNext, null, tint = Color.White) }
        }
    }
}

@Composable
private fun ControlsPage(state: WearUiState, actions: WearPlayerActions) {
    val playback = state.playback
    val enabled = state.phoneConnected
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SectionTitle(stringResource(R.string.controls))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledAction(
                icon = if (playback.isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = stringResource(if (playback.isLiked) R.string.liked else R.string.like),
                active = playback.isLiked,
                activeContainer = BrandPlum,
                enabled = enabled,
                onClick = actions.toggleLike,
            )
            LabeledAction(
                icon = Icons.Filled.Shuffle,
                label = stringResource(R.string.shuffle),
                active = playback.shuffleEnabled,
                activeContainer = SecondaryContainer,
                enabled = enabled,
                onClick = actions.toggleShuffle,
            )
            val repeatLabel = when (playback.repeatMode) {
                REPEAT_MODE_ALL -> R.string.repeat_all
                REPEAT_MODE_ONE -> R.string.repeat_one
                else -> R.string.repeat_off
            }
            LabeledAction(
                icon = if (playback.repeatMode == REPEAT_MODE_ONE) {
                    Icons.Filled.RepeatOne
                } else {
                    Icons.Filled.Repeat
                },
                label = stringResource(repeatLabel),
                active = playback.repeatMode != REPEAT_MODE_OFF,
                activeContainer = TertiaryContainer,
                enabled = enabled,
                onClick = actions.cycleRepeat,
            )
        }
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(17.dp)) {
            LabeledAction(
                icon = Icons.Filled.Replay10,
                label = stringResource(R.string.seek_back),
                enabled = enabled,
                onClick = actions.seekBack,
            )
            LabeledAction(
                icon = Icons.Filled.Forward10,
                label = stringResource(R.string.seek_forward),
                enabled = enabled,
                onClick = actions.seekForward,
            )
        }
        Spacer(Modifier.weight(1f))
        VolumeControl(playback, enabled, actions.volumeDown, actions.volumeUp)
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
private fun QueuePage(
    playback: WearPlaybackSnapshot,
    connected: Boolean,
    onPlayQueueIndex: (Int) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (playback.queueIndex - 1).coerceAtLeast(0),
    )
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onRotaryScrollEvent { event ->
                scope.launch { listState.scrollBy(event.verticalScrollPixels) }
                true
            },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 17.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        item {
            SectionTitle(stringResource(R.string.queue))
            Spacer(Modifier.height(4.dp))
        }
        if (playback.queue.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.queue_empty),
                    color = MutedText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                )
            }
        } else {
            itemsIndexed(
                items = playback.queue,
                key = { index, item -> "${item.mediaId}:$index" },
            ) { index, item ->
                QueueRow(
                    item = item,
                    active = index == playback.queueIndex,
                    enabled = connected,
                    onClick = { onPlayQueueIndex(playback.queueOffset + index) },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(item: WearQueueItem, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) BrandPlum.copy(alpha = 0.72f) else SurfaceContainer)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(37.dp).clip(CircleShape).background(SurfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = item.artworkUri.takeIf(String::isNotBlank),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.artworkUri.isBlank()) {
                Icon(Icons.Filled.MusicNote, null, tint = MutedText, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title.ifBlank { stringResource(R.string.unknown_title) },
                color = if (active) Color.White else Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.artist.ifBlank { stringResource(R.string.unknown_artist) },
                color = MutedText,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (active) {
            Text("•", color = BrandCoral, fontSize = 19.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun ArtworkWithProgress(playback: WearPlaybackSnapshot) {
    val timed = rememberPlaybackTiming(playback)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
        AsyncImage(
            model = playback.artworkUri.takeIf(String::isNotBlank),
            contentDescription = stringResource(R.string.artwork_description, playback.title),
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(85.dp).clip(CircleShape).background(SurfaceContainer),
        )
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                Color.White.copy(alpha = 0.18f), -90f, 360f, false,
                Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                BrandCoral, -90f, 360f * timed.fraction, false,
                Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun VolumeControl(
    playback: WearPlaybackSnapshot,
    enabled: Boolean,
    onDown: () -> Unit,
    onUp: () -> Unit,
) {
    val fraction = if (playback.maxDeviceVolume > 0) {
        playback.deviceVolume.toFloat() / playback.maxDeviceVolume
    } else 0f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
    ) {
        RoundActionButton(
            enabled = enabled && playback.deviceVolume > 0,
            onClick = onDown,
            size = 38,
            description = stringResource(R.string.volume_down),
        ) { Icon(Icons.AutoMirrored.Filled.VolumeDown, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.volume), color = MutedText, fontSize = 9.sp)
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.15f), CircleShape)) {
                Box(
                    Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp)
                        .background(Primary, CircleShape),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        RoundActionButton(
            enabled = enabled && playback.deviceVolume < playback.maxDeviceVolume,
            onClick = onUp,
            size = 38,
            description = stringResource(R.string.volume_up),
        ) { Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
    }
}

@Composable
private fun LabeledAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean = false,
    activeContainer: Color = BrandPlum,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    RoundActionButton(
        enabled = enabled,
        onClick = onClick,
        size = 44,
        containerColor = activeContainer.takeIf { active },
        description = label,
    ) { Icon(icon, null, tint = OnSurface, modifier = Modifier.size(21.dp)) }
}

@Composable
private fun RoundActionButton(
    enabled: Boolean,
    onClick: () -> Unit,
    size: Int,
    description: String,
    emphasized: Boolean = false,
    containerColor: Color? = null,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(
                when {
                    !enabled -> Color.White.copy(alpha = 0.06f)
                    containerColor != null -> containerColor
                    emphasized -> IconCream
                    else -> SurfaceContainerHigh
                },
                CircleShape,
            )
            .clickable(enabled = enabled, onClickLabel = description) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ConnectionLabel(connected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).background(if (connected) BrandCoral else MutedText, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(
            stringResource(if (connected) R.string.connected else R.string.offline).uppercase(),
            color = MutedText,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PageIndicator(page: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(PAGE_COUNT) { index ->
            Box(
                Modifier
                    .size(if (index == page) 5.dp else 3.dp)
                    .background(
                        if (index == page) Color.White else Color.White.copy(alpha = 0.35f),
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun EmptyContent(connected: Boolean, loading: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier.size(72.dp).background(IconCream, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_jelyvo_watch),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(62.dp),
            )
        }
        Spacer(Modifier.height(13.dp))
        Text(
            stringResource(
                when {
                    loading -> R.string.connecting
                    !connected -> R.string.phone_unavailable
                    else -> R.string.nothing_playing
                },
            ),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            stringResource(if (connected) R.string.start_on_phone else R.string.bring_phone_closer),
            color = MutedText,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorPill(reason: String?) {
    val message = stringResource(
        when (reason) {
            "login_required" -> R.string.login_required
            "phone_unavailable" -> R.string.phone_unavailable
            "send_failed" -> R.string.send_failed
            "no_media" -> R.string.no_media
            "unsupported_command" -> R.string.unsupported_command
            else -> R.string.action_failed
        },
    )
    Text(
        message,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        modifier = Modifier
            .padding(top = 8.dp, start = 25.dp, end = 25.dp)
            .background(Color(0xFFD23F34), RoundedCornerShape(14.dp))
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

@Composable
private fun AmbientPlayback(playback: WearPlaybackSnapshot) {
    val timed = rememberPlaybackTiming(playback)
    var clock by remember { androidx.compose.runtime.mutableStateOf(currentClock()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            clock = currentClock()
        }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 29.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(clock, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                playback.title.ifBlank { stringResource(R.string.unknown_title) },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                playback.artist.ifBlank { stringResource(R.string.unknown_artist) },
                color = MutedText,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.16f))) {
                Box(Modifier.fillMaxWidth(timed.fraction).height(2.dp).background(Color.White.copy(alpha = 0.75f)))
            }
            Spacer(Modifier.height(5.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(timed.positionMs), color = MutedText, fontSize = 9.sp)
                Text(formatTime(playback.durationMs), color = MutedText, fontSize = 9.sp)
            }
        }
    }
}

private data class PlaybackTiming(val positionMs: Long, val fraction: Float)

@Composable
private fun rememberPlaybackTiming(playback: WearPlaybackSnapshot): PlaybackTiming {
    var nowMs by remember(playback.updatedAtMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playback.isPlaying, playback.updatedAtMs) {
        while (playback.isPlaying) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }
    val position = playback.estimatedPositionMs(nowMs)
    val fraction = if (playback.durationMs > 0L) {
        (position.toFloat() / playback.durationMs).coerceIn(0f, 1f)
    } else 0f
    return PlaybackTiming(position, fraction)
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1_000L)
    return "%d:%02d".format(Locale.getDefault(), totalSeconds / 60, totalSeconds % 60)
}

private fun currentClock(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

private const val PAGE_PLAYER = 0
private const val PAGE_CONTROLS = 1
private const val PAGE_COUNT = 3
private const val REPEAT_MODE_OFF = 0
private const val REPEAT_MODE_ONE = 1
private const val REPEAT_MODE_ALL = 2
private val Background = Color(0xFF151110)
private val OnSurface = Color(0xFFF2DFDB)
private val MutedText = Color(0xFFDCC1BC)
private val Primary = Color(0xFFFFB4A8)
private val BrandCoral = Color(0xFFF47B67)
private val BrandPlum = Color(0xFF74304F)
private val SecondaryContainer = Color(0xFF5D3F3A)
private val TertiaryContainer = Color(0xFF52452F)
private val SurfaceContainer = Color(0xFF221B1A)
private val SurfaceContainerHigh = Color(0xFF2D2523)
private val IconCream = Color(0xFFFFF8ED)
