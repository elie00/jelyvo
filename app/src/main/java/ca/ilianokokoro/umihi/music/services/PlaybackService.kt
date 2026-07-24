package ca.ilianokokoro.umihi.music.services

import android.app.PendingIntent
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Timeline
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
import androidx.glance.appwidget.updateAll
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.ExoCache
import ca.ilianokokoro.umihi.music.core.PlaybackLoudnessStore
import ca.ilianokokoro.umihi.music.core.UmihiHttpClient
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.datasources.YoutubeDataSourceFactory
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeStatsTracker
import ca.ilianokokoro.umihi.music.core.managers.AudioOutputManager
import ca.ilianokokoro.umihi.music.core.managers.EqualizerManager
import ca.ilianokokoro.umihi.music.core.managers.EqualizerProfileStore
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.HistoryRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlaylistRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlayerQueueRepository
import ca.ilianokokoro.umihi.music.data.repositories.RadioRepository
import ca.ilianokokoro.umihi.music.data.repositories.SongRepository
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import ca.ilianokokoro.umihi.music.extensions.cappedTo
import ca.ilianokokoro.umihi.music.extensions.toSong
import ca.ilianokokoro.umihi.music.models.PlaybackAudioInfo
import ca.ilianokokoro.umihi.music.widget.PlaybackWidget
import ca.ilianokokoro.umihi.music.widget.PlaybackWidgetStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.uuid.Uuid

@UnstableApi
class PlaybackService : MediaLibraryService() {
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var exoCache: ExoCache
    private lateinit var player: ExoPlayer
    private lateinit var datastoreRepository: DatastoreRepository
    private var currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
    private val songRepository = SongRepository()
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var playerQueueRepository: PlayerQueueRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queuePersistenceJob: Job? = null
    private var volumeFadeJob: Job? = null
    @Volatile private var normalizedVolume = 1f
    @Volatile private var crossfadeMs = 0L
    @Volatile private var fadeInActive = false
    @Volatile private var queueRestoreComplete = false
    @Volatile private var isRestoringQueue = false
    private var consecutivePlaybackErrors = 0
    private val historyRepository by lazy { HistoryRepository(applicationContext) }
    private val radioRepository = RadioRepository()
    private var lastRadioSeedId: String? = null

