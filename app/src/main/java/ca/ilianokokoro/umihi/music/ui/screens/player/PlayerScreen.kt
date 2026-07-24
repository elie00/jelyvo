package ca.ilianokokoro.umihi.music.ui.screens.player

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.extensions.toTimeString
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.screens.player.components.LyricsPanel
import ca.ilianokokoro.umihi.music.ui.screens.player.components.PlayerArtworkBackground
import ca.ilianokokoro.umihi.music.ui.screens.player.components.PlayerControls
import ca.ilianokokoro.umihi.music.ui.screens.player.components.PlayerMenuBottomSheet
import ca.ilianokokoro.umihi.music.ui.screens.player.components.QueueBottomSheet
import ca.ilianokokoro.umihi.music.ui.screens.player.components.SleepTimerBottomSheet
import ca.ilianokokoro.umihi.music.ui.screens.player.components.SpeedSelectorBottomSheet
import ca.ilianokokoro.umihi.music.ui.theme.ArtworkPlayerTheme
import ca.ilianokokoro.umihi.music.ui.theme.nunitoFontFamily
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onOpenArtist: (Song) -> Unit = {},
    onOpenAlbum: (Song) -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
    application: Application,
    playerViewModel: PlayerViewModel = viewModel(
        factory =
            PlayerViewModel.Factory(application = application)
    )
) {
    val uiState = playerViewModel.uiState.collectAsStateWithLifecycle().value
    val playbackData = PlayerManager.audioInfo.collectAsStateWithLifecycle().value
    val orientation = LocalConfiguration.current.orientation
    val currentSong = uiState.queue.getOrNull(uiState.currentIndex)
    val isVideoMode = currentSong?.forceVideo == true
    val isFullscreenVideo = isVideoMode && uiState.isVideoFullscreen

    // Rotating to landscape while in Video mode goes fullscreen; rotating back to portrait (or
    // leaving Video mode) exits. The fullscreen button on the surface can enter it from portrait.
    LaunchedEffect(orientation, isVideoMode) {
        playerViewModel.setVideoFullscreen(
            isVideoMode && orientation == Configuration.ORIENTATION_LANDSCAPE
        )
    }

    BackHandler(enabled = isFullscreenVideo) {
        playerViewModel.setVideoFullscreen(false)
    }

    // Immersive fullscreen: hide the system bars while the video fills the screen.
    val fullscreenActivity = LocalContext.current.findActivity()
    val fullscreenView = LocalView.current
    DisposableEffect(isFullscreenVideo) {
        val insetsController = fullscreenActivity?.window?.let { window ->
            WindowCompat.getInsetsController(window, fullscreenView)
        }
        if (isFullscreenVideo && insetsController != null) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Close the screen in resumed with an empty queue
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uiState.queue.isEmpty() && currentSong == null) {
                onBack()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ArtworkPlayerTheme(
        artworkUri = currentSong?.thumbnailPath ?: currentSong?.thumbnailHref,
    ) {
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent
    ) { paddingValues ->
        Box(modifier = modifier.fillMaxSize()) {
        if (!isFullscreenVideo) {
            PlayerArtworkBackground(
                artworkUri = currentSong?.thumbnailPath ?: currentSong?.thumbnailHref,
                modifier = Modifier.matchParentSize()
            )
        }
        if (isFullscreenVideo) {
            FullscreenVideo(
                uiState = uiState,
                onBack = { playerViewModel.setVideoFullscreen(false) },
                onSeek = playerViewModel::seek,
                onSeekPlayer = playerViewModel::seekPlayer,
                onUpdateSeekBarHeldState = playerViewModel::updateSeekBarHeldState,
                onSeekForward = { playerViewModel.seekBy(10_000L) },
                onSeekBackward = { playerViewModel.seekBy(-10_000L) },
                modifier = Modifier.fillMaxSize()
            )
        } else if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),

                horizontalAlignment = Alignment.CenterHorizontally

            ) {
                PlayerTopBar(
                    onBack = onBack,
                    onMoreClick = { playerViewModel.setPlayerMenuVisibility(true) },
                    hasVideoVariant = uiState.hasVideoVariant,
                    isVideoMode = isVideoMode,
                    isModeSwitchEnabled = !uiState.isSwitchingPlaybackMode,
                    onSelectMode = playerViewModel::setPlaybackMode,
                )

                ArtworkOrLyrics(
                    currentSong = currentSong,
                    uiState = uiState,
                    onToggle = playerViewModel::toggleLyricsVisibility,
                    onSkipNext = PlayerManager::skipToNext,
                    onSkipPrevious = PlayerManager::skipToPrevious,
                    onSeekForward = { playerViewModel.seekBy(10_000L) },
                    onSeekBackward = { playerViewModel.seekBy(-10_000L) },
                    onEnterFullscreen = { playerViewModel.setVideoFullscreen(true) },
                    artworkModifier = artworkModifier,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1.08f)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.92f)
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SongInfo(
                        song = currentSong,
                        isLoggedIn = uiState.isLoggedIn,
                        isLiked = uiState.isLiked,
                        isLiking = uiState.isLiking,
                        onToggleLike = playerViewModel::toggleLike,
                        onArtistClick = { playerViewModel.openArtist(onOpenArtist) },
                        showMoreButton = false,
                    )
                    PlayerControls(
                        isPlaying = uiState.isPlaying,
                        isLoading = uiState.isLoading,
                        progress = uiState.playbackProgress,
                        onSeek = playerViewModel::seek,
                        onSeekPlayer = playerViewModel::seekPlayer,
                        onUpdateSeekBarHeldState = playerViewModel::updateSeekBarHeldState,
                        onOpenQueue = {
                            playerViewModel.setQueueVisibility(true)
                        },
                        onOpenSleepTimer = {
                            playerViewModel.setSleepTimerSheetVisibility(true)
                        },
                        onOpenSpeedSelector = {
                            playerViewModel.setSpeedSelectorVisibility(true)
                        },
                        playbackSpeed = uiState.playbackSpeed,
                        sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                        audioInfo = playbackData,
                    )
                }
            }
        } else if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (uiState.hasVideoVariant) {
                        PlaybackModeToggle(
                            isVideoMode = currentSong?.forceVideo == true,
                            enabled = !uiState.isSwitchingPlaybackMode,
                            onSelectMode = playerViewModel::setPlaybackMode,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    ArtworkOrLyrics(
                        currentSong = currentSong,
                        uiState = uiState,
                        onToggle = playerViewModel::toggleLyricsVisibility,
                        onSkipNext = PlayerManager::skipToNext,
                        onSkipPrevious = PlayerManager::skipToPrevious,
                        onSeekForward = { playerViewModel.seekBy(10_000L) },
                        onSeekBackward = { playerViewModel.seekBy(-10_000L) },
                        onEnterFullscreen = { playerViewModel.setVideoFullscreen(true) },
                        artworkModifier = artworkModifier,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    SongInfo(
                        song = currentSong,
                        isLoggedIn = uiState.isLoggedIn,
                        isLiked = uiState.isLiked,
                        isLiking = uiState.isLiking,
                        onToggleLike = playerViewModel::toggleLike,
                        onArtistClick = { playerViewModel.openArtist(onOpenArtist) },
                        onMoreClick = { playerViewModel.setPlayerMenuVisibility(true) },
                    )

                    PlayerControls(
                        isPlaying = uiState.isPlaying,
                        isLoading = uiState.isLoading,
                        progress = uiState.playbackProgress,
                        onSeek = playerViewModel::seek,
                        onSeekPlayer = playerViewModel::seekPlayer,
                        onUpdateSeekBarHeldState = playerViewModel::updateSeekBarHeldState,
                        onOpenQueue = {
                            playerViewModel.setQueueVisibility(true)
                        },
                        onOpenSleepTimer = {
                            playerViewModel.setSleepTimerSheetVisibility(true)
                        },
                        onOpenSpeedSelector = {
                            playerViewModel.setSpeedSelectorVisibility(true)
                        },
                        playbackSpeed = uiState.playbackSpeed,
                        sleepTimerRemainingSeconds = uiState.sleepTimerRemainingSeconds,
                        audioInfo = playbackData,
                    )
                }
            }

        }
        }
    }


    if (uiState.isSpeedSelectorShown) {
        SpeedSelectorBottomSheet(
            changeVisibility = playerViewModel::setSpeedSelectorVisibility,
            currentSpeed = uiState.playbackSpeed,
            onSelectSpeed = playerViewModel::setPlaybackSpeed,
        )
    }

    if (uiState.isQueueModalShown) {
        QueueBottomSheet(
            changeVisibility = playerViewModel::setQueueVisibility,
            songs = uiState.queue,
            currentIndex = uiState.currentIndex
        )
    }

    if (uiState.isPlayerMenuShown && currentSong != null) {
        PlayerMenuBottomSheet(
            changeVisibility = playerViewModel::setPlayerMenuVisibility,
            song = currentSong,
            onOpenAlbum = onOpenAlbum,
            onOpenEqualizer = onOpenEqualizer,
            onPlayVideoVersion = playerViewModel::playCurrentAsVideo,
            abLoop = uiState.abLoop,
            onToggleAbLoop = playerViewModel::toggleAbLoop,
        )
    }

    if (uiState.isSleepTimerModalShown) {
        SleepTimerBottomSheet(
            changeVisibility = playerViewModel::setSleepTimerSheetVisibility,
            activeRemainingSeconds = uiState.sleepTimerRemainingSeconds,
            onStartTimer = playerViewModel::startSleepTimer,
            onStartEndOfSong = playerViewModel::startSleepTimerEndOfSong,
            onCancelTimer = playerViewModel::cancelSleepTimer,
        )
    }
    }
}

