package ca.ilianokokoro.umihi.music

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Rational
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.cast.CastManager
import ca.ilianokokoro.umihi.music.core.helpers.DisplayRefresh
import ca.ilianokokoro.umihi.music.core.helpers.ExternalAudio
import ca.ilianokokoro.umihi.music.core.jam.JamInvites
import ca.ilianokokoro.umihi.music.core.jam.JamManager
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.core.managers.ScreenAwakeManager
import ca.ilianokokoro.umihi.music.core.managers.VersionManager
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiConstants
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.SongRepository
import ca.ilianokokoro.umihi.music.data.repositories.StartupPreferences
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.ui.components.PipVideoOverlay
import ca.ilianokokoro.umihi.music.ui.components.dialog.UpdateDialog
import ca.ilianokokoro.umihi.music.ui.navigation.NavigationRoot
import ca.ilianokokoro.umihi.music.ui.theme.ThemeColor
import ca.ilianokokoro.umihi.music.ui.theme.ThemePreset
import ca.ilianokokoro.umihi.music.ui.theme.UmihiMusicTheme
import ca.ilianokokoro.umihi.music.ui.theme.dmSansFontFamily
import ca.ilianokokoro.umihi.music.ui.theme.nunitoFontFamily
import cat.ereza.customactivityoncrash.config.CaocConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val songRepository by lazy { SongRepository() }
    private val datastoreRepository by lazy { DatastoreRepository(applicationContext) }

    /** Mirrors [isInPictureInPictureMode] into Compose so the video-only PiP layer can be shown. */
    private val isInPipModeState by lazy(LazyThreadSafetyMode.NONE) {
        mutableStateOf(false)
    }
    private var postFirstFrameInitialized = false
    private var pipReceiverRegistered = false
    private var lastAutomaticUpdateCheckAtMs: Long? = null

    /** Handles the PiP window's play/pause remote action. */
    private val pipActionReceiver by lazy(LazyThreadSafetyMode.NONE) {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Constants.Player.Pip.ACTION_PLAY_PAUSE) {
                    return
                }
                val controller = PlayerManager.currentController ?: return
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    controller.play()
                }
            }
        }
    }

    /** Keeps the PiP play/pause remote action in sync with the actual playback state. */
    private val pipPlayerListener by lazy(LazyThreadSafetyMode.NONE) {
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePipParams()
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(StartupShellView(this))
        initializeAfterFirstFrame()

        handleShareIntent(intent)
        handleViewIntent(intent)
        handleVoiceSearchIntent(intent)
        lifecycleScope.launch {
            DatastoreRepository(applicationContext).settings
                .map { it.preferFastestRefresh }
                .distinctUntilChanged()
                .collect { DisplayRefresh.apply(this@MainActivity, it) }
        }

        //    debugPrintDataStores()
    }

    private fun setAppContent() {
        setContent {
            val themeColor by datastoreRepository.themeColor
                .collectAsStateWithLifecycle(
                    initialValue = ThemeColor.Umihi
                )
            val themePreset by datastoreRepository.themePreset
                .collectAsStateWithLifecycle(
                    initialValue = ThemePreset.Studio
                )

            UmihiMusicTheme(
                themeColor = themeColor,
                themePreset = themePreset,
                fontFamily = nunitoFontFamily(),
                studioFontFamily = dmSansFontFamily(),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavigationRoot(
                        modifier = Modifier.fillMaxSize()
                    )
                    UpdateDialog(lifecycleScope)

                    // In PiP the whole activity is rendered in the tiny window: cover the app UI
                    // with a video-only layer so the window shows just the letterboxed video.
                    if (isInPipModeState.value) {
                        PipVideoOverlay(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

//    private fun debugPrintDataStores() {
//        lifecycleScope.launch {
//            DatastoreRepository(this@MainActivity).debugPrintAllPreferences()
//        }
//    }


    /**
     * ExoPlayer/MediaSession construction, crash-handler setup and permission IPC are useful but
     * none are required to paint the launch screen. Running them after the first traversal prevents
     * the playback service from consuming the main thread inside time-to-first-frame.
     */
    private fun initializeAfterFirstFrame() {
        window.decorView.doOnPreDraw {
            // A normal post can replace the native view before RenderThread has latched its buffer.
            // One additional vsync guarantees the skeleton is the measured first app frame.
            window.decorView.postDelayed(startup@{
                if (isFinishing || isDestroyed) return@startup
                setAppContent()
                initializeRuntimeAfterAppFrame()
            }, FIRST_FRAME_LATCH_DELAY_MS)
        }
    }

    private fun initializeRuntimeAfterAppFrame() {
        window.decorView.doOnPreDraw {
            window.decorView.post {
                if (isFinishing || isDestroyed) return@post

                PlayerManager.connectController(this@MainActivity)
                if (!postFirstFrameInitialized) {
                    postFirstFrameInitialized = true
                    ScreenAwakeManager.registerActivity(this@MainActivity)
                    // The PackageInfo lookup is useful later, never for launch rendering.
                    lifecycleScope.launch(Dispatchers.IO) {
                        VersionManager.initialize(applicationContext)
                    }
                    CastManager.initializeIfSupported(
                        applicationContext,
                        Dispatchers.IO.asExecutor(),
                    )
                    initCaoc()
                    initPictureInPicture()
                    requestNotificationPermission()
                    checkForUpdatesIfDue()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (postFirstFrameInitialized) {
            checkForUpdatesIfDue()
        }
    }

    private fun checkForUpdatesIfDue() {
        val now = SystemClock.elapsedRealtime()
        val lastCheck = lastAutomaticUpdateCheckAtMs
        if (lastCheck != null && now - lastCheck < AUTOMATIC_UPDATE_CHECK_INTERVAL_MS) {
            return
        }
        lastAutomaticUpdateCheckAtMs = now
        lifecycleScope.launch {
            VersionManager.checkForUpdates(this@MainActivity)
        }
    }

    companion object {
        private const val FIRST_FRAME_LATCH_DELAY_MS = 32L
        private const val REQUEST_CODE_NOTIFICATION_PERMISSION = 1001
        private const val AUTOMATIC_UPDATE_CHECK_INTERVAL_MS = 60 * 60 * 1000L
    }

    override fun onDestroy() {
        if (pipReceiverRegistered) {
            unregisterReceiver(pipActionReceiver)
        }
        PlayerManager.currentController?.removeListener(pipPlayerListener)
        ScreenAwakeManager.unregisterActivity(this)
        super.onDestroy()
    }

    /**
     * Home/recents during video playback: hand the video off to a picture-in-picture window.
     * Pure audio playback never triggers PiP (the media notification already covers it).
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        if (PlayerManager.getCurrentSong()?.forceVideo != true || !PlayerManager.isPlaying) {
            return
        }
        if (PlayerManager.isCasting.value) return

        try {
            enterPictureInPictureMode(buildPipParams(isPlaying = true))
        } catch (_: IllegalStateException) {
            // PiP refused (disabled by the user/device): regular background playback continues.
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState.value = isInPictureInPictureMode
        PlayerManager.setInPipMode(isInPictureInPictureMode)
    }

    private fun initPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        ContextCompat.registerReceiver(
            this,
            pipActionReceiver,
            IntentFilter(Constants.Player.Pip.ACTION_PLAY_PAUSE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        pipReceiverRegistered = true

        // The controller connects asynchronously (and may reconnect): (re-)attach the listener
        // that keeps the PiP remote action synced with the playback state.
        lifecycleScope.launch {
            PlayerManager.controllerState.collect { controller ->
                controller?.removeListener(pipPlayerListener)
                controller?.addListener(pipPlayerListener)
            }
        }
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isInPipModeState.value) {
            return
        }
        setPictureInPictureParams(buildPipParams(PlayerManager.isPlaying))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipParams(isPlaying: Boolean): PictureInPictureParams {
        val playPauseIntent = PendingIntent.getBroadcast(
            this,
            Constants.Player.Pip.REQUEST_CODE_PLAY_PAUSE,
            Intent(Constants.Player.Pip.ACTION_PLAY_PAUSE).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val title = getString(if (isPlaying) R.string.pause else R.string.play)

        return PictureInPictureParams.Builder()
            .setAspectRatio(
                Rational(
                    Constants.Player.Pip.ASPECT_RATIO_WIDTH,
                    Constants.Player.Pip.ASPECT_RATIO_HEIGHT
                )
            )
            .setActions(
                listOf(
                    RemoteAction(
                        Icon.createWithResource(this, iconRes),
                        title,
                        title,
                        playPauseIntent
                    )
                )
            )
            .build()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
        handleViewIntent(intent)
        handleVoiceSearchIntent(intent)
    }

    override fun attachBaseContext(newBase: Context) {
        // Synchronous SharedPreferences mirror read: no DataStore I/O before the first frame.
        val useSpecialLanguage = StartupPreferences.useSpecialLanguage(newBase)

        val context = if (useSpecialLanguage) {
            val locale = java.util.Locale.forLanguageTag(Constants.Locale.Special.CODE)
            val config = Configuration(newBase.resources.configuration)
            config.setLocales(android.os.LocaleList(locale))
            newBase.createConfigurationContext(config)
        } else {
            newBase
        }

        super.attachBaseContext(context)
    }

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) {
            return
        }
        if (intent.type != "text/plain") {
            return
        }

        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return

        val urlRegex = Regex(
            YoutubeApiConstants.URL_REGEX
        )

        val url = urlRegex.find(text)?.value ?: return

        val videoId = YoutubeDataExtractor.extractYouTubeVideoId(url) ?: return

        playVideoFromId(videoId)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) {
            return
        }
        val data: Uri = intent.data ?: return

        val mimeType = intent.type ?: contentResolver.getType(data)
        if (data.scheme == ContentResolver.SCHEME_CONTENT && ExternalAudio.isAudio(mimeType)) {
            PlayerManager.playSong(ExternalAudio.toSong(contentResolver, data))
            return
        }

        JamInvites.parse(data.toString())?.let { invite ->
            JamManager.offerInvite(invite)
            return
        }

        val videoId = YoutubeDataExtractor.extractYouTubeVideoId(data.toString()) ?: return
        playVideoFromId(videoId)
    }

    /**
     * Handles Assistant and Android Auto voice launches such as "play FTR on Umihi".
     * PlayerManager retains the requested song while its media controller is still connecting,
     * so a voice command received during cold start is not lost.
     */
    private fun handleVoiceSearchIntent(intent: Intent?) {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) {
            return
        }

        val query = listOf(
            intent.getStringExtra(SearchManager.QUERY),
            intent.getStringExtra(MediaStore.EXTRA_MEDIA_TITLE),
            intent.getStringExtra(MediaStore.EXTRA_MEDIA_ARTIST),
            intent.getStringExtra(MediaStore.EXTRA_MEDIA_ALBUM),
            intent.getStringExtra(MediaStore.EXTRA_MEDIA_PLAYLIST),
            intent.getStringExtra(Intent.EXTRA_TEXT),
        ).firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

        if (query.isBlank()) {
            PlayerManager.currentController?.play()
            return
        }

        lifecycleScope.launch {
            val result = songRepository.search(query, SearchFilter.Songs)
                .first { it !is ApiResult.Loading }
            val song = (result as? ApiResult.Success)?.data?.songs?.firstOrNull()
            if (song != null) {
                PlayerManager.playSong(song)
            } else {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.no_voice_search_result, query),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }


    private fun playVideoFromId(id: String) {
        lifecycleScope.launch {
            songRepository.getSongInfo(id).collect { apiResult ->
                when (apiResult) {
                    is ApiResult.Error -> {
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.get_song_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    ApiResult.Loading -> {}
                    is ApiResult.Success -> {
                        PlayerManager.playSong(apiResult.data)
                    }
                }
            }
        }

    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_CODE_NOTIFICATION_PERMISSION,
            )
        }
    }

    private fun initCaoc() {
        CaocConfig.Builder.create()
            .backgroundMode(CaocConfig.BACKGROUND_MODE_CRASH)
            .trackActivities(true)
            .apply()
    }
}

/**
 * Native dependency-light first frame. It replaces the platform splash with the app's actual
 * layout silhouette while Compose, downloadable fonts and navigation load for the following frame.
 */
private class StartupShellView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val isDark = resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val backgroundPaint = Paint().apply {
        color = if (isDark) 0xFF222017.toInt() else 0xFFFFF8E7.toInt()
    }
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDark) 0xFF3A382F.toInt() else 0xFFE8E0CC.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPaint(backgroundPaint)

        val horizontal = 20f * density
        val top = 48f * density
        val radius = 12f * density
        canvas.drawRoundRect(
            RectF(horizontal, top, width * 0.44f, top + 28f * density),
            radius,
            radius,
            placeholderPaint,
        )

        var itemTop = top + 56f * density
        repeat(3) {
            canvas.drawRoundRect(
                RectF(horizontal, itemTop, width - horizontal, itemTop + 88f * density),
                radius,
                radius,
                placeholderPaint,
            )
            itemTop += 100f * density
        }
    }
}
