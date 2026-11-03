package ca.ilianokokoro.umihi.music.services

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.ConnectivityManager
import android.net.Uri
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.glance.appwidget.updateAll
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.automix.LevelAnalysis
import ca.ilianokokoro.umihi.music.automix.TrackAnalysis
import ca.ilianokokoro.umihi.music.data.database.AppDatabase
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.ExoCache
import ca.ilianokokoro.umihi.music.core.NotInterestedFilter
import ca.ilianokokoro.umihi.music.core.PlaybackLoudnessStore
import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.core.cast.CastManager
import ca.ilianokokoro.umihi.music.core.cast.planCastHandover
import ca.ilianokokoro.umihi.music.core.datasources.HdVideoMediaSourceFactory
import ca.ilianokokoro.umihi.music.core.datasources.YoutubeDataSourceFactory
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper
import ca.ilianokokoro.umihi.music.core.jam.JamBridge
import ca.ilianokokoro.umihi.music.core.managers.AudioOutputManager
import ca.ilianokokoro.umihi.music.core.managers.EqualizerManager
import ca.ilianokokoro.umihi.music.core.managers.EqualizerProfileStore
import ca.ilianokokoro.umihi.music.core.managers.NotInterestedStore
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.core.managers.QueueSourceType
import ca.ilianokokoro.umihi.music.core.playback.AutomixAnalyses
import ca.ilianokokoro.umihi.music.core.playback.AutomixPlanner
import ca.ilianokokoro.umihi.music.core.playback.CrossfadeController
import ca.ilianokokoro.umihi.music.core.playback.CrossfadeRules
import ca.ilianokokoro.umihi.music.core.playback.PlannedFade
import ca.ilianokokoro.umihi.music.core.playback.TrackLevelReader
import ca.ilianokokoro.umihi.music.core.reco.TasteProfileRepository
import ca.ilianokokoro.umihi.music.core.scrobbling.DatastorePendingScrobbleStore
import ca.ilianokokoro.umihi.music.core.scrobbling.LastFmSink
import ca.ilianokokoro.umihi.music.core.scrobbling.ListenBrainzSink
import ca.ilianokokoro.umihi.music.core.scrobbling.ScrobbleCoordinator
import ca.ilianokokoro.umihi.music.core.scrobbling.ScrobbleDelivery
import ca.ilianokokoro.umihi.music.core.scrobbling.toScrobbleTrack
import ca.ilianokokoro.umihi.music.core.youtube.VideoQualitySelector
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeStatsTracker
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeStreamResolver
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.HistoryRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlayEventRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlayerQueueRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlaylistRepository
import ca.ilianokokoro.umihi.music.data.repositories.RadioRepository
import ca.ilianokokoro.umihi.music.data.repositories.SongRepository
import ca.ilianokokoro.umihi.music.extensions.cappedTo
import ca.ilianokokoro.umihi.music.extensions.toSong
import ca.ilianokokoro.umihi.music.models.PlayEvent
import ca.ilianokokoro.umihi.music.models.PlaybackAudioInfo
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import ca.ilianokokoro.umihi.music.models.adapters.toTrack
import ca.ilianokokoro.umihi.music.wear.WearPlaybackStatePublisher
import ca.ilianokokoro.umihi.music.widget.PlaybackWidget
import ca.ilianokokoro.umihi.music.widget.PlaybackWidgetStateStore
import java.io.File
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