@Composable
private fun ArtworkOrLyrics(
    currentSong: Song?,
    uiState: PlayerState,
    onToggle: () -> Unit,
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onEnterFullscreen: () -> Unit = {},
    artworkModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.clickable(onClick = onToggle)) {
        if (uiState.areLyricsShown) {
            LyricsPanel(
                lyricsState = uiState.lyrics,
                currentPositionMs = uiState.playbackProgress.position.toLong(),
                modifier = Modifier.fillMaxSize()
            )
        } else if (currentSong?.forceVideo == true) {
            VideoSurface(
                onToggle = onToggle,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onSeekForward = onSeekForward,
                onSeekBackward = onSeekBackward,
                onEnterFullscreen = onEnterFullscreen,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Thumbnail(
                href = (currentSong?.thumbnailPath ?: currentSong?.thumbnailHref).toString(),
                onToggle = onToggle,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onSeekForward = onSeekForward,
                onSeekBackward = onSeekBackward,
                artworkModifier = artworkModifier,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun Thumbnail(
    href: String,
    onToggle: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    artworkModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        val artSize = minOf(maxWidth, maxHeight)

        AnimatedContent(
            targetState = href,
            transitionSpec = {
                fadeIn(
                    animationSpec = tween(Constants.Player.IMAGE_TRANSITION_DELAY)
                ).togetherWith(
                    fadeOut(
                        animationSpec = tween(Constants.Player.IMAGE_TRANSITION_DELAY)
                    )
                )
            }
        ) { targetState ->
            SquareImage(
                uri = targetState,
                modifier = artworkModifier
                    .size(artSize)
                    // Tap toggles lyrics; double-tap on the right/left half seeks ±10s.
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onToggle() },
                            onDoubleTap = { offset ->
                                if (offset.x >= size.width / 2f) {
                                    onSeekForward()
                                } else {
                                    onSeekBackward()
                                }
                            }
                        )
                    }
                    // Horizontal swipe: left = next track, right = previous track.
                    .pointerInput(Unit) {
                        var dragTotal = 0f
                        val threshold = size.width * 0.15f
                        detectHorizontalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onDragEnd = {
                                if (dragTotal <= -threshold) {
                                    onSkipNext()
                                } else if (dragTotal >= threshold) {
                                    onSkipPrevious()
                                }
                            },
                            onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount }
                        )
                    }
            )
        }
    }
}