    private var audioManager: AudioManager? = null
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            AudioOutputManager.refresh(applicationContext)
            applyEqualizerForCurrentOutput()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            AudioOutputManager.refresh(applicationContext)
            applyEqualizerForCurrentOutput()
        }
    }

    private lateinit var callback: UmihiMediaLibraryCallback


    override fun onCreate() {
        super.onCreate()

        datastoreRepository = DatastoreRepository(applicationContext)
        playlistRepository = PlaylistRepository(application)
        playerQueueRepository = PlayerQueueRepository(applicationContext)
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
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .build()
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

        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                scheduleQueuePersistence()
            }

            override fun onMediaItemTransition(
                mediaItem: MediaItem?,
                reason: Int
            ) {
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
                    historyRepository.record(playedSong, System.currentTimeMillis())
                    maybeAppendRadio(playedSong, settings)
                }
                prefetchNextStreamUrl()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_IDLE) {
                    YoutubeStatsTracker.stopPlaybackTracking()
                }
                if (playbackState == Player.STATE_READY) {
                    // A track played successfully: reset the failure guard.
                    consecutivePlaybackErrors = 0
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                scheduleQueuePersistence()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                scheduleQueuePersistence()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                scheduleQueuePersistence()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    scheduleQueuePersistence()
                    serviceScope.launch { persistPlaybackPosition() }
                }
                updatePlaybackWidget(player.currentMediaItem)
            }

            override fun onPlayerError(error: PlaybackException) {
                consecutivePlaybackErrors++
                val queueSize = player.mediaItemCount
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
                    player.pause()
                    return
                }
                player.seekToNext()
                player.prepare()
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
        })

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

        restoreQueueAndStartPersistence()
        startAudioVolumeManagement()
        startAudioOutputEqualizer()
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
        if (queueRestoreComplete && ::player.isInitialized) {
            val snapshot = captureQueueSnapshot()
            runBlocking(Dispatchers.IO) {
                persistQueueSnapshot(snapshot)
            }
        }
        if (::player.isInitialized) {
            val currentItem = player.currentMediaItem
            runBlocking(Dispatchers.IO) {
                PlaybackWidgetStateStore.write(
                    context = applicationContext,
                    mediaItem = currentItem,
                    isPlaying = false,
                    artworkBytes = currentItem?.mediaMetadata?.artworkData?.cappedTo(),
                )
                PlaybackWidget().updateAll(applicationContext)
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
            val restored = playerQueueRepository.restore()
            withContext(Dispatchers.Main) {
                if (restored != null && player.mediaItemCount == 0) {
                    isRestoringQueue = true
                    player.setMediaItems(
                        restored.songs.map { it.mediaItem },
                        restored.currentIndex,
                        restored.positionMs,
                    )
                    player.repeatMode = restored.repeatMode
                    player.shuffleModeEnabled = restored.shuffleEnabled
                    player.prepare()
                    player.playWhenReady = false
                    PlayerManager.restoreQueueProvenance(
                        sourceName = restored.sourceName,
                        isRadio = restored.isRadio,
                    )
                    isRestoringQueue = false
                }
                queueRestoreComplete = true
            }
        }

        serviceScope.launch {
            while (true) {
                delay(QUEUE_POSITION_SAVE_INTERVAL_MS)
                val isPlaying = withContext(Dispatchers.Main) { player.isPlaying }
                if (queueRestoreComplete && isPlaying) {
                    persistQueueSnapshot()
                    persistPlaybackPosition()
                }
            }
        }
    }

    /**
     * Persists the current track's playback position into the listening history so a partially
     * listened title can later be resumed. Reuses the existing periodic save loop (throttled) plus
     * a save on pause; a no-op when there is no recorded play row for the current track.
     */
    private suspend fun persistPlaybackPosition() {
        if (isRestoringQueue) return
        val snapshot = withContext(Dispatchers.Main) {
            val item = player.currentMediaItem ?: return@withContext null
            Triple(item.mediaId, player.currentPosition, player.duration)
        } ?: return
        val (songId, positionMs, durationMs) = snapshot
        if (songId.isBlank() || positionMs <= 0) return
        historyRepository.updatePosition(
            youtubeId = songId,
            positionMs = positionMs,
            durationMs = durationMs.coerceAtLeast(0L),
        )
    }

    /**
     * Fade length applied around an automatic track transition. When the user has set a crossfade
     * duration it wins; otherwise the short default fade keeps loudness-normalized gapless
     * transitions click-free. Re-read at each transition so changes apply without a restart.
     */
    private fun fadeDurationMs(): Long =
        if (crossfadeMs > 0L) crossfadeMs else AUDIO_FADE_DURATION_MS

    private fun startAudioVolumeManagement() {
        serviceScope.launch {
            datastoreRepository.settings.collectLatest { settings ->
                crossfadeMs = settings.crossfadeSeconds.coerceIn(0, 12) * 1000L
            }
        }

        serviceScope.launch {
            PlaybackLoudnessStore.updates.collectLatest { update ->
                withContext(Dispatchers.Main) {
                    if (player.currentMediaItem?.mediaId == update.videoId) {
                        normalizedVolume = update.volume
                        if (!fadeInActive) player.volume = normalizedVolume
                    }
                }
            }
        }

        serviceScope.launch {
            while (true) {
                delay(AUDIO_FADE_POLL_INTERVAL_MS)
                withContext(Dispatchers.Main) {
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

    private fun applyTrackVolume(videoId: String, transitionReason: Int) {
        normalizedVolume = PlaybackLoudnessStore.volumeFor(videoId)
        volumeFadeJob?.cancel()
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
        )
    }

    private fun captureQueueSnapshot(): QueueSnapshot {
        val songs = List(player.mediaItemCount) { index ->
            player.getMediaItemAt(index).toSong()
        }
        return QueueSnapshot(
            songs = songs,
            currentIndex = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            repeatMode = player.repeatMode,
            shuffleEnabled = player.shuffleModeEnabled,
            sourceName = PlayerManager.queueSourceName.value,
            isRadio = PlayerManager.isRadioQueue.value,
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
        val nextIndex = player.currentMediaItemIndex + 1
        if (nextIndex >= player.mediaItemCount) {
            return
        }
        val nextSong = player.getMediaItemAt(nextIndex).toSong()
        // On-device tracks already have a playable URI; there is no YouTube stream to prefetch.
        if (nextSong.isLocal) {
            return
        }
        val context = applicationContext
        serviceScope.launch {
            try {
                YoutubeDataExtractor.getSongPlayerUrl(context, nextSong)
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
            if (player.currentMediaItem?.mediaId == mediaItem.mediaId) {
                player.replaceMediaItem(
                    player.currentMediaItemIndex,
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
                when (action) {
                    ACTION_WIDGET_PLAY_PAUSE -> {
                        if (player.isPlaying) {
                            player.pause()
                        } else if (player.mediaItemCount > 0) {
                            if (player.playbackState == Player.STATE_IDLE) player.prepare()
                            player.play()
                        }
                    }

                    ACTION_WIDGET_PREVIOUS -> player.seekToPreviousMediaItem()
                    ACTION_WIDGET_NEXT -> player.seekToNextMediaItem()
                }
            }
        }
    }

    private fun updatePlaybackWidget(mediaItem: MediaItem?, artworkBytes: ByteArray? = null) {
        serviceScope.launch {
            val isPlaying = withContext(Dispatchers.Main) { player.isPlaying }
            PlaybackWidgetStateStore.write(
                context = applicationContext,
                mediaItem = mediaItem,
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
        private const val AUDIO_FADE_POLL_INTERVAL_MS = 30L
        private const val QUEUE_CHANGE_DEBOUNCE_MS = 350L
        private const val QUEUE_POSITION_SAVE_INTERVAL_MS = 5_000L
        private const val MAX_CONSECUTIVE_PLAYBACK_ERRORS = 5
    }
}
