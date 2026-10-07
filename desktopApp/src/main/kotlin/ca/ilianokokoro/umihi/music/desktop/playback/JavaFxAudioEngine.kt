package ca.ilianokokoro.umihi.music.desktop.playback

import ca.ilianokokoro.umihi.music.core.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import kotlin.random.Random

/** Serialized playback controller. Network work never owns or reads a JavaFX player. */
class JavaFxAudioEngine(
    private val resolver: StreamResolver,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val output: AudioOutput = JavaFxOutput(),
    private val random: Random = Random.Default,
) : PlaybackEngine {
    private val lifetime = SupervisorJob(scope.coroutineContext[Job])
    private val engineScope = CoroutineScope(scope.coroutineContext + lifetime)
    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val _queue = MutableStateFlow(PlaybackQueue())
    override val queue: StateFlow<PlaybackQueue> = _queue.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _progress = MutableStateFlow<PlaybackProgress?>(null)
    private val _volume = MutableStateFlow(1.0)
    val volume: StateFlow<Double> = _volume.asStateFlow()

    private var resolution: Job? = null
    private var generation = 0L
    private var released = false
    private var playWhenReady = true
    private var opened = false
    private var order = emptyList<String>()

    override suspend fun load(queue: PlaybackQueue, startIndex: Int) = command {
        // Always own slot identity, including duplicate titles and caller-supplied duplicate ids.
        _queue.value = PlaybackQueue(
            queue.entries.map { it.copy(entryId = UUID.randomUUID().toString()) },
            if (queue.entries.isEmpty()) -1 else startIndex.coerceIn(queue.entries.indices),
        )
        rebuildOrder()
        openCurrent()
    }

    override fun enqueue(track: Track) = command {
        val entry = QueueEntry(UUID.randomUUID().toString(), track)
        _queue.value = _queue.value.copy(entries = _queue.value.entries + entry)
        order = order + entry.entryId
        if (_queue.value.current == null) {
            _queue.value = _queue.value.copy(currentIndex = 0)
            openCurrent()
        }
    }

    override fun play() = command {
        playWhenReady = true
        if (!opened && _state.value.status != PlaybackStatus.BUFFERING) openCurrent()
        else if (opened) {
            if (_state.value.status == PlaybackStatus.ENDED) output.seek(0)
            output.play()
        }
    }

    override fun pause() = command {
        playWhenReady = false
        output.pause()
        _state.value = _state.value.copy(isPlaying = false)
    }

    override fun seekTo(positionMs: Long) = command {
        output.seek(relativeSeekTarget(positionMs, _progress.value?.durationMs ?: 0, 0))
    }

    override fun seekBy(deltaMs: Long) = command {
        val progress = _progress.value ?: return@command
        output.seek(relativeSeekTarget(progress.positionMs, progress.durationMs, deltaMs))
    }

    override fun seekToIndex(index: Int) = command { select(index) }

    override fun skipNext() = command { step(1) }

    override fun skipPrevious() = command {
        if ((_progress.value?.positionMs ?: 0) > 3_000) output.seek(0) else step(-1)
    }

    override fun setRepeatMode(mode: RepeatMode) = command {
        _state.value = _state.value.copy(repeatMode = mode)
    }

    override fun setShuffleEnabled(enabled: Boolean) = command {
        _state.value = _state.value.copy(shuffleEnabled = enabled)
        rebuildOrder()
    }

    fun setVolume(value: Double) = command {
        if (!value.isFinite()) return@command
        _volume.value = value.coerceIn(0.0, 1.0)
        output.volume(_volume.value)
    }

    fun remove(index: Int) = command {
        val queue = _queue.value
        val removed = queue.entries.getOrNull(index) ?: return@command
        val entries = queue.entries.toMutableList().apply { removeAt(index) }
        val currentId = queue.current?.entryId
        val nextIndex = if (removed.entryId == currentId) index.coerceAtMost(entries.lastIndex)
            else entries.indexOfFirst { it.entryId == currentId }
        _queue.value = PlaybackQueue(entries, nextIndex)
        order = order - removed.entryId
        if (removed.entryId == currentId) openCurrent()
        else _state.value = _state.value.copy(currentIndex = nextIndex)
    }

    fun move(index: Int, destination: Int) = command {
        val queue = _queue.value
        if (index !in queue.entries.indices || destination !in queue.entries.indices) return@command
        val entries = queue.entries.toMutableList().apply { add(destination, removeAt(index)) }
        val currentIndex = entries.indexOfFirst { it.entryId == queue.current?.entryId }
        _queue.value = PlaybackQueue(entries, currentIndex)
        _state.value = _state.value.copy(currentIndex = currentIndex)
        if (!_state.value.shuffleEnabled) rebuildOrder()
    }

    override suspend fun progress(): PlaybackProgress? = _progress.value
    fun snapshotProgress(): PlaybackProgress? = _progress.value

    fun release() = output.dispatch {
        if (!released) {
            released = true
            generation++
            lifetime.cancel()
            output.close()
            _progress.value = null
            _state.value = PlaybackState()
        }
    }

    private fun command(action: () -> Unit) = output.dispatch {
        if (!released) action()
    }

    private fun select(index: Int) {
        if (index !in _queue.value.entries.indices) return
        _queue.value = _queue.value.copy(currentIndex = index)
        openCurrent()
    }

    private fun step(delta: Int) {
        val queue = _queue.value
        val current = order.indexOf(queue.current?.entryId)
        if (current < 0 || order.isEmpty()) return
        val target = current + delta
        val slot = when {
            target in order.indices -> order[target]
            _state.value.repeatMode == RepeatMode.ALL -> order[Math.floorMod(target, order.size)]
            else -> return
        }
        select(queue.entries.indexOfFirst { it.entryId == slot })
    }

    private fun rebuildOrder() {
        val queue = _queue.value
        val ids = queue.entries.map { it.entryId }
        order = if (_state.value.shuffleEnabled && queue.current != null) {
            listOf(queue.current!!.entryId) + ids.filterNot { it == queue.current!!.entryId }.shuffled(random)
        } else ids
    }

    private fun openCurrent() {
        val opening = ++generation
        resolution?.cancel()
        output.close()
        opened = false
        _progress.value = null
        _error.value = null
        val entry = _queue.value.current
        _state.value = _state.value.copy(
            status = if (entry == null) PlaybackStatus.IDLE else PlaybackStatus.BUFFERING,
            currentTrack = entry?.track,
            currentIndex = _queue.value.currentIndex,
            isPlaying = false,
        )
        if (entry == null) return
        playWhenReady = true
        resolution = engineScope.launch {
            try {
                val url = resolver.resolve(entry.track, allowLocal = true).url
                command {
                    if (opening != generation) return@command
                    try {
                        output.open(url, events(opening))
                        opened = true
                        output.volume(_volume.value)
                        if (playWhenReady) output.play()
                    } catch (_: Exception) { fail(opening) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                command { fail(opening) }
            }
        }
    }

    private fun fail(opening: Long) {
        if (opening != generation) return
        generation++ // Invalidates callbacks already queued by the failed native player.
        output.close()
        opened = false
        _progress.value = null
        _error.value = "Lecture impossible. Vérifiez votre connexion puis réessayez."
        _state.value = _state.value.copy(status = PlaybackStatus.IDLE, isPlaying = false)
    }

    private fun events(opening: Long) = object : AudioOutput.Events {
        fun current(action: () -> Unit) { if (!released && opening == generation) action() }
        override fun ready() = current { _state.value = _state.value.copy(status = PlaybackStatus.READY) }
        override fun playing(value: Boolean) = current {
            _state.value = _state.value.copy(isPlaying = value, status = PlaybackStatus.READY)
        }
        override fun progress(value: PlaybackProgress) = current { _progress.value = value }
        override fun failed() = current { fail(opening) }
        override fun ended() = current {
            _state.value = _state.value.copy(isPlaying = false, status = PlaybackStatus.ENDED)
            if (_state.value.repeatMode == RepeatMode.ONE) select(_queue.value.currentIndex)
            else step(1)
        }
    }
}