/**
 * Stable player chrome: back and overflow stay in the same place while the center slot switches
 * between the quiet "now playing" label and Song/Video mode. This prevents the artwork from
 * jumping when video availability resolves asynchronously.
 */
@Composable
private fun PlayerTopBar(
    onBack: () -> Unit,
    onMoreClick: () -> Unit,
    hasVideoVariant: Boolean,
    isVideoMode: Boolean,
    isModeSwitchEnabled: Boolean,
    onSelectMode: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(CircleShape),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
            )
        }

        if (hasVideoVariant) {
            PlaybackModeToggle(
                isVideoMode = isVideoMode,
                enabled = isModeSwitchEnabled,
                onSelectMode = onSelectMode,
            )
        } else {
            Text(
                text = stringResource(R.string.playing_now),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(
            onClick = onMoreClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clip(CircleShape),
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.more),
            )
        }
    }
}


/**
 * "Song | Video" pill shown at the top of the player when the current item has a video variant.
 * The selected segment reflects what is actually playing; [enabled] debounces taps while a mode
 * switch is resolving streams.
 */
@Composable
private fun PlaybackModeToggle(
    isVideoMode: Boolean,
    enabled: Boolean,
    onSelectMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaybackModeSegment(
            label = stringResource(R.string.player_mode_song),
            selected = !isVideoMode,
            enabled = enabled,
            onClick = { onSelectMode(false) }
        )
        PlaybackModeSegment(
            label = stringResource(R.string.player_mode_video),
            selected = isVideoMode,
            enabled = enabled,
            onClick = { onSelectMode(true) }
        )
    }
}

