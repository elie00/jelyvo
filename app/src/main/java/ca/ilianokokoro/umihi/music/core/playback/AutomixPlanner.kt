package ca.ilianokokoro.umihi.music.core.playback

import androidx.media3.common.MediaItem
import ca.ilianokokoro.umihi.music.automix.CrossfadeMode
import ca.ilianokokoro.umihi.music.automix.TrackAnalysis
import ca.ilianokokoro.umihi.music.automix.TransitionPlan
import ca.ilianokokoro.umihi.music.automix.TransitionStyle
import ca.ilianokokoro.umihi.music.automix.TransitionTrackInfo
import ca.ilianokokoro.umihi.music.automix.planTransition
import kotlin.math.roundToLong

/** Asks the Automix planner about a pair of queue items, in the terms the crossfade works in. */
object AutomixPlanner {
    fun plan(
        current: MediaItem,
        next: MediaItem,
        currentAnalysis: TrackAnalysis?,
        nextAnalysis: TrackAnalysis?,
        positionMs: Long,
        durationMs: Long,
        fadeSeconds: Double,
        playingAnAlbum: Boolean,
    ): PlannedFade? = toPlannedFade(
        planTransition(
            analysis = currentAnalysis ?: PENDING,
            nextAnalysis = nextAnalysis ?: PENDING,
            currentTrack = current.toTrackInfo(durationMs),
            nextTrack = next.toTrackInfo(0L),
            currentTime = positionMs / 1000.0,
            duration = durationMs / 1000.0,
            fadeSeconds = fadeSeconds,
            mode = CrossfadeMode.SMART,
            // Only an album really being played through earns its gapless handoff.
            albumSequential = playingAnAlbum,
            mixWindow = true,
        )
    )

    /**
     * Takes what the crossfade can play. It has no filters or speed changes, so any other style is
     * played as the equal-power fade it degrades to. Null leaves the transition as it would be
     * without Automix, which is how a blocked pair or a gapless album handoff should sound.
     */
    fun toPlannedFade(plan: TransitionPlan): PlannedFade? {
        if (plan.blocked || plan.transitionStyle == TransitionStyle.GAPLESS) return null
        if (plan.fadeMs < CrossfadeRules.MIN_OVERLAP_MS) return null
        return PlannedFade(
            startMs = (plan.transitionStart * 1000).roundToLong(),
            fadeMs = plan.fadeMs,
            incomingCueMs = (plan.incomingCueTime * 1000).roundToLong().coerceAtLeast(0L),
        )
    }

    private fun MediaItem.toTrackInfo(durationMs: Long) = TransitionTrackInfo(
        id = mediaId,
        durationMs = durationMs,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        album = mediaMetadata.albumTitle?.toString().orEmpty(),
    )

    /** Not measured yet: the planner answers with the ordinary fade until it is. */
    private val PENDING = TrackAnalysis(status = "pending")
}
