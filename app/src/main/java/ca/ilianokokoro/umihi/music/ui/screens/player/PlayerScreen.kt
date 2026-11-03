package ca.ilianokokoro.umihi.music.ui.screens.player

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.car.app.connection.CarConnection
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import ca.ilianokokoro.umihi.music.core.domain.PlaybackEngine
import ca.ilianokokoro.umihi.music.core.helpers.ShareHelper
import ca.ilianokokoro.umihi.music.core.managers.AudioOutputManager
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.core.managers.QueueProvenance
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.ui.components.SquareImage
import ca.ilianokokoro.umihi.music.ui.components.cast.AvailableDevicesSheet
import ca.ilianokokoro.umihi.music.ui.components.cast.rememberCarConnectionType
import ca.ilianokokoro.umihi.music.ui.components.studio.AlertDialog
import ca.ilianokokoro.umihi.music.ui.components.studio.TextButton
import ca.ilianokokoro.umihi.music.ui.navigation.supportsPermanentSecondaryPane
import ca.ilianokokoro.umihi.music.ui.screens.player.components.LyricsPanel
import ca.ilianokokoro.umihi.music.ui.screens.player.components.PlayerArtworkBackground
import ca.ilianokokoro.umihi.music.ui.screens.player.components.PlayerControls
import ca.ilianokokoro.umihi.music.ui.screens.player.components.PlayerMenuBottomSheet
import ca.ilianokokoro.umihi.music.ui.screens.player.components.QueueBottomSheet
import ca.ilianokokoro.umihi.music.ui.screens.player.components.QueuePanel
import ca.ilianokokoro.umihi.music.ui.screens.player.components.SleepTimerBottomSheet
import ca.ilianokokoro.umihi.music.ui.screens.player.components.SongDetailsBottomSheet
import ca.ilianokokoro.umihi.music.ui.screens.player.components.SpeedSelectorBottomSheet
import ca.ilianokokoro.umihi.music.ui.theme.ArtworkPlayerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    playbackEngine: PlaybackEngine,
    onBack: () -> Unit,
    onOpenArtist: (Song) -> Unit = {},
    onOpenAlbum: (Song) -> Unit = {},
    onOpenQueueSource: (QueueProvenance) -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    modifier: Modifier = Modifier,
    artworkModifier: Modifier = Modifier,
    application: Application,
    playerViewModel: PlayerViewModel = viewModel(
        factory =
            PlayerViewModel.Factory(application = application)
    )
) {
    val context = LocalContext.current
    val uiState = playerViewModel.uiState.collectAsStateWithLifecycle().value
    val playbackData = PlayerManager.audioInfo.collectAsStateWithLifecycle().value
    val isCasting by PlayerManager.isCasting.collectAsStateWithLifecycle()
    val castDeviceName by PlayerManager.castDeviceName.collectAsStateWithLifecycle()
    val activeAudioRoute by AudioOutputManager.activeRoute.collectAsStateWithLifecycle()
    val carConnectionType = rememberCarConnectionType()
    val isCarConnected = carConnectionType != CarConnection.CONNECTION_TYPE_NOT_CONNECTED
    val outputDeviceName = when {
        isCasting -> castDeviceName
        isCarConnected -> stringResource(
            if (carConnectionType == CarConnection.CONNECTION_TYPE_NATIVE) {
                R.string.android_automotive
            } else {
                R.string.android_auto
            }
        )
        activeAudioRoute?.kind != null &&
            activeAudioRoute?.kind != AudioOutputManager.RouteKind.PHONE ->
            activeAudioRoute?.name
        else -> null
    }
    val isExternalDevice = isCasting || isCarConnected ||
        activeAudioRoute?.kind?.let { it != AudioOutputManager.RouteKind.PHONE } == true
    var showDevicePicker by remember { mutableStateOf(false) }
    var showSongDetails by remember { mutableStateOf(false) }
    val gesturePreferences = remember(context) {
        context.getSharedPreferences("player-onboarding", Context.MODE_PRIVATE)
    }
    var showGestureHelp by remember {
        mutableStateOf(!gesturePreferences.getBoolean("gestures-seen", false))
    }
    val configuration = LocalConfiguration.current
    val orientation = configuration.orientation
    val showPermanentQueue = supportsPermanentSecondaryPane(
        widthDp = configuration.screenWidthDp,
        heightDp = configuration.screenHeightDp,
    )
    val playerFocusRequester = remember { FocusRequester() }
    val currentSong = uiState.queue.getOrNull(uiState.currentIndex)
    val isVideoMode = currentSong?.forceVideo == true
    val isFullscreenVideo = isVideoMode && !isCasting && uiState.isVideoFullscreen

    LaunchedEffect(Unit) {
        AudioOutputManager.refresh(context.applicationContext)
        playerFocusRequester.requestFocus()
    }

    LaunchedEffect(showPermanentQueue) {
        if (showPermanentQueue) {
            playerViewModel.setQueueVisibility(false)
        }
    }

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
    BackHandler(enabled = uiState.areLyricsShown && !isFullscreenVideo) {
        playerViewModel.toggleLyricsVisibility()
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
        Box(
            modifier = modifier
                .fillMaxSize()
                .focusRequester(playerFocusRequester)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else {
                        when (event.key) {
                            Key.Spacebar -> {
                                if (uiState.isPlaying) {
                                    playbackEngine.pause()
                                } else {
                                    playbackEngine.play()
                                }
                                true
                            }

                            Key.DirectionLeft -> {
                                playbackEngine.seekBy(-10_000L)
                                true
                            }

                            Key.DirectionRight -> {
                                playbackEngine.seekBy(10_000L)
                                true
                            }

                            Key.Escape -> {
                                onBack()
                                true
                            }

                            else -> false
                        }
                    }
                },
        ) {
        if (!isFullscreenVideo) {
            PlayerArtworkBackground(
                artworkUri = currentSong?.thumbnailPath ?: currentSong?.thumbnailHref,
                isPlaying = uiState.isPlaying,
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
        } else if (
            orientation == Configuration.ORIENTATION_PORTRAIT && !showPermanentQueue
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),

                horizontalAlignment = Alignment.CenterHorizontally

            ) {
                PlayerTopBar(
                    onBack = onBack,
                    onMoreClick = { playerViewModel.setPlayerMenuVisibility(true) },
                    isExternalDevice = isExternalDevice,
                    onDevicesClick = { showDevicePicker = true },
                    hasVideoVariant = uiState.hasVideoVariant && !isCasting,
                    isVideoMode = isVideoMode,
                    isModeSwitchEnabled = !uiState.isSwitchingPlaybackMode,
                    onSelectMode = playerViewModel::setPlaybackMode,
                )

                ArtworkOrLyrics(
                    currentSong = currentSong,
                    uiState = uiState,
                    onToggle = playerViewModel::toggleLyricsVisibility,
                    onRetryLyrics = playerViewModel::retryLyrics,
                    onSkipNext = PlayerManager::skipToNext,
                    onSkipPrevious = PlayerManager::skipToPrevious,
                    onSeekForward = { playerViewModel.seekBy(10_000L) },
                    onSeekBackward = { playerViewModel.seekBy(-10_000L) },
                    onEnterFullscreen = { playerViewModel.setVideoFullscreen(true) },
                    isCasting = isCasting,
                    artworkModifier = artworkModifier,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1.08f)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.92f)
                        .padding(horizontal = 30.dp),
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
                        isCasting = isCasting,
                        outputDeviceName = outputDeviceName,
                        audioInfo = playbackData,
                    )
                }
            }
        } else if (
            orientation == Configuration.ORIENTATION_LANDSCAPE || showPermanentQueue
        ) {
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
                    if (showPermanentQueue) {
                        PlayerTopBar(
                            onBack = onBack,
                            onMoreClick = { playerViewModel.setPlayerMenuVisibility(true) },
                            isExternalDevice = isExternalDevice,
                            onDevicesClick = { showDevicePicker = true },
                            hasVideoVariant = uiState.hasVideoVariant && !isCasting,
                            isVideoMode = isVideoMode,
                            isModeSwitchEnabled = !uiState.isSwitchingPlaybackMode,
                            onSelectMode = playerViewModel::setPlaybackMode,
                        )
                    } else if (uiState.hasVideoVariant && !isCasting) {
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
                        onRetryLyrics = playerViewModel::retryLyrics,
                        onSkipNext = PlayerManager::skipToNext,
                        onSkipPrevious = PlayerManager::skipToPrevious,
                        onSeekForward = { playerViewModel.seekBy(10_000L) },
                        onSeekBackward = { playerViewModel.seekBy(-10_000L) },
                        onEnterFullscreen = { playerViewModel.setVideoFullscreen(true) },
                        isCasting = isCasting,
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
                        showMoreButton = !showPermanentQueue,
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
                        showQueueButton = !showPermanentQueue,
                        isCasting = isCasting,
                        outputDeviceName = outputDeviceName,
                        audioInfo = playbackData,
                    )
                }

                if (showPermanentQueue) {
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1.05f)
                            .padding(top = 12.dp, end = 12.dp, bottom = 12.dp),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                        tonalElevation = 2.dp,
                    ) {
                        QueuePanel(
                            songs = uiState.queue,
                            currentIndex = uiState.currentIndex,
                            isSavingQueue = uiState.isSavingQueue,
                            smartTransitions = uiState.smartTransitions,
                            onSmartTransitionsChange = playerViewModel::setSmartTransitions,
                            onOpenSource = onOpenQueueSource,
                            onSaveQueue = playerViewModel::saveQueueAsPlaylist,
                            modifier = Modifier.padding(top = 20.dp),
                        )
                    }
                }
            }

        }
        }
    }

    if (uiState.isSpeedSelectorShown && !isCasting) {
        SpeedSelectorBottomSheet(
            changeVisibility = playerViewModel::setSpeedSelectorVisibility,
            currentSpeed = uiState.playbackSpeed,
            onSelectSpeed = playerViewModel::setPlaybackSpeed,
        )
    }

    if (uiState.isQueueModalShown && !showPermanentQueue) {
        QueueBottomSheet(
            changeVisibility = playerViewModel::setQueueVisibility,
            songs = uiState.queue,
            currentIndex = uiState.currentIndex,
            isSavingQueue = uiState.isSavingQueue,
            smartTransitions = uiState.smartTransitions,
            onSmartTransitionsChange = playerViewModel::setSmartTransitions,
            onOpenSource = {
                playerViewModel.setQueueVisibility(false)
                onOpenQueueSource(it)
            },
            onSaveQueue = playerViewModel::saveQueueAsPlaylist,
        )
    }

    if (uiState.isPlayerMenuShown && currentSong != null) {
        PlayerMenuBottomSheet(
            changeVisibility = playerViewModel::setPlayerMenuVisibility,
            song = currentSong,
            onOpenAlbum = { playerViewModel.openAlbum(onOpenAlbum) },
            onOpenArtist = { playerViewModel.openArtist(onOpenArtist) },
            onShowDetails = { showSongDetails = true },
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
            defaultMinutes = uiState.sleepTimerDefaultMinutes,
            onStartTimer = playerViewModel::startSleepTimer,
            onStartEndOfSong = playerViewModel::startSleepTimerEndOfSong,
            onCancelTimer = playerViewModel::cancelSleepTimer,
        )
    }

    if (showDevicePicker) {
        AvailableDevicesSheet(
            carConnectionType = carConnectionType,
            onDismiss = { showDevicePicker = false },
        )
    }

    if (showSongDetails && currentSong != null) {
        SongDetailsBottomSheet(
            song = currentSong,
            audioInfo = playbackData,
            onOpenArtist = {
                showSongDetails = false
                playerViewModel.openArtist(onOpenArtist)
            },
            onOpenAlbum = {
                showSongDetails = false
                playerViewModel.openAlbum(onOpenAlbum)
            },
            onDismiss = { showSongDetails = false },
        )
    }

    if (showGestureHelp) {
        GestureHelpDialog(
            onDismiss = {
                gesturePreferences.edit().putBoolean("gestures-seen", true).apply()
                showGestureHelp = false
            }
        )
    }
    }
}