@Composable
private fun PlaybackModeSegment(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                }
            )
            .clickable(enabled = enabled && !selected, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

/**
 * 16:9 video surface shown in place of the artwork while the current item plays in Video mode.
 * The SurfaceView is attached to the session player through the MediaController (which forwards
 * it to the service's ExoPlayer) and detached on dispose, so audio-only screens never hold a
 * surface. Reuses the artwork's tap/double-tap/swipe gestures.
 */
@Composable
private fun VideoSurface(
    onToggle: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onEnterFullscreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val controller = PlayerManager.controllerState.collectAsStateWithLifecycle().value
    val isInPip = PlayerManager.isInPipMode.collectAsStateWithLifecycle().value
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }

    Box(
        modifier = modifier.padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.large)
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { context -> SurfaceView(context).also { surfaceView = it } },
                modifier = Modifier
                    .fillMaxSize()
                    // Tap toggles lyrics; double-tap on the right/left half seeks ±10s.
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onToggle() },
                            onDoubleTap = { offset ->
                                if (offset.x >= size.width / 2f) {
                                    onSeekForward()
                                } else {
                                    onSeekBackward()
                                }
                            }
                        )
                    }
                    // Horizontal swipe: left = next track, right = previous track.
                    .pointerInput(Unit) {
                        var dragTotal = 0f
                        val threshold = size.width * 0.15f
                        detectHorizontalDragGestures(
                            onDragStart = { dragTotal = 0f },
                            onDragEnd = {
                                if (dragTotal <= -threshold) {
                                    onSkipNext()
                                } else if (dragTotal >= threshold) {
                                    onSkipPrevious()
                                }
                            },
                            onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount }
                        )
                    }
            )

            IconButton(
                onClick = onEnterFullscreen,
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fullscreen,
                    contentDescription = stringResource(R.string.fullscreen),
                    tint = Color.White
                )
            }
        }
    }

    // Keyed on isInPip too: leaving PiP re-runs the effect so this surface is re-attached after
    // the PiP overlay's surface cleared itself (clearing is a no-op for a non-current view).
    DisposableEffect(controller, surfaceView, isInPip) {
        val view = surfaceView
        if (controller != null && view != null && !isInPip) {
            controller.setVideoSurfaceView(view)
        }
        onDispose {
            if (view != null) {
                controller?.clearVideoSurfaceView(view)
            }
        }
    }
}

/**
 * Fullscreen Video mode: the whole screen is black with the video letterboxed at its largest
 * 16:9 fit, all player chrome replaced by overlay controls (back, ±10s, play/pause, seek bar)
 * that auto-hide after a short idle delay while playing. Tap toggles the overlay; double-tap on
 * the right/left half seeks ±10s like the regular surface.
 */
@Composable
private fun FullscreenVideo(
    uiState: PlayerState,
    onBack: () -> Unit,
    onSeek: (location: Float) -> Unit,
    onSeekPlayer: () -> Unit,
    onUpdateSeekBarHeldState: (isHeld: Boolean) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = PlayerManager.controllerState.collectAsStateWithLifecycle().value
    val isInPip = PlayerManager.isInPipMode.collectAsStateWithLifecycle().value
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }

    var areControlsShown by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }

    fun showControls() {
        areControlsShown = true
        interactionTick++
    }

    // Auto-hide the overlay after an idle delay, but only while actually playing and while the
    // seek bar isn't held; any interaction bumps [interactionTick] to restart the countdown.
    LaunchedEffect(interactionTick, uiState.isPlaying, uiState.isSeekBarHeld) {
        if (uiState.isPlaying && !uiState.isSeekBarHeld) {
            delay(Constants.Player.FULLSCREEN_CONTROLS_HIDE_DELAY_MS)
            areControlsShown = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (areControlsShown) {
                            areControlsShown = false
                        } else {
                            showControls()
                        }
                    },
                    onDoubleTap = { offset ->
                        if (offset.x >= size.width / 2f) {
                            onSeekForward()
                        } else {
                            onSeekBackward()
                        }
                        showControls()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context -> SurfaceView(context).also { surfaceView = it } },
            modifier = Modifier.aspectRatio(16f / 9f)
        )

        AnimatedVisibility(
            visible = areControlsShown,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            FullscreenVideoControls(
                uiState = uiState,
                onBack = onBack,
                onSeek = onSeek,
                onSeekPlayer = onSeekPlayer,
                onUpdateSeekBarHeldState = onUpdateSeekBarHeldState,
                onSeekForward = {
                    onSeekForward()
                    showControls()
                },
                onSeekBackward = {
                    onSeekBackward()
                    showControls()
                },
            )
        }
    }

    DisposableEffect(controller, surfaceView, isInPip) {
        val view = surfaceView
        if (controller != null && view != null && !isInPip) {
            controller.setVideoSurfaceView(view)
        }
        onDispose {
            if (view != null) {
                controller?.clearVideoSurfaceView(view)
            }
        }
    }
}

