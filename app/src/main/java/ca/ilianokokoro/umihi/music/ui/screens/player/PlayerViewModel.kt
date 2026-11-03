package ca.ilianokokoro.umihi.music.ui.screens.player


import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.managers.LikeManager
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeStreamExtractor
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.LocalPlaylistRepository
import ca.ilianokokoro.umihi.music.core.lyrics.EmbeddedLyrics
import ca.ilianokokoro.umihi.music.data.repositories.LyricsRepository
import ca.ilianokokoro.umihi.music.data.repositories.SongRepository
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.adapters.toTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


class PlayerViewModel(application: Application) :
    AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(PlayerState())
    val uiState = _uiState.asStateFlow()
    private val datastoreRepository = DatastoreRepository(application)
    private val localPlaylistRepository = LocalPlaylistRepository(application)
    private val lyricsRepository = LyricsRepository { song ->
        song.localUri?.let { EmbeddedLyrics.forUri(getApplication(), it) }
    }
    private val songRepository = SongRepository()
    private var lyricsJob: Job? = null
    private var lyricsSongId: String? = null

    // Per-youtubeId result of the video-variant (watch-next counterpart) lookup, so each track is
    // probed at most once per player screen. A null value means "probed, no video variant".
    private val videoVariantCache = mutableMapOf<String, Song?>()
    private var videoVariantJob: Job? = null
    private var videoStreamPrewarmJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            PlayerManager.clearAbLoop()
            updateCurrentSong()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateIsPlayingState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateIsLoadingState()
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isPlaying = false,
                )
            }
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            updateQueue()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {

            val artworkUri = mediaMetadata.artworkUri ?: return
            updateThumbnail(artworkUri)
        }
    }

    init {
        PlayerManager.currentController?.addListener(playerListener)

        startProgressUpdate()
        updateCurrentSong()
        updateIsLoadingState()
        updateIsPlayingState()

        viewModelScope.launch {
            PlayerManager.sleepTimerRemainingSeconds.collect { seconds ->
                _uiState.update { it.copy(sleepTimerRemainingSeconds = seconds) }
            }
        }

        viewModelScope.launch {
            PlayerManager.playbackSpeed.collect { speed ->
                _uiState.update { it.copy(playbackSpeed = speed) }
            }
        }

        viewModelScope.launch {
            PlayerManager.abLoop.collect { loop ->
                _uiState.update { it.copy(abLoop = loop) }
            }
        }

        viewModelScope.launch {
            // Collected (not read once) so a changed default sleep timer duration applies to the
            // next sheet opening without recreating the player.
            datastoreRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        isLoggedIn = !settings.cookies.isEmpty(),
                        sleepTimerDefaultMinutes = settings.sleepTimerDefaultMinutes,
                        smartTransitions = settings.smartTransitions,
                    )
                }
            }
        }
    }

    fun setSmartTransitions(enabled: Boolean) {
        viewModelScope.launch {
            datastoreRepository.save(
                DatastoreRepository.PreferenceKeys.SMART_TRANSITIONS,
                enabled,
            )
        }
    }


    fun toggleLike() {
        val currentSong = _uiState.value.queue.getOrNull(_uiState.value.currentIndex) ?: return
        if (_uiState.value.isLiking) {
            return
        }

        viewModelScope.launch {
            val settings = datastoreRepository.getSettings()
            if (settings.cookies.isEmpty()) {
                return@launch
            }

            val isCurrentlyLiked = _uiState.value.isLiked
            val newLiked = !isCurrentlyLiked

            _uiState.update { it.copy(isLiked = newLiked, isLiking = true) }

            val succeeded = LikeManager.setLikedAndWait(
                song = currentSong,
                liked = newLiked,
                context = getApplication(),
                currentStatus = isCurrentlyLiked,
            )

            if (succeeded) {
                _uiState.update { state ->
                    val updatedQueue = state.queue.toMutableList().apply {
                        val index = state.currentIndex
                        if (index in indices) {
                            set(index, this[index].withLikeStatus(newLiked))
                        }
                    }
                    state.copy(isLiked = newLiked, isLiking = false, queue = updatedQueue)
                }
            } else {
                _uiState.update { it.copy(isLiked = isCurrentlyLiked, isLiking = false) }
            }
        }
    }

    fun saveQueueAsPlaylist(name: String, songs: List<Song>) {
        val playlistName = name.trim()
        if (
            playlistName.isEmpty() ||
            songs.isEmpty() ||
            _uiState.value.isSavingQueue
        ) {
            return
        }

        _uiState.update { it.copy(isSavingQueue = true) }
        viewModelScope.launch {
            try {
                localPlaylistRepository.createFromTracks(
                    name = playlistName,
                    tracks = songs.map { it.toTrack() },
                )
                Toast.makeText(
                    getApplication(),
                    getApplication<Application>().getString(
                        R.string.queue_saved_toast,
                        playlistName,
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                printe(
                    message = "Failed to save queue as playlist: ${exception.message}",
                    exception = exception,
                )
                Toast.makeText(
                    getApplication(),
                    R.string.queue_save_failed_toast,
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                _uiState.update { it.copy(isSavingQueue = false) }
            }
        }
    }

    /**
     * Opens the current song's artist page. When the queue item doesn't carry the artist
     * browse id (e.g. restored queues or radio items), it is resolved once through the
     * watch-next endpoint and cached back onto the song before navigating.
     */
    fun openArtist(onOpenArtist: (Song) -> Unit) {
        val currentSong = _uiState.value.queue.getOrNull(_uiState.value.currentIndex) ?: return

        if (currentSong.artistId != null) {
            onOpenArtist(currentSong)
            return
        }

        if (_uiState.value.isResolvingArtist) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingArtist = true) }
            try {
                val artistId = YoutubeDataExtractor.extractArtistBrowseId(
                    YoutubeApiClient.next(currentSong.youtubeId),
                    currentSong.youtubeId,
                )
                if (artistId == null) {
                    Toast.makeText(
                        getApplication(),
                        R.string.artist_unavailable,
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }
                currentSong.artistId = artistId
                onOpenArtist(currentSong)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                printe(
                    message = "Failed to resolve artist for ${currentSong.youtubeId}: ${e.message}",
                    exception = e
                )
            } finally {
                _uiState.update { it.copy(isResolvingArtist = false) }
            }
        }
    }

    /**
     * Opens the current track's album. Album-page queues carry this context up front; restored
     * queues and radio items resolve it lazily through watch-next so the menu action remains useful
     * instead of disappearing when metadata is incomplete.
     */
    fun openAlbum(onOpenAlbum: (Song) -> Unit) {
        val currentSong = _uiState.value.queue.getOrNull(_uiState.value.currentIndex) ?: return

        if (currentSong.albumId != null) {
            onOpenAlbum(currentSong)
            return
        }
        if (currentSong.isLocal || _uiState.value.isResolvingAlbum) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isResolvingAlbum = true) }
            try {
                val album = YoutubeDataExtractor.extractAlbumBrowseInfo(
                    YoutubeApiClient.next(currentSong.youtubeId),
                    currentSong.youtubeId,
                )
                if (album == null) {
                    Toast.makeText(
                        getApplication(),
                        R.string.album_unavailable,
                        Toast.LENGTH_SHORT,
                    ).show()
                    return@launch
                }

                val resolvedSong = currentSong.withAlbumContext(
                    albumId = album.first,
                    albumName = album.second,
                )
                _uiState.update { state ->
                    val updatedQueue = state.queue.toMutableList().apply {
                        val index = state.currentIndex
                        if (index in indices) {
                            set(index, resolvedSong)
                        }
                    }
                    state.copy(queue = updatedQueue)
                }
                onOpenAlbum(resolvedSong)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                printe(
                    message = "Failed to resolve album for ${currentSong.youtubeId}: ${e.message}",
                    exception = e,
                )
                Toast.makeText(
                    getApplication(),
                    R.string.album_unavailable,
                    Toast.LENGTH_SHORT,
                ).show()
            } finally {
                _uiState.update { it.copy(isResolvingAlbum = false) }
            }
        }
    }

    /** Menu entry point ("Play video version"): same flow as the player's Song|Video pill. */
    fun playCurrentAsVideo() {
        setPlaybackMode(video = true)
    }

    /**
     * Song|Video pill toggle: switches the current item between its audio-preferred stream and the
     * muxed music-video stream, in place (queue and playback position are both kept). The chosen
     * mode is remembered in [PlayerManager] for the rest of the session. Defensive: when no video
     * stream can be resolved, a toast is shown and playback stays in audio mode.
     */
    fun setPlaybackMode(video: Boolean) {
        switchPlaybackMode(video, fromUser = true)
    }

    private fun switchPlaybackMode(video: Boolean, fromUser: Boolean) {
        val current = _uiState.value.queue.getOrNull(_uiState.value.currentIndex) ?: return
        if (current.isLocal || _uiState.value.isSwitchingPlaybackMode) {
            return
        }
        PlayerManager.currentController ?: return

        if (fromUser) {
            PlayerManager.setVideoModePreferred(video)
        }

        if (current.forceVideo == video) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSwitchingPlaybackMode = true) }
            try {
                if (video) {
                    switchToVideoMode(current, fromUser)
                } else {
                    switchToSongMode(current)
                }
            } finally {
                _uiState.update { it.copy(isSwitchingPlaybackMode = false) }
            }
        }
    }

    /**
     * Video mode: plays the muxed stream of the current item's video variant (the watch-next
     * counterpart when the item is an audio track, the item itself when it already is a video).
     * The stream is pre-resolved before touching the queue so a failure leaves audio playing
     * untouched — with a toast when the switch was user-initiated.
     */
    private suspend fun switchToVideoMode(current: Song, fromUser: Boolean) {
        val variant = videoVariantCache[current.youtubeId] ?: current

        val hasVideoStream = try {
            YoutubeStreamExtractor.probeVideoStream(getApplication(), variant)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            printe(
                message = "Failed to resolve video stream for ${variant.youtubeId}: ${e.message}",
                exception = e
            )
            false
        }

        if (!hasVideoStream) {
            if (fromUser) {
                Toast.makeText(
                    getApplication(),
                    getApplication<Application>().getString(R.string.video_mode_unavailable),
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        // 16:9 clip poster: the counterpart already carries one; when the item plays as itself,
        // fetch it from the player response so the artwork switches deterministically.
        var poster = variant.thumbnailHref.takeIf { it.isNotBlank() }
        if (poster == null) {
            try {
                songRepository.getSongInfo(variant.youtubeId).collect { result ->
                    if (result is ApiResult.Success) {
                        poster = result.data.thumbnailHref
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (e: Exception) {
                printe(message = "Failed to fetch video artwork: ${e.message}", exception = e)
            }
        }

        // Remember the audio-mode item so toggling back restores it exactly (cover included).
        PlayerManager.rememberAudioOriginal(variant.youtubeId, current)

        val videoSong = current.copy(
            youtubeId = variant.youtubeId,
            duration = variant.duration.takeIf { it.isNotBlank() } ?: current.duration,
            thumbnailHref = poster?.takeIf { it.isNotBlank() } ?: current.thumbnailHref,
            thumbnailPath = null,
            streamUrl = null,
            audioFilePath = null,
        ).also {
            it.forceVideo = true
            it.isVideo = true
            it.artistId = current.artistId
            it.albumId = current.albumId
            it.albumName = current.albumName
            it.isSuggestion = current.isSuggestion
        }

        // Offloaded audio cannot drive a video renderer: keep it off while a video item is current.
        videoStreamPrewarmJob?.cancel()
        PlayerManager.setAudioOffloadEnabled(false)
        PlayerManager.replaceCurrentWithVideo(videoSong)
    }

    /**
     * Song mode: swaps the current video item back to its remembered audio original (or, failing
     * that, the same item with `forceVideo` cleared so the resolution pipeline re-applies the
     * audio-preferred counterpart swap), keeping queue and playback position.
     */
    private suspend fun switchToSongMode(current: Song) {
        val original = PlayerManager.getAudioOriginal(current.youtubeId)

        val audioSong = original ?: current.copy(
            thumbnailPath = null,
            streamUrl = null,
        ).also {
            it.forceVideo = false
            it.isVideo = current.isVideo
            it.artistId = current.artistId
            it.albumId = current.albumId
            it.albumName = current.albumName
            it.isSuggestion = current.isSuggestion
        }

        // Restore the user's audio-offload preference now that no video surface is needed.
        val settings = datastoreRepository.getSettings()
        PlayerManager.setAudioOffloadEnabled(settings.useAudioOffload)
        PlayerManager.replaceCurrentWithAudio(audioSong)
    }

    /**
     * Decides whether the current item can offer Video mode: video items (playing as either
     * variant) always can; audio tracks are probed once per id through the watch-next counterpart
     * lookup. When the session's last chosen mode is Video, an eligible new track is switched
     * over automatically (silently keeping audio when its video stream cannot be resolved).
     */
    private fun updateVideoVariant(song: Song?) {
        videoVariantJob?.cancel()
        videoStreamPrewarmJob?.cancel()

        if (song == null || song.isLocal) {
            _uiState.update { it.copy(hasVideoVariant = false) }
            return
        }

        // Direct YouTube links are initially modeled like audio tracks even when their own id is a
        // muxed music video. Start that safe probe immediately instead of gating prewarming on the
        // optional watch-next counterpart lookup (which can be rate-limited independently).
        prewarmVideoStream(song)

        if (song.forceVideo || song.isVideo) {
            _uiState.update { it.copy(hasVideoVariant = true) }
            maybeApplyPreferredVideoMode(song)
            return
        }

        if (videoVariantCache.containsKey(song.youtubeId)) {
            val cachedVariant = videoVariantCache[song.youtubeId]
            val hasVariant = cachedVariant != null
            _uiState.update { it.copy(hasVideoVariant = hasVariant) }
            if (hasVariant) {
                prewarmVideoStream(cachedVariant)
                maybeApplyPreferredVideoMode(song)
            }
            return
        }

        _uiState.update { it.copy(hasVideoVariant = false) }
        videoVariantJob = viewModelScope.launch {
            val variant = try {
                YoutubeDataExtractor.extractVideoCounterpart(
                    YoutubeApiClient.next(song.youtubeId)
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                printe(
                    message = "Video variant lookup failed for ${song.youtubeId}: ${e.message}",
                    exception = e
                )
                null
            }
            videoVariantCache[song.youtubeId] = variant

            // Only surface the result if this track is still the one playing.
            val state = _uiState.value
            val stillCurrent = state.queue.getOrNull(state.currentIndex)
                ?.youtubeId == song.youtubeId
            if (stillCurrent) {
                _uiState.update { it.copy(hasVideoVariant = variant != null) }
                if (variant != null) {
                    prewarmVideoStream(variant)
                    maybeApplyPreferredVideoMode(song)
                }
            }
        }
    }

    private fun maybeApplyPreferredVideoMode(song: Song) {
        if (PlayerManager.videoModePreferred.value && !song.forceVideo) {
            switchPlaybackMode(video = true, fromUser = false)
        }
    }

    /**
     * The player discovers video availability while audio is already playing. Resolve the signed
     * muxed URL during that idle time so tapping Video only has to attach the renderer and buffer.
     */
    private fun prewarmVideoStream(video: Song) {
        if (PlayerManager.videoModePreferred.value || video.forceVideo) return
        videoStreamPrewarmJob?.cancel()
        videoStreamPrewarmJob = viewModelScope.launch {
            try {
                // Do not make speculative video resolution compete with the title the user just
                // asked to hear. Audio reaches PLAYING first; video then warms during playback.
                delay(VIDEO_PREWARM_START_DELAY_MS)
                YoutubeStreamExtractor.probeVideoStream(getApplication(), video)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort: the explicit Video action retains its normal error handling.
            }
        }
    }

    /**
     * Fullscreen video (landscape rotation or the fullscreen button on the surface). Only
     * meaningful while the current item plays in Video mode; the screen clears it otherwise.
     */
    fun setVideoFullscreen(fullscreen: Boolean) {
        _uiState.update { it.copy(isVideoFullscreen = fullscreen) }
    }

    fun setSleepTimerSheetVisibility(show: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSleepTimerModalShown = show) }
        }
    }

    fun startSleepTimer(minutes: Int) {
        PlayerManager.startSleepTimer(minutes)
    }

    fun startSleepTimerEndOfSong() {
        PlayerManager.startSleepTimerEndOfSong()
    }

    fun cancelSleepTimer() {
        PlayerManager.cancelSleepTimer()
    }

    fun setSpeedSelectorVisibility(show: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSpeedSelectorShown = show) }
        }
    }

    fun setPlayerMenuVisibility(show: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPlayerMenuShown = show) }
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        PlayerManager.setPlaybackSpeed(speed)
    }

    fun toggleAbLoop() {
        PlayerManager.toggleAbLoop()
    }

    fun seekPlayer() {
        PlayerManager.currentController?.seekTo(_uiState.value.playbackProgress.position.toLong())
    }

    /**
     * Relative seek (double-tap on the artwork). Clamps to 0..duration so the ±10s jumps never
     * run past the ends of the track.
     */
    fun seekBy(deltaMs: Long) {
        val controller = PlayerManager.currentController ?: return
        val duration = controller.duration
        val upperBound = if (duration > 0) duration else Long.MAX_VALUE
        val target = (controller.currentPosition + deltaMs).coerceIn(0L, upperBound)
        controller.seekTo(target)
    }

    fun seek(location: Float) {
        viewModelScope.launch {
            _uiState.update {
                _uiState.value.copy(
                    playbackProgress = PlaybackProgress(
                        duration = it.playbackProgress.duration,
                        position = location,
                    ),
                )
            }
        }
    }

    fun updateSeekBarHeldState(isHeld: Boolean) {
        viewModelScope.launch {
            if (_uiState.value.isSeekBarHeld == isHeld) {
                return@launch
            }


            _uiState.update {
                _uiState.value.copy(
                    isSeekBarHeld = isHeld,
                )
            }
        }
    }

    fun setQueueVisibility(show: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                _uiState.value.copy(
                    isQueueModalShown = show
                )
            }
        }
    }

    /**
     * Carries the locally-known `isLiked` flag over onto a freshly-pulled queue, keyed by
     * youtubeId. O(n) — indexes the previous queue once instead of a linear scan per song.
     */
    private fun mergeLikes(previousQueue: List<Song>, freshQueue: List<Song>): List<Song> {
        val previousById = previousQueue.associateBy { it.youtubeId }
        return freshQueue.map { freshSong ->
            previousById[freshSong.youtubeId]?.let { existing ->
                if (existing.isLiked != freshSong.isLiked) {
                    freshSong.withLikeStatus(existing.isLiked)
                } else {
                    freshSong
                }
            } ?: freshSong
        }
    }

    private fun updateCurrentSong() {
        val index = PlayerManager.getCurrentIndex()
        val freshQueue = PlayerManager.getQueue()

        _uiState.update { state ->
            val mergedQueue = mergeLikes(state.queue, freshQueue)

            state.copy(
                currentIndex = index,
                queue = mergedQueue,
                playbackProgress = PlaybackProgress(
                    duration = 0f,
                    position = 0f,
                ),
                isLiked = mergedQueue.getOrNull(index)?.isLiked ?: false
            )
        }

        val current = freshQueue.getOrNull(index)
        if (current != null && current.youtubeId != lyricsSongId) {
            lyricsSongId = current.youtubeId
            loadLyrics(current)
        }
        updateVideoVariant(current)
    }

    private fun loadLyrics(song: Song, useCache: Boolean = true) {
        lyricsJob?.cancel()
        _uiState.update { it.copy(lyrics = ApiResult.Loading) }
        lyricsJob = viewModelScope.launch {
            lyricsRepository.getLyrics(song, useCache).collect { result ->
                _uiState.update { it.copy(lyrics = result) }
            }
        }
    }

    fun toggleLyricsVisibility() {
        _uiState.update { it.copy(areLyricsShown = !it.areLyricsShown) }
    }

    fun retryLyrics() {
        val currentSong = _uiState.value.queue.getOrNull(_uiState.value.currentIndex) ?: return
        loadLyrics(currentSong, useCache = false)
    }

    private fun updateQueue() {
        _uiState.update { state ->
            val mergedQueue = mergeLikes(state.queue, PlayerManager.getQueue())

            state.copy(
                currentIndex = PlayerManager.getCurrentIndex(),
                queue = mergedQueue
            )
        }
    }

    private fun startProgressUpdate() {
        viewModelScope.launch {
            while (true) {
                val state = _uiState.value

                if (!state.isSeekBarHeld && !state.isLoading) {
                    val controller = PlayerManager.currentController

                    val rawPosition = controller?.currentPosition
                    val rawDuration = controller?.duration

                    val loop = state.abLoop
                    if (loop?.pointBMs != null && rawPosition != null && rawPosition >= loop.pointBMs) {
                        controller?.seekTo(loop.pointAMs)
                    }

                    val current = state.playbackProgress

                    val safeDuration = when {
                        rawDuration == null -> current.duration
                        rawDuration == C.TIME_UNSET -> 0f
                        rawDuration <= 0 -> 0f
                        else -> rawDuration.toFloat()
                    }

                    val safePosition = when {
                        rawPosition == null -> current.position
                        rawPosition < 0 -> 0f
                        rawDuration == null || rawDuration == C.TIME_UNSET -> 0f
                        else -> rawPosition
                            .coerceAtMost(rawDuration)
                            .toFloat()
                    }.coerceIn(0f, safeDuration)

                    if (
                        safePosition != current.position ||
                        safeDuration != current.duration
                    ) {
                        _uiState.update {
                            it.copy(
                                playbackProgress = PlaybackProgress(
                                    position = safePosition,
                                    duration = safeDuration
                                )
                            )
                        }
                    }
                }

                delay(Constants.Player.PROGRESS_UPDATE_DELAY.milliseconds)
            }
        }
    }

    private fun updateIsLoadingState() {
        viewModelScope.launch {
            when (PlayerManager.playbackState) {
                Player.STATE_BUFFERING -> {
                    _uiState.update {
                        _uiState.value.copy(
                            isLoading = true
                        )
                    }
                }

                Player.STATE_READY -> {
                    _uiState.update {
                        _uiState.value.copy(
                            isLoading = false
                        )
                    }
                }

                else -> {
                }
            }
        }
    }

    private fun updateIsPlayingState() {
        viewModelScope.launch {
            _uiState.update {
                _uiState.value.copy(
                    isPlaying = PlayerManager.isPlaying
                )
            }
        }
    }


    private fun updateThumbnail(newUri: Uri) {
        _uiState.update { state ->
            val index = state.currentIndex
            val queue = state.queue

            if (index !in queue.indices) {
                return@update state
            }

            val currentSong = queue[index]
            if (currentSong.thumbnailHref == newUri.toString()) {
                return@update state
            }

            val updatedQueue = queue.toMutableList().apply {
                set(index, currentSong.copy(thumbnailHref = newUri.toString()))
            }

            state.copy(queue = updatedQueue)
        }
    }

    override fun onCleared() {
        videoStreamPrewarmJob?.cancel()
        PlayerManager.currentController?.removeListener(playerListener)
        super.onCleared()
    }

    companion object {
        private const val VIDEO_PREWARM_START_DELAY_MS = 750L

        fun Factory(
            application: Application,
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    PlayerViewModel(application)
                }
            }
    }
}