@Composable
private fun GestureHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.gesture_help_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GestureHint(Icons.Rounded.Swipe, stringResource(R.string.gesture_swipe_tracks))
                GestureHint(Icons.Rounded.TouchApp, stringResource(R.string.gesture_double_tap))
                GestureHint(Icons.Rounded.Lyrics, stringResource(R.string.gesture_tap_lyrics))
                GestureHint(Icons.Rounded.Share, stringResource(R.string.gesture_share_lyric))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.got_it))
            }
        },
    )
}

@Composable
private fun GestureHint(icon: ImageVector, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.padding(10.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ArtworkOrLyrics(
    currentSong: Song?,
    uiState: PlayerState,
    onToggle: () -> Unit,
    onRetryLyrics: () -> Unit,
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    onEnterFullscreen: () -> Unit = {},
    isCasting: Boolean = false,
    artworkModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()
    val showCoverLabel = stringResource(R.string.show_cover)
    Box(modifier = modifier) {
        if (uiState.areLyricsShown) {
            LyricsPanel(
                lyricsState = uiState.lyrics,
                currentPositionMs = uiState.playbackProgress.position.toLong(),
                isPlaying = uiState.isPlaying,
                playbackSpeed = uiState.playbackSpeed,
                onSeekTo = { PlayerManager.currentController?.seekTo(it) },
                onRetry = onRetryLyrics,
                onShareLine = { line ->
                    currentSong?.let { song ->
                        shareScope.launch { ShareHelper.shareLyricsCard(context, song, line) }
                    }
                },
                // Only the lyric lines consume taps (they seek); the surrounding area closes the
                // panel, mirroring the artwork. Without it the system back was the only way out.
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onToggle,
                    )
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(showCoverLabel) {
                                onToggle()
                                true
                            },
                        )
                    }
            )
        } else if (currentSong?.forceVideo == true && !isCasting) {
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
                contentDescription = currentSong?.title?.let {
                    stringResource(R.string.player_artwork_description, it)
                },
                // Buffering is not a pause: the cover must not dip at every track change.
                isPlaying = uiState.isPlaying || uiState.isLoading,
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

private const val ARTWORK_PAUSED_SCALE = 0.88f

@Composable
fun Thumbnail(
    href: String,
    contentDescription: String? = null,
    isPlaying: Boolean = true,
    onToggle: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    onSeekBackward: () -> Unit = {},
    artworkModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val showLyricsLabel = stringResource(R.string.show_lyrics)
    BoxWithConstraints(
        modifier = modifier.padding(30.dp),
        contentAlignment = Alignment.Center
    ) {
        val artSize = minOf(maxWidth, maxHeight)
        // The cover sits back while the music is stopped and comes forward when it plays.
        val artScale by animateFloatAsState(
            targetValue = if (isPlaying) 1f else ARTWORK_PAUSED_SCALE,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 320f),
            label = "playerArtworkScale",
        )

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
                contentDescription = contentDescription,
                modifier = artworkModifier
                    .size(artSize)
                    .semantics {
                        onClick(label = showLyricsLabel) {
                            onToggle()
                            true
                        }
                    }
                    .graphicsLayer {
                        scaleX = artScale
                        scaleY = artScale
                    }
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
 * Stable player chrome: back and overflow stay in the same place while the optional Song/Video
 * mode switch occupies the center. The player deliberately has no redundant "now playing" label.
 */
@Composable
private fun PlayerTopBar(
    onBack: () -> Unit,
    onMoreClick: () -> Unit,
    isExternalDevice: Boolean,
    onDevicesClick: () -> Unit,
    hasVideoVariant: Boolean,
    isVideoMode: Boolean,
    isModeSwitchEnabled: Boolean,
    onSelectMode: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(onBack) {
                var downwardDrag = 0f
                detectVerticalDragGestures(
                    onDragStart = { downwardDrag = 0f },
                    onDragEnd = {
                        if (downwardDrag >= 72.dp.toPx()) {
                            onBack()
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        downwardDrag = (downwardDrag + dragAmount).coerceAtLeast(0f)
                        change.consume()
                    },
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 34.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)),
        )

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
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = stringResource(R.string.back),
            )
        }

        if (hasVideoVariant) {
            PlaybackModeToggle(
                isVideoMode = isVideoMode,
                enabled = isModeSwitchEnabled,
                onSelectMode = onSelectMode,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onDevicesClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        .copy(alpha = 0.72f),
                    contentColor = if (isExternalDevice) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
                modifier = Modifier.clip(CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Devices,
                    contentDescription = stringResource(R.string.available_devices),
                )
            }
            IconButton(
                onClick = onMoreClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        .copy(alpha = 0.72f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.clip(CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.more),
                )
            }
        }
    }
}

