package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ca.ilianokokoro.umihi.music.core.Constants

/**
 * One fully-qualified listening signal: how long a track was *effectively* listened to (pauses
 * and seeks excluded) and in which context. Unlike [PlayHistoryEntry], which feeds the visible
 * history/stats, this table is the raw input of the local recommendation engine.
 */
@Immutable
@Entity(
    tableName = Constants.Database.PLAY_EVENTS_TABLE,
    indices = [Index("youtubeId"), Index("playedAt")],
)
data class PlayEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val youtubeId: String,
    /** Wall-clock instant the play started (epoch ms). */
    val playedAt: Long,
    /** Effective listening time: only accumulates while the player was playing. */
    val durationListenedMs: Long,
    /** Track duration when it was known, 0 otherwise. */
    val trackDurationMs: Long,
    /** durationListenedMs / trackDurationMs, clamped to [0, 1]. Stored for cheap SQL aggregates. */
    val completionRatio: Float,
    /** [ca.ilianokokoro.umihi.music.core.playback.PlayEndReason] name. */
    val endReason: String,
    /** Local hour (0-23) of [playedAt], denormalized for time-of-day profiles. */
    val hourOfDay: Int,
    /** ISO day of week (1 = Monday … 7 = Sunday) of [playedAt]. */
    val dayOfWeek: Int,
    /** [ca.ilianokokoro.umihi.music.core.managers.AudioOutputManager.Output] name, if known. */
    val audioOutput: String?,
    /** Queue provenance (album/playlist name, "radio"…) as set by PlayerManager. */
    val sourceContext: String?,
)

/** Per-track rollup of [PlayEvent] rows, one row per distinct youtubeId. */
@Immutable
data class PlayEventAggregate(
    val youtubeId: String,
    val playCount: Int,
    val completedCount: Int,
    val skippedEarlyCount: Int,
    val averageCompletion: Float,
    val totalListenedMs: Long,
    val lastPlayedAt: Long,
)
