package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable

/**
 * A partially listened track that can be resumed from [positionMs]. [progress] drives the small
 * indicator shown in the "resume listening" home section.
 */
@Immutable
data class ResumeItem(
    val song: Song,
    val positionMs: Long,
    val durationMs: Long,
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}
