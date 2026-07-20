package ca.ilianokokoro.umihi.music.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

object PlaybackLoudnessStore {
    private val volumeByVideoId = ConcurrentHashMap<String, Float>()
    private val _updates = MutableSharedFlow<Update>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val updates = _updates.asSharedFlow()

    fun update(videoId: String, loudnessDb: Double?) {
        if (videoId.isBlank()) return
        val volume = loudnessDb
            ?.takeIf(Double::isFinite)
            ?.coerceAtLeast(0.0)
            ?.let { dbAboveTarget -> 10.0.pow(-dbAboveTarget / 20.0).toFloat() }
            ?.coerceIn(MIN_NORMALIZED_VOLUME, 1f)
            ?: 1f
        volumeByVideoId[videoId] = volume
        _updates.tryEmit(Update(videoId, volume))
    }

    fun volumeFor(videoId: String): Float = volumeByVideoId[videoId] ?: 1f

    data class Update(val videoId: String, val volume: Float)

    private const val MIN_NORMALIZED_VOLUME = 0.25f
}