@Composable
private fun FullscreenVideoControls(
    uiState: PlayerState,
    onBack: () -> Unit,
    onSeek: (location: Float) -> Unit,
    onSeekPlayer: () -> Unit,
    onUpdateSeekBarHeldState: (isHeld: Boolean) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = Color.White
            )
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onSeekBackward) {
                Icon(
                    imageVector = Icons.Rounded.Replay10,
                    contentDescription = stringResource(R.string.seek_backward_10),
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    val controller = PlayerManager.currentController
                    if (controller?.isPlaying == true) {
                        controller.pause()
                    } else {
                        controller?.play()
                    }
                },
                modifier = Modifier.size(72.dp)
            ) {
                if (uiState.isLoading) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(40.dp))
                } else {
                    Icon(
                        imageVector = if (uiState.isPlaying) {
                            Icons.Rounded.Pause
                        } else {
                            Icons.Rounded.PlayArrow
                        },
                        contentDescription = if (uiState.isPlaying) {
                            stringResource(R.string.pause)
                        } else {
                            stringResource(R.string.play)
                        },
                        tint = Color.White,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }

            IconButton(onClick = onSeekForward) {
                Icon(
                    imageVector = Icons.Rounded.Forward10,
                    contentDescription = stringResource(R.string.seek_forward_10),
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Slider(
                value = uiState.playbackProgress.position,
                valueRange = 0f..uiState.playbackProgress.duration,
                onValueChange = { newValue ->
                    onUpdateSeekBarHeldState(true)
                    onSeek(newValue)
                },
                onValueChangeFinished = {
                    onSeekPlayer()
                    onUpdateSeekBarHeldState(false)
                },
                track = { sliderState ->
                    LinearWavyProgressIndicator(
                        progress = { sliderState.coercedValueAsFraction },
                        modifier = Modifier.fillMaxWidth(),
                        // Flat track while paused, wavy while playing
                        amplitude = { if (uiState.isPlaying) 1f else 0f },
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = uiState.playbackProgress.position.toTimeString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = uiState.playbackProgress.duration.toTimeString(),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }
    }
}

/** Unwraps a Compose [Context] back to its hosting [Activity], if any. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


@Composable
fun SongInfo(
    song: Song?,
    isLoggedIn: Boolean = false,
    isLiked: Boolean = false,
    isLiking: Boolean = false,
    onToggleLike: () -> Unit = {},
    onArtistClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
    showMoreButton: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = song?.title ?: "",
                style = MaterialTheme.typography.titleLargeEmphasized.copy(fontFamily = nunitoFontFamily),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.basicMarquee()
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraSmall)
                    .clickable(enabled = song != null, onClick = onArtistClick)
            ) {
                Text(
                    text = song?.artist ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .basicMarquee()
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.view_artist),
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 8.dp)
        ) {
            if (isLoggedIn) {
                FilledIconToggleButton(
                    checked = isLiked,
                    onCheckedChange = {
                        if (isLiking) {
                            return@FilledIconToggleButton
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        onToggleLike()
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        checkedContainerColor = IconButtonDefaults.filledIconToggleButtonColors().checkedContainerColor,
                        checkedContentColor = IconButtonDefaults.filledIconToggleButtonColors().checkedContentColor,
                    ),
                ) {
                    Icon(
                        imageVector = if (isLiked) {
                            Icons.Rounded.Favorite
                        } else {
                            Icons.Rounded.FavoriteBorder
                        },
                        contentDescription = if (isLiked) {
                            stringResource(R.string.unlike)
                        } else {
                            stringResource(R.string.like)
                        }
                    )
                }
            }

            if (showMoreButton) {
                IconButton(
                    onClick = onMoreClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.more)
                    )
                }
            }
        }
    }
}