@UnstableApi
class PlaybackService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var exoCache: ExoCache
    private lateinit var player: ExoPlayer
    private lateinit var crossfade: CrossfadeController
    private lateinit var jam: JamBridge
    private val activePlayer: Player
        get() = mediaLibrarySession?.player ?: player
    @Volatile private var isCasting = false
    private lateinit var datastoreRepository: DatastoreRepository
    private var currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    private val songRepository = SongRepository()
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var playerQueueRepository: PlayerQueueRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queuePersistenceJob: Job? = null
    private var volumeFadeJob: Job? = null
    @Volatile private var normalizedVolume = 1f
    @Volatile private var normalizeVolumeEnabled = true
    @Volatile private var crossfadeMs = 0L
    @Volatile private var smartTransitionsEnabled = true
    @Volatile private var automixEnabled = false
    private lateinit var automix: AutomixAnalyses
    @Volatile private var resumeOnAudioConnect = false
    // Set only when ExoPlayer pauses because the output vanished (becoming-noisy), so a manual
    // pause never triggers the resume-on-connect behaviour.
    @Volatile private var pausedByBecomingNoisy = false
    @Volatile private var fadeInActive = false
    @Volatile private var queueRestoreComplete = false
    @Volatile private var isRestoringQueue = false
    private var consecutivePlaybackErrors = 0
    private val historyRepository by lazy { HistoryRepository(applicationContext) }
    private val playEventRepository by lazy { PlayEventRepository(applicationContext) }
    private val playEventTracker = PlayEventTracker()
    private val scrobbles by lazy {
        ScrobbleCoordinator(
            scope = serviceScope,
            delivery = ScrobbleDelivery(
                store = DatastorePendingScrobbleStore(datastoreRepository),
                sinks = listOf(
                    ListenBrainzSink(datastoreRepository),
                    LastFmSink(datastoreRepository),
                ),
            ),
        )
    }
    private val radioRepository = RadioRepository()
    private var lastRadioSeedId: String? = null

    private var audioManager: AudioManager? = null
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            AudioOutputManager.refresh(applicationContext)
            applyEqualizerForCurrentOutput()
            maybeResumeOnAudioConnect(addedDevices)
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            AudioOutputManager.refresh(applicationContext)
            applyEqualizerForCurrentOutput()
        }
    }

    private lateinit var callback: UmihiMediaLibraryCallback
    private lateinit var sessionListener: Player.Listener
    private lateinit var wearPlaybackStatePublisher: WearPlaybackStatePublisher

    internal fun publishWearPlaybackState() {
        wearPlaybackStatePublisher.publish(activePlayer)
    }


    @OptIn(UnstableApi::class, ExperimentalApi::class)
    override fun onCreate() {
        super.onCreate()

        datastoreRepository = DatastoreRepository(applicationContext)
        playlistRepository = PlaylistRepository(application)
        playerQueueRepository = PlayerQueueRepository(applicationContext)
        scrobbles.start()
        exoCache = ExoCache(application)

        val httpDataSourceFactory = OkHttpDataSource.Factory(UmihiHttpClient.client)
            .setUserAgent(Util.getUserAgent(this, packageName))

        val defaultDataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(exoCache.cache)
            .setUpstreamDataSourceFactory(defaultDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val resolvingFactory = YoutubeDataSourceFactory(application, cacheDataSourceFactory)

        val audioOffloadPreferences =
            TrackSelectionParameters.AudioOffloadPreferences.Builder()
                .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                .setIsGaplessSupportRequired(true)
                .setIsSpeedChangeSupportRequired(true)
                .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                Constants.ExoPlayer.MIN_BUFFER_MS,
                Constants.ExoPlayer.MAX_BUFFER_MS,
                Constants.ExoPlayer.BUFFER_FOR_PLAYBACK_MS,
                Constants.ExoPlayer.BUFFER_AFTER_REBUFFER_MS,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(this)
            .experimentalSetEnableMediaCodecVideoRendererPrewarming(true)

        player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .setDeviceVolumeControlEnabled(true)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                HdVideoMediaSourceFactory(
                    delegate = DefaultMediaSourceFactory(resolvingFactory),
                    // Read per item: the preference can change between two tracks of a queue.
                    wantsMergedVideo = {
                        runBlocking {
                            VideoQualitySelector.needsSeparateAudio(
                                DatastoreRepository(applicationContext).settings.first().videoQuality
                            )
                        }
                    },
                )
            )
            .build()
        val levelReader = TrackLevelReader(resolvingFactory)
        automix = AutomixAnalyses(
            scope = CoroutineScope(serviceScope.coroutineContext + Dispatchers.Main),
            analyze = { id -> analyzeForAutomix(id, levelReader) },
        )
        crossfade = CrossfadeController(
            main = player,
            createTail = {
                // Audio focus and output changes stay with the main player, the only one the
                // session knows. The tail just follows it.
                ExoPlayer.Builder(this)
                    .setAudioAttributes(audioAttributes, false)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
                    .build()
            },
            scope = CoroutineScope(serviceScope.coroutineContext + Dispatchers.Main),
            planFade = ::planFade,
            isAllowed = { !isCasting && (crossfadeMs > 0L || automixEnabled) },
            targetVolume = { normalizedVolume },
        )
        crossfade.start()
        jam = JamBridge(
            player = { activePlayer },
            scope = CoroutineScope(serviceScope.coroutineContext + Dispatchers.Main),
            isSuspended = { isRestoringQueue },
        )
        jam.start()
        player.addAnalyticsListener(
            object : AnalyticsListener {
                override fun onAudioInputFormatChanged(
                    eventTime: AnalyticsListener.EventTime,
                    format: Format,
                    decoderReuseEvaluation: DecoderReuseEvaluation?
                ) {
                    PlayerManager.updatePlaybackInfo(
                        PlaybackAudioInfo(
                            format = Constants.ExoPlayer.AUDIO_MIME_MAP[format.sampleMimeType]
                                ?: format.sampleMimeType,
                            sampleRate = format.sampleRate
                                .takeIf { it != Format.NO_VALUE },
                            bitrate = format.bitrate
                                .takeIf { it != Format.NO_VALUE },
                            channelCount = format.channelCount
                                .takeIf { it != Format.NO_VALUE }
                        )
                    )
                }
            }
        )

        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(audioOffloadPreferences)
                .build()

        serviceScope.launch {
            val settings = datastoreRepository.settings.first()
            val mode = if (settings.useAudioOffload) {
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
            } else {
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
            }
            withContext(Dispatchers.Main) {
                player.trackSelectionParameters =
                    player.trackSelectionParameters
                        .buildUpon()
                        .setAudioOffloadPreferences(
                            player.trackSelectionParameters.audioOffloadPreferences
                                .buildUpon()
                                .setAudioOffloadMode(mode)
                                .build()
                        )
                        .build()
            }
        }

        sessionListener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                    jam.onLocalQueueChanged()
                }
                scheduleQueuePersistence()
                wearPlaybackStatePublisher.publish(activePlayer)
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
                jam.onLocalTrack(mediaItem)
                scrobbles.onTrackChanged(
                    track = mediaItem?.toScrobbleTrack(System.currentTimeMillis()),
                    isPlaying = activePlayer.isPlaying,
                )
                // Finalizes the previous track's play event and opens one for the new track.
                // Safe during a queue restore: a play that never ran is never recorded.
                persistPlayEvent(
                    playEventTracker.onMediaItemTransition(
                        youtubeId = mediaItem?.mediaId,
                        isPlaying = activePlayer.isPlaying,
                        nowMs = System.currentTimeMillis(),
                        audioOutput = AudioOutputManager.currentOutput.value.name,
                        sourceContext = playEventSourceContext(),
                    )
                )
                scheduleQueuePersistence()
                PlayerManager.updatePlaybackInfo(PlaybackAudioInfo())
                updatePlaybackWidget(
                    mediaItem,
                    mediaItem?.mediaMetadata?.artworkData?.cappedTo(),
                )
                updateCurrentMediaItemThumbnail(mediaItem)
                applyAudioOffloadForItem(mediaItem)
                val songId = mediaItem?.mediaId ?: return
                applyTrackVolume(songId, reason)
                // Restoring the last queue is not a new listen and must not affect history/stats.
                if (isRestoringQueue) return
                val playedSong = mediaItem.toSong()
                serviceScope.launch {
                    val settings = datastoreRepository.getSettings()
                    YoutubeStatsTracker.onPlaybackStarted(songId, settings)
                    historyRepository.record(playedSong.toTrack(), System.currentTimeMillis())
                    maybeAppendRadio(playedSong, settings)
                }
                prefetchNextStreamUrl()
                wearPlaybackStatePublisher.publish(activePlayer)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                wearPlaybackStatePublisher.publish(activePlayer)
                if (playbackState == Player.STATE_IDLE) {
                    YoutubeStatsTracker.stopPlaybackTracking()
                }
                if (playbackState == Player.STATE_READY) {
                    // A track played successfully: reset the failure guard.
                    consecutivePlaybackErrors = 0
                    playEventTracker.onDurationKnown(activePlayer.duration)
                    scrobbles.onDurationKnown(activePlayer.duration)
                }
                if (playbackState == Player.STATE_ENDED) {
                    // The queue played out with no further transition: close the last play here.
                    playEventTracker.onDurationKnown(activePlayer.duration)
                    persistPlayEvent(playEventTracker.onPlaybackEnded(System.currentTimeMillis()))
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK &&
                    oldPosition.mediaItemIndex == newPosition.mediaItemIndex
                ) {
                    jam.onLocalSeek(newPosition.positionMs)
                }
                scheduleQueuePersistence()
                wearPlaybackStatePublisher.publish(activePlayer)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                scheduleQueuePersistence()
                wearPlaybackStatePublisher.publish(activePlayer)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                scheduleQueuePersistence()
                wearPlaybackStatePublisher.publish(activePlayer)
            }

            override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
                wearPlaybackStatePublisher.publish(activePlayer)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playEventTracker.onIsPlayingChanged(isPlaying, System.currentTimeMillis())
                scrobbles.onIsPlayingChanged(isPlaying)
                if (!isPlaying) {
                    scheduleQueuePersistence()
                    serviceScope.launch { persistPlaybackPosition() }
                }
                updatePlaybackWidget(activePlayer.currentMediaItem)
                wearPlaybackStatePublisher.publish(activePlayer)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                jam.onLocalPlayWhenReady(playWhenReady)
                // Only an unplug/disconnect pause arms the resume-on-connect guard; any other
                // playWhenReady change (manual pause, resume, focus loss) disarms it.
                pausedByBecomingNoisy = !playWhenReady &&
                        reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
            }

            override fun onPlayerError(error: PlaybackException) {
                consecutivePlaybackErrors++
                val queueSize = activePlayer.mediaItemCount
                // Stop instead of skipping endlessly when every track fails (e.g. offline).
                if (consecutivePlaybackErrors >= queueSize ||
                    consecutivePlaybackErrors >= MAX_CONSECUTIVE_PLAYBACK_ERRORS
                ) {
                    printe(
                        message = "Playback stopped after $consecutivePlaybackErrors consecutive errors",
                        exception = error,
                    )
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.playback_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                    activePlayer.pause()
                    return
                }
                activePlayer.seekToNext()
                activePlayer.prepare()
            }

            // Expose audio session ID for third-party equalizer apps
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (currentAudioSessionId == audioSessionId) {
                    return
                }

                if (currentAudioSessionId > 0) {
                    sendBroadcast(
                        Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentAudioSessionId)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                        }
                    )
                }

                currentAudioSessionId = audioSessionId

                // Bind the in-app equalizer to the ExoPlayer session (re-applies saved config).
                EqualizerManager.attach(audioSessionId)

                if (audioSessionId > 0) {
                    sendBroadcast(
                        Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                        }
                    )
                }
            }
        }
        wearPlaybackStatePublisher = WearPlaybackStatePublisher(applicationContext)
        player.addListener(sessionListener)

        val intent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        callback = UmihiMediaLibraryCallback(
            service = this,
            serviceScope = serviceScope,
            datastoreRepository = datastoreRepository,
            songRepository = songRepository,
            playlistRepository = playlistRepository
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, callback)
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(CacheBitmapLoader(DataSourceBitmapLoader.Builder(this).build()))
            .build()
        wearPlaybackStatePublisher.publish(activePlayer)

        observeCastSessions()
        restoreQueueAndStartPersistence()
        startAudioVolumeManagement()
        startAudioOutputEqualizer()

        // Retention: listening signals older than a year no longer describe current taste.
        serviceScope.launch {
            playEventRepository.purgeExpired(System.currentTimeMillis())
        }

        // Publishes the persisted taste profile immediately, then recomputes it in the background.
        TasteProfileRepository.getInstance(applicationContext).start()
    }

    /**
     * Provenance stored on each play event: the stable source identifier when the feature that
     * built the queue declared one (e.g. "daily-mix" — language-independent, so downstream
     * feedback can key on it), else the collection name the queue was built from, or the
     * "radio" marker for seed radios, which have no name of their own.
     */
    private fun playEventSourceContext(): String? =
        PlayerManager.queueSourceId.value
            ?: PlayerManager.queueSourceName.value
            ?: PLAY_EVENT_SOURCE_RADIO.takeIf { PlayerManager.isRadioQueue.value }

    private fun persistPlayEvent(event: PlayEvent?) {
        if (event == null) return
        serviceScope.launch {
            playEventRepository.record(event)
            // Debounced: a burst of skips ends in a single taste profile recomputation.
            TasteProfileRepository.getInstance(applicationContext).onTrackFinished()
        }
    }

    /**
     * Keeps the equalizer profile in sync with the active audio output: applies
     * the stored profile for the current output on start, then re-applies the
     * matching one whenever the output changes (e.g. plugging in headphones or
     * connecting a Bluetooth speaker).
     */
    private fun startAudioOutputEqualizer() {
        audioManager = (getSystemService(AUDIO_SERVICE) as? AudioManager)?.also {
            it.registerAudioDeviceCallback(audioDeviceCallback, null)
        }
        AudioOutputManager.refresh(applicationContext)
        applyEqualizerForCurrentOutput()
    }

    private fun applyEqualizerForCurrentOutput() {
        serviceScope.launch {
            val output = AudioOutputManager.currentOutput.value
            val profile = EqualizerProfileStore.load(applicationContext, output)
            EqualizerManager.setDesiredConfig(
                enabled = profile.enabled,
                preset = profile.preset,
                bandLevels = profile.bands,
                bassBoostStrength = profile.bassBoost,
            )
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaLibrarySession? = mediaLibrarySession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        when (val action = intent?.action) {
            ACTION_WIDGET_PLAY_PAUSE,
            ACTION_WIDGET_PREVIOUS,
            ACTION_WIDGET_NEXT -> handleWidgetAction(action)
        }
        return result
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleQueuePersistence(immediate = true)
        val player = mediaLibrarySession?.player
        if (player == null || player.mediaItemCount == 0) {
            pauseAllPlayersAndStopSelf()
        }
    }

    override fun onDestroy() {
        queuePersistenceJob?.cancel()
        volumeFadeJob?.cancel()
        if (::crossfade.isInitialized) crossfade.release()
        if (::automix.isInitialized) automix.release()
        val playerReady = ::player.isInitialized
        val snapshot = if (queueRestoreComplete && playerReady) captureQueueSnapshot() else null
        val currentItem = if (playerReady) activePlayer.currentMediaItem else null
        val interruptedPlay = playEventTracker.onServiceDestroyed(System.currentTimeMillis())
        // Blocking on purpose: serviceScope is about to die, and the queue snapshot and the
        // interrupted play (exactly the signal APP_KILLED exists for) must reach disk first. The
        // writes run concurrently under one deadline, so a slow disk or widget host cannot hold
        // the main thread long enough to raise an ANR.
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(SHUTDOWN_WRITE_TIMEOUT_MS) {
                snapshot?.let { launch { persistQueueSnapshot(it) } }
                interruptedPlay?.let { launch { playEventRepository.record(it) } }
                if (playerReady) launch {
                    PlaybackWidgetStateStore.write(
                        context = applicationContext,
                        track = currentItem?.toSong()?.toTrack(),
                        isPlaying = false,
                        artworkBytes = currentItem?.mediaMetadata?.artworkData?.cappedTo(),
                    )
                    PlaybackWidget().updateAll(applicationContext)
                }
            }
        }
        audioManager?.unregisterAudioDeviceCallback(audioDeviceCallback)
        serviceScope.cancel()
        mediaLibrarySession?.run {
            if (currentAudioSessionId > 0) {
                sendBroadcast(
                    Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentAudioSessionId)
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    }
                )
            }
            release()
        }
        mediaLibrarySession = null
        EqualizerManager.release()
        // Released unconditionally: a null session must not leak the player or cache lock.
        if (::player.isInitialized) player.release()
        if (::exoCache.isInitialized) exoCache.release()
        super.onDestroy()
    }

    private fun restoreQueueAndStartPersistence() {
        serviceScope.launch {
            // Hydrate before restoring or fetching radio so rejected tracks cannot re-enter through
            // a persisted suggestion after a cold start.
            NotInterestedStore.hydrate(applicationContext, serviceScope)
            val restored = playerQueueRepository.restore()
            withContext(Dispatchers.Main) {
                if (restored != null && player.mediaItemCount == 0) {
                    isRestoringQueue = true
                    val hiddenIndices = NotInterestedFilter.hiddenSuggestionIndices(
                        queue = restored.songs,
                        hidden = NotInterestedStore.hiddenIds,
                        currentIndex = restored.currentIndex,
                    ).toSet()
                    player.setMediaItems(
                        restored.songs
                            .filterIndexed { index, _ -> index !in hiddenIndices }
                            .map { it.mediaItem },
                        NotInterestedFilter.indexAfterRemovals(
                            restored.currentIndex,
                            hiddenIndices,
                        ),
                        restored.positionMs,
                    )
                    player.repeatMode = restored.repeatMode
                    player.shuffleModeEnabled = restored.shuffleEnabled
                    player.prepare()
                    player.playWhenReady = false
                    PlayerManager.restoreQueueProvenance(
                        sourceName = restored.sourceName,
                        isRadio = restored.isRadio,
                        sourceId = restored.sourceId,
                    )
                    isRestoringQueue = false
                }
                queueRestoreComplete = true
            }
        }

        serviceScope.launch {
            while (true) {
                delay(QUEUE_POSITION_SAVE_INTERVAL_MS)
                val isPlaying = withContext(Dispatchers.Main) { activePlayer.isPlaying }
                if (queueRestoreComplete && isPlaying) {
                    persistQueueSnapshot()
                    persistPlaybackPosition()
                }
            }
        }
    }

    private fun observeCastSessions() {
        serviceScope.launch {
            CastManager.sessionPlayer.collect { castPlayer ->
                withContext(Dispatchers.Main.immediate) {
                    if (castPlayer != null) {
                        startCasting(castPlayer)
                    } else {
                        stopCasting()
                    }
                }
            }
        }
    }

    private fun startCasting(castPlayer: Player) {
        if (isCasting) return
        val session = mediaLibrarySession ?: return
        if (castPlayer.applicationLooper != player.applicationLooper) {
            Toast.makeText(
                applicationContext,
                getString(R.string.cast_unavailable),
                Toast.LENGTH_LONG,
            ).show()
            CastManager.endCurrentSession()
            return
        }

        val snapshot = captureQueueSnapshot()
        val plan = planCastHandover(
            songs = snapshot.songs,
            currentIndex = snapshot.currentIndex,
            positionMs = snapshot.positionMs,
        )
        if (plan.songs.isEmpty()) {
            Toast.makeText(
                applicationContext,
                getString(
                    if (snapshot.songs.size == 1) {
                        R.string.cast_error_local_track
                    } else {
                        R.string.cast_no_compatible_tracks
                    }
                ),
                Toast.LENGTH_LONG,
            ).show()
            CastManager.endCurrentSession()
            return
        }

        val wasPlaying = player.playWhenReady
        castPlayer.setMediaItems(
            plan.songs.map { it.mediaItem },
            plan.startIndex,
            plan.startPositionMs,
        )
        castPlayer.repeatMode = snapshot.repeatMode
        castPlayer.shuffleModeEnabled = snapshot.shuffleEnabled

        player.removeListener(sessionListener)
        try {
            session.setPlayer(castPlayer)
            isCasting = true
            castPlayer.addListener(sessionListener)
        } catch (_: IllegalArgumentException) {
            player.addListener(sessionListener)
            CastManager.endCurrentSession()
            Toast.makeText(
                applicationContext,
                getString(R.string.cast_unavailable),
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        castPlayer.prepare()
        castPlayer.playWhenReady = wasPlaying
        player.pause()
        if (plan.skippedLocalCount > 0) {
            Toast.makeText(
                applicationContext,
                resources.getQuantityString(
                    R.plurals.cast_local_tracks_skipped,
                    plan.skippedLocalCount,
                    plan.skippedLocalCount,
                ),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private fun stopCasting() {
        if (!isCasting) return
        val session = mediaLibrarySession ?: return
        val castPlayer = activePlayer
        val wasPlaying = castPlayer.playWhenReady
        val items = List(castPlayer.mediaItemCount) { index ->
            castPlayer.getMediaItemAt(index).toSong().mediaItem
        }
        val currentIndex = castPlayer.currentMediaItemIndex
            .coerceIn(0, (items.size - 1).coerceAtLeast(0))
        val positionMs = castPlayer.currentPosition.coerceAtLeast(0L)
        val repeatMode = castPlayer.repeatMode
        val shuffleEnabled = castPlayer.shuffleModeEnabled

        if (items.isEmpty()) {
            player.clearMediaItems()
        } else {
            player.setMediaItems(items, currentIndex, positionMs)
        }
        player.repeatMode = repeatMode
        player.shuffleModeEnabled = shuffleEnabled

        castPlayer.removeListener(sessionListener)
        session.setPlayer(player)
        isCasting = false
        player.addListener(sessionListener)
        if (items.isNotEmpty()) player.prepare()
        player.playWhenReady = wasPlaying
        castPlayer.pause()
        castPlayer.release()
    }

    /**
     * Persists the current track's playback position into the listening history so a partially
     * listened title can later be resumed. Reuses the existing periodic save loop (throttled) plus
     * a save on pause; a no-op when there is no recorded play row for the current track.
     */
    private suspend fun persistPlaybackPosition() {
        if (isRestoringQueue) return
        val snapshot = withContext(Dispatchers.Main) {
            val item = activePlayer.currentMediaItem ?: return@withContext null
            Triple(item.mediaId, activePlayer.currentPosition, activePlayer.duration)
        } ?: return
        val (songId, positionMs, durationMs) = snapshot
        if (songId.isBlank() || positionMs <= 0) return
        historyRepository.updatePosition(
            youtubeId = songId,
            positionMs = positionMs,
            durationMs = durationMs.coerceAtLeast(0L),
        )
    }

    private fun planFade(durationMs: Long): PlannedFade? {
        if (!automixEnabled) return CrossfadeRules.plainFade(durationMs, fadeDurationMs())
        val current = player.currentMediaItem ?: return null
        val next = player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }
            ?.let(player::getMediaItemAt)
            ?: return null
        automix.want(listOf(current.mediaId, next.mediaId))
        val isAlbum = PlayerManager.queueProvenance.value?.type == QueueSourceType.Album
        return AutomixPlanner.plan(
            current = current,
            next = next,
            currentAnalysis = automix[current.mediaId],
            nextAnalysis = automix[next.mediaId],
            positionMs = player.currentPosition,
            durationMs = durationMs,
            fadeSeconds = automixFadeSeconds(isAlbum),
            playingAnAlbum = isAlbum,
        )
    }

    /** What Automix falls back on where a track tells it nothing: the listener's fade if any. */
    private fun automixFadeSeconds(isAlbum: Boolean): Double {
        val configured = if (isAlbum) 0L else maxOf(crossfadeMs, fadeDurationMs())
        return (if (configured >= CrossfadeRules.MIN_OVERLAP_MS) configured else DEFAULT_AUTOMIX_FADE_MS) / 1000.0
    }

    /**
     * Reads a track's loudness for Automix. Reading fetches whatever is not stored yet, so on a
     * metered connection only files already on the device are read.
     */
    private suspend fun analyzeForAutomix(id: String, reader: TrackLevelReader): TrackAnalysis? {
        val item = findQueuedItem(id) ?: return null
        val uri = item.localConfiguration?.uri ?: return null
        // A download keeps its YouTube address in the queue; playback resolves it to the file.
        val isLocal = uri.scheme == "file" || uri.scheme == "content" ||
            AppDatabase.getInstance(application).songRepository().getSong(id)?.audioFilePath != null
        val connectivity = getSystemService(ConnectivityManager::class.java)
        if (!isLocal && connectivity?.isActiveNetworkMetered != false) return null
        val levels = reader.read(uri) ?: return null
        return withContext(Dispatchers.Default) { LevelAnalysis.analyze(id, levels) }
    }

    private fun findQueuedItem(id: String): MediaItem? =
        (0 until player.mediaItemCount).asSequence()
            .map(player::getMediaItemAt)
            .firstOrNull { it.mediaId == id }

    /**
     * Fade length applied around an automatic track transition. When the user has set a crossfade
     * duration it wins; otherwise the short default fade keeps loudness-normalized gapless
     * transitions click-free. Re-read at each transition so changes apply without a restart.
     */
    private fun fadeDurationMs(): Long {
        if (!smartTransitionsEnabled) {
            return if (crossfadeMs > 0L) crossfadeMs else AUDIO_FADE_DURATION_MS
        }
        return when (PlayerManager.queueProvenance.value?.type) {
            QueueSourceType.Album -> AUDIO_FADE_DURATION_MS
            QueueSourceType.Radio -> 6_000L
            QueueSourceType.Playlist -> 4_000L
            QueueSourceType.Artist -> 3_000L
            null -> if (crossfadeMs > 0L) crossfadeMs else AUDIO_FADE_DURATION_MS
        }
    }

    private fun startAudioVolumeManagement() {
        serviceScope.launch {
            datastoreRepository.settings.collectLatest { settings ->
                crossfadeMs = settings.crossfadeSeconds.coerceIn(0, 12) * 1000L
                smartTransitionsEnabled = settings.smartTransitions
                automixEnabled = settings.automix
                resumeOnAudioConnect = settings.resumeOnAudioConnect
                val normalizeChanged = normalizeVolumeEnabled != settings.normalizeVolume
                normalizeVolumeEnabled = settings.normalizeVolume
                if (normalizeChanged) {
                    // Re-apply the current track's volume right away so toggling the setting is
                    // audible without waiting for the next track transition.
                    withContext(Dispatchers.Main) {
                        if (isCasting) return@withContext
                        val videoId = player.currentMediaItem?.mediaId ?: return@withContext
                        normalizedVolume = trackVolumeFor(videoId)
                        if (!fadeInActive && !crossfade.isFading) player.volume = normalizedVolume
                    }
                }
            }
        }

        serviceScope.launch {
            PlaybackLoudnessStore.updates.collectLatest { update ->
                withContext(Dispatchers.Main) {
                    if (isCasting || !normalizeVolumeEnabled) return@withContext
                    if (player.currentMediaItem?.mediaId == update.videoId) {
                        normalizedVolume = update.volume
                        if (!fadeInActive && !crossfade.isFading) player.volume = normalizedVolume
                    }
                }
            }
        }

        serviceScope.launch {
            while (true) {
                delay(AUDIO_FADE_POLL_INTERVAL_MS)
                withContext(Dispatchers.Main) {
                    if (isCasting || crossfade.isActive) return@withContext
                    if (fadeInActive || !player.isPlaying || !player.hasNextMediaItem()) {
                        return@withContext
                    }
                    val fadeMs = fadeDurationMs()
                    val duration = player.duration
                    val remaining = duration - player.currentPosition
                    player.volume = if (duration > 0 && remaining in 1..fadeMs) {
                        normalizedVolume * (remaining.toFloat() / fadeMs)
                    } else {
                        normalizedVolume
                    }
                }
            }
        }
    }

    /** ReplayGain-style attenuation for [videoId], or full volume when normalization is off. */
    private fun trackVolumeFor(videoId: String): Float =
        if (normalizeVolumeEnabled) PlaybackLoudnessStore.volumeFor(videoId) else 1f

    /**
     * Resumes playback when an audio output (headphones, Bluetooth) reconnects, but only when the
     * pause came from the matching disconnect (becoming-noisy) and the user opted in. A manual
     * pause never resumes.
     */
    private fun maybeResumeOnAudioConnect(addedDevices: Array<out AudioDeviceInfo>?) {
        if (!resumeOnAudioConnect || !pausedByBecomingNoisy) return
        val headphoneConnected = addedDevices.orEmpty().any { device ->
            device.isSink && device.type in HEADPHONE_DEVICE_TYPES
        }
        if (!headphoneConnected) return
        pausedByBecomingNoisy = false
        serviceScope.launch {
            withContext(Dispatchers.Main) {
                if (isCasting) return@withContext
                val current = activePlayer
                if (!current.playWhenReady && current.mediaItemCount > 0) {
                    if (current.playbackState == Player.STATE_IDLE) current.prepare()
                    current.play()
                }
            }
        }
    }

    private fun applyTrackVolume(videoId: String, transitionReason: Int) {
        if (isCasting) return
        normalizedVolume = trackVolumeFor(videoId)
        volumeFadeJob?.cancel()
        // The crossfade is bringing this track in and owns the volume until it ends.
        if (crossfade.isFading) {
            fadeInActive = false
            return
        }
        if (transitionReason != Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            fadeInActive = false
            player.volume = normalizedVolume
            return
        }

        fadeInActive = true
        player.volume = 0f
        volumeFadeJob = serviceScope.launch {
            val steps = (fadeDurationMs() / AUDIO_FADE_POLL_INTERVAL_MS).toInt()
                .coerceAtLeast(1)
            repeat(steps) { step ->
                delay(AUDIO_FADE_POLL_INTERVAL_MS)
                withContext(Dispatchers.Main) {
                    player.volume = normalizedVolume * ((step + 1f) / steps)
                }
            }
            fadeInActive = false
        }
    }

    private fun scheduleQueuePersistence(immediate: Boolean = false) {
        if (!queueRestoreComplete || isRestoringQueue) return
        queuePersistenceJob?.cancel()
        queuePersistenceJob = serviceScope.launch {
            if (!immediate) delay(QUEUE_CHANGE_DEBOUNCE_MS)
            persistQueueSnapshot()
        }
    }

    private suspend fun persistQueueSnapshot() {
        val snapshot = withContext(Dispatchers.Main) { captureQueueSnapshot() }
        persistQueueSnapshot(snapshot)
    }

    private suspend fun persistQueueSnapshot(snapshot: QueueSnapshot) {
        playerQueueRepository.save(
            songs = snapshot.songs,
            currentIndex = snapshot.currentIndex,
            positionMs = snapshot.positionMs,
            repeatMode = snapshot.repeatMode,
            shuffleEnabled = snapshot.shuffleEnabled,
            sourceName = snapshot.sourceName,
            isRadio = snapshot.isRadio,
            sourceId = snapshot.sourceId,
        )
    }

    private fun captureQueueSnapshot(): QueueSnapshot {
        val current = activePlayer
        val songs = List(current.mediaItemCount) { index ->
            current.getMediaItemAt(index).toSong()
        }
        return QueueSnapshot(
            songs = songs,
            currentIndex = current.currentMediaItemIndex,
            positionMs = current.currentPosition,
            repeatMode = current.repeatMode,
            shuffleEnabled = current.shuffleModeEnabled,
            sourceName = PlayerManager.queueSourceName.value,
            isRadio = PlayerManager.isRadioQueue.value,
            sourceId = PlayerManager.queueSourceId.value,
        )
    }

    private data class QueueSnapshot(
        val songs: List<Song>,
        val currentIndex: Int,
        val positionMs: Long,
        val repeatMode: Int,
        val shuffleEnabled: Boolean,
        val sourceName: String?,
        val isRadio: Boolean,
        val sourceId: String?,
    )

    /**
     * Endless-playback ("radio"): when autoplay is on and the current track is the last one in the
     * queue, fetches related songs for it and appends them, so playback never stops. Guarded by the
     * seed id so it fires at most once per track.
     */
    private suspend fun maybeAppendRadio(currentSong: Song, settings: UmihiSettings) {
        if (!settings.autoplay || currentSong.youtubeId.isBlank()) {
            return
        }
        // A collection queue (album, artist, playlist) ends where the collection ends.
        if (PlayerManager.isCollectionQueue) {
            return
        }

        val isLast = withContext(Dispatchers.Main) {
            val p = mediaLibrarySession?.player ?: return@withContext false
            p.currentMediaItemIndex >= p.mediaItemCount - 1
        }
        if (!isLast || lastRadioSeedId == currentSong.youtubeId) {
            return
        }
        lastRadioSeedId = currentSong.youtubeId

        val related = radioRepository.getRelated(currentSong)
            .firstOrNull { it is ApiResult.Success } as? ApiResult.Success
            ?: return
        val songs = related.data
        if (songs.isEmpty()) {
            return
        }

        withContext(Dispatchers.Main) {
            val p = mediaLibrarySession?.player ?: return@withContext
            // Re-check: the user may have queued items while we were fetching.
            if (p.currentMediaItemIndex >= p.mediaItemCount - 1) {
                // Exclude titles already in the queue so the radio never re-appends them.
                val existingIds = buildSet {
                    for (i in 0 until p.mediaItemCount) add(p.getMediaItemAt(i).mediaId)
                }
                val fresh = songs.filter { it.youtubeId !in existingIds }
                if (fresh.isNotEmpty()) {
                    p.addMediaItems(fresh.map { it.apply { isSuggestion = true }.mediaItem })
                }
            }
        }
    }

    /**
     * Resolves (and caches) the next queue item's stream URL ahead of time, so a manual "next"
     * press plays almost instantly instead of waiting for on-demand extraction. Best-effort.
     */
    private fun prefetchNextStreamUrl() {
        val current = activePlayer
        val nextIndex = current.currentMediaItemIndex + 1
        if (nextIndex >= current.mediaItemCount) {
            return
        }
        val nextSong = current.getMediaItemAt(nextIndex).toSong()
        // On-device tracks already have a playable URI; there is no YouTube stream to prefetch.
        if (nextSong.isLocal) {
            return
        }
        val context = applicationContext
        serviceScope.launch {
            try {
                YoutubeStreamResolver(context).resolve(nextSong.toTrack())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Ignore: prefetch is opportunistic, real resolution happens on playback.
            }
        }
    }

    /**
     * Audio offload cannot drive a video renderer: while a Video-mode (forceVideo) item is
     * current, offload is forced off regardless of the user's setting, and the preference is
     * re-applied as soon as an audio item plays again.
     */
    private fun applyAudioOffloadForItem(mediaItem: MediaItem?) {
        if (isCasting) return
        val forceVideo = mediaItem?.mediaMetadata?.extras
            ?.getBoolean(Constants.ExoPlayer.SongMetadata.FORCE_VIDEO, false) ?: false

        serviceScope.launch {
            val offloadEnabled = !forceVideo && datastoreRepository.getSettings().useAudioOffload
            val mode = if (offloadEnabled) {
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
            } else {
                TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
            }
            withContext(Dispatchers.Main) {
                val current = player.trackSelectionParameters.audioOffloadPreferences.audioOffloadMode
                if (current != mode) {
                    player.trackSelectionParameters =
                        player.trackSelectionParameters
                            .buildUpon()
                            .setAudioOffloadPreferences(
                                player.trackSelectionParameters.audioOffloadPreferences
                                    .buildUpon()
                                    .setAudioOffloadMode(mode)
                                    .build()
                            )
                            .build()
                }
            }
        }
    }

    private fun updateCurrentMediaItemThumbnail(mediaItem: MediaItem?) {
        if (mediaItem == null) {
            return
        }

        val context = applicationContext
        val songId = mediaItem.mediaId
        val forceVideo = mediaItem.mediaMetadata.extras
            ?.getBoolean(Constants.ExoPlayer.SongMetadata.FORCE_VIDEO, false) ?: false

        serviceScope.launch {
            try {
                // A downloaded thumbnail is the official-audio cover; skip it when the user asked
                // for the video version so the clip poster (already on the media item) is honoured.
                if (!forceVideo) {
                    val imageDir = UmihiHelper.getDownloadDirectory(
                        context,
                        Constants.Downloads.THUMBNAILS_FOLDER
                    )

                    val downloadedImage = File(imageDir, "$songId.jpg")
                    if (downloadedImage.exists()) {
                        val imageBytes = downloadedImage.readBytes()

                        updateMediaItemArtwork(
                            mediaItem,
                            imageBytes.cappedTo(),
                            downloadedImage.toUri()
                        )
                        return@launch
                    }
                }

                // Load the bytes for the artwork the media item already carries (the official album
                // cover, or the clip poster in video mode) instead of re-deriving a thumbnail from
                // the player response, which for a music video is the video clip poster and would
                // make the artwork drift away from the official audio.
                val artworkUri = mediaItem.mediaMetadata.artworkUri ?: return@launch
                val artBytes = UmihiHelper.fetchArtworkBytes(artworkUri.toString())
                if (artBytes != null) {
                    updateMediaItemArtwork(mediaItem, artBytes, artworkUri)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (ex: Exception) {
                printe(
                    message = "Failed to get full res thumbnail for $songId. Error : ${ex.message}",
                )
            }
        }
    }

    private suspend fun updateMediaItemArtwork(
        mediaItem: MediaItem,
        artBytes: ByteArray?,
        uri: Uri
    ) {
        val extras = mediaItem.mediaMetadata.extras
        extras?.putString(
            Constants.ExoPlayer.SongMetadata.UID,
            Uuid.random().toString()
        )

        val updated = mediaItem.buildUpon()
            .setMediaMetadata(
                mediaItem.mediaMetadata.buildUpon()
                    .setArtworkData(artBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .setArtworkUri(uri)
                    .setExtras(extras)
                    .build()
            )
            .build()

        withContext(Dispatchers.Main) {
            if (activePlayer.currentMediaItem?.mediaId == mediaItem.mediaId) {
                activePlayer.replaceMediaItem(
                    activePlayer.currentMediaItemIndex,
                    updated
                )
            }
        }
        updatePlaybackWidget(updated, artBytes?.cappedTo())
    }

    private fun handleWidgetAction(action: String) {
        serviceScope.launch {
            var attempts = 0
            while (!queueRestoreComplete && attempts < 20) {
                delay(50)
                attempts++
            }
            withContext(Dispatchers.Main) {
                val current = activePlayer
                when (action) {
                    ACTION_WIDGET_PLAY_PAUSE -> {
                        if (current.isPlaying) {
                            current.pause()
                        } else if (current.mediaItemCount > 0) {
                            if (current.playbackState == Player.STATE_IDLE) current.prepare()
                            current.play()
                        }
                    }

                    ACTION_WIDGET_PREVIOUS -> current.seekToPreviousMediaItem()
                    ACTION_WIDGET_NEXT -> current.seekToNextMediaItem()
                }
            }
        }
    }

    private fun updatePlaybackWidget(mediaItem: MediaItem?, artworkBytes: ByteArray? = null) {
        serviceScope.launch {
            val isPlaying = withContext(Dispatchers.Main) { activePlayer.isPlaying }
            PlaybackWidgetStateStore.write(
                context = applicationContext,
                track = mediaItem?.toSong()?.toTrack(),
                isPlaying = isPlaying,
                artworkBytes = artworkBytes,
            )
            PlaybackWidget().updateAll(applicationContext)
        }
    }

    companion object {
        const val ACTION_WIDGET_PLAY_PAUSE =
            "ca.ilianokokoro.umihi.music.action.WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_PREVIOUS =
            "ca.ilianokokoro.umihi.music.action.WIDGET_PREVIOUS"
        const val ACTION_WIDGET_NEXT =
            "ca.ilianokokoro.umihi.music.action.WIDGET_NEXT"
        private const val AUDIO_FADE_DURATION_MS = 450L
        const val DEFAULT_AUTOMIX_FADE_MS = 6_000L
        private const val AUDIO_FADE_POLL_INTERVAL_MS = 30L
        private const val QUEUE_CHANGE_DEBOUNCE_MS = 350L
        private const val QUEUE_POSITION_SAVE_INTERVAL_MS = 5_000L
        private const val MAX_CONSECUTIVE_PLAYBACK_ERRORS = 5
        private const val SHUTDOWN_WRITE_TIMEOUT_MS = 3_000L

        /** Personal audio outputs whose connection may resume a becoming-noisy pause. */
        private val HEADPHONE_DEVICE_TYPES = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
    }
}