/**
 * Song|Video pill shown at the top of the player when the current item has a video variant.
 * Icon-only segments (note vs camera); the labels move to content descriptions. The selected
 * segment reflects what is actually playing; [enabled] debounces taps while a mode switch is
 * resolving streams.
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
            icon = Icons.Rounded.MusicNote,
            contentDescription = stringResource(R.string.player_mode_song),
            selected = !isVideoMode,
            enabled = enabled,
            onClick = { onSelectMode(false) }
        )
        PlaybackModeSegment(
            icon = Icons.Rounded.Videocam,
            contentDescription = stringResource(R.string.player_mode_video),
            selected = isVideoMode,
            enabled = enabled,
            onClick = { onSelectMode(true) }
        )
    }
}

@Composable
private fun PlaybackModeSegment(
    icon: ImageVector,
    contentDescription: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = if (selected) {
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
            .padding(horizontal = 14.dp, vertical = 5.dp)
            .size(20.dp)
    )
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
        AnimatedContent(
            targetState = song,
            contentKey = { it?.youtubeId },
            transitionSpec = {
                (
                    fadeIn(tween(180)) +
                        slideInVertically(tween(180)) { height -> height / 8 }
                    ) togetherWith fadeOut(tween(90))
            },
            contentAlignment = Alignment.TopStart,
            label = "playerTrackInfo",
            modifier = Modifier.weight(1f),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = it?.title ?: "",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.basicMarquee()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(enabled = it != null, onClick = onArtistClick)
                ) {
                    Text(
                        text = it?.artist ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .basicMarquee()
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.view_artist),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        checkedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        checkedContentColor = MaterialTheme.colorScheme.primary,
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
