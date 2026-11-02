package ca.ilianokokoro.umihi.music.core.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * One crossfade: when it starts in the outgoing track, how long it lasts, and where in the incoming
 * track it enters.
 */
data class PlannedFade(val startMs: Long, val fadeMs: Long, val incomingCueMs: Long = 0L)

/** The timing rules of a crossfade, free of any player so they can be tested. */
object CrossfadeRules {
    /** Shorter fades are transitions the existing volume fade already handles cleanly. */
    const val MIN_OVERLAP_MS = 1_500L

    /** How early the tail player is prepared, so it is ready the moment the fade starts. */
    const val ARM_LEAD_MS = 5_000L

    /** How late the fade may start. Past this the listener sought away, so it is abandoned. */
    const val START_SLACK_MS = 300L

    /** Equal power: the two gains' squares always sum to one, so loudness holds steady. */
    fun outgoingGain(progress: Float): Float =
        cos(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

    fun incomingGain(progress: Float): Float =
        sin(progress.coerceIn(0f, 1f) * PI.toFloat() / 2f)

    fun canOverlap(fadeMs: Long, durationMs: Long): Boolean =
        fadeMs >= MIN_OVERLAP_MS && durationMs > 0L && durationMs >= fadeMs * 2

    /** The position in the outgoing track at which the fade begins. */
    fun fadeStartMs(durationMs: Long, fadeMs: Long): Long = durationMs - fadeMs

    /** A plain fade over the very end of the track. */
    fun plainFade(durationMs: Long, fadeMs: Long): PlannedFade =
        PlannedFade(startMs = fadeStartMs(durationMs, fadeMs), fadeMs = fadeMs)

    fun shouldArm(positionMs: Long, fade: PlannedFade): Boolean =
        positionMs in (fade.startMs - ARM_LEAD_MS) until fade.startMs

    fun isTooLateToStart(positionMs: Long, fade: PlannedFade): Boolean =
        positionMs > fade.startMs + START_SLACK_MS

    fun shouldArm(positionMs: Long, durationMs: Long, fadeMs: Long): Boolean {
        val start = fadeStartMs(durationMs, fadeMs)
        return positionMs in (start - ARM_LEAD_MS) until start
    }

    fun isTooLateToStart(positionMs: Long, durationMs: Long, fadeMs: Long): Boolean =
        positionMs > fadeStartMs(durationMs, fadeMs) + START_SLACK_MS
}

/**
 * A real crossfade: both tracks audible at once, one falling as the other rises. One ExoPlayer
 * renders one item at a time, so overlap needs a second decoder.
 *
 * The main player stays the only player the session, the queue and Cast ever see. Just before the
 * fade, a second "tail" player is paused on the current track at the fade's start position. When
 * the main player reaches that position, the tail takes over the end of the track and the main
 * player moves on to the next one. Nothing else in the service has to know a second player exists.
 *
 * Anything unusual (pause, seek, track change, tail not ready) abandons the overlap, which leaves
 * an ordinary transition rather than a broken one. Everything here runs on the main thread.
 */
class CrossfadeController(
    private val main: ExoPlayer,
    private val createTail: () -> ExoPlayer,
    private val scope: CoroutineScope,
    /** The fade this track should end with, or null to leave the transition alone. */
    private val planFade: (durationMs: Long) -> PlannedFade?,
    private val isAllowed: () -> Boolean,
    private val targetVolume: () -> Float,
) {
    private sealed interface State {
        data object Idle : State
        data class Armed(val mediaId: String, val fade: PlannedFade) : State
        data class Fading(
            val startedAtNanos: Long,
            val fadeMs: Long,
            val tailVolume: Float,
        ) : State
    }

    private var state: State = State.Idle
    private var tail: ExoPlayer? = null
    private var loop: Job? = null

    /** True from preparation to the fade's end: the service's own fade-out must stay out. */
    val isActive: Boolean get() = state != State.Idle

    /** True during the fade itself: the main player's volume belongs to this class. */
    val isFading: Boolean get() = state is State.Fading

    fun start() {
        if (loop != null) return
        loop = scope.launch {
            while (true) {
                delay(
                    when (state) {
                        State.Idle -> IDLE_TICK_MS
                        is State.Armed -> ARMED_TICK_MS
                        is State.Fading -> FADE_TICK_MS
                    }
                )
                tick()
            }
        }
    }

    fun release() {
        loop?.cancel()
        loop = null
        tail?.release()
        tail = null
        state = State.Idle
    }

    private fun tick() {
        when (val current = state) {
            State.Idle -> armIfDue()
            is State.Armed -> startIfDue(current)
            is State.Fading -> advance(current)
        }
    }

    private fun eligible(fadeMs: Long): Boolean =
        isAllowed() && main.hasNextMediaItem() && main.repeatMode != Player.REPEAT_MODE_ONE &&
            main.videoSize.width <= 0 &&
            CrossfadeRules.canOverlap(fadeMs, main.duration)

    @OptIn(UnstableApi::class)
    private fun armIfDue() {
        if (!main.isPlaying || !isAllowed() || main.duration <= 0L) return
        val fade = planFade(main.duration) ?: return
        if (!eligible(fade.fadeMs)) return
        if (!CrossfadeRules.shouldArm(main.currentPosition, fade)) return
        val item = main.currentMediaItem ?: return

        val player = tail ?: createTail().also { created ->
            created.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) = abandon()
            })
            tail = created
        }
        // Same session, so the equalizer shapes both tracks alike.
        if (main.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            player.setAudioSessionId(main.audioSessionId)
        }
        player.playbackParameters = main.playbackParameters
        player.volume = 0f
        player.playWhenReady = false
        player.setMediaItem(item, fade.startMs)
        player.prepare()
        state = State.Armed(item.mediaId, fade)
    }

    private fun startIfDue(armed: State.Armed) {
        // The plan is fixed once armed: the tail is already waiting at its start position.
        val fade = armed.fade
        val position = main.currentPosition
        val stillValid = main.playWhenReady && eligible(fade.fadeMs) &&
            main.currentMediaItem?.mediaId == armed.mediaId &&
            !CrossfadeRules.isTooLateToStart(position, fade)
        if (!stillValid) {
            abandon()
            return
        }
        if (position < fade.startMs) return

        val player = tail
        if (player == null || player.playbackState != Player.STATE_READY) {
            abandon()
            return
        }
        val volume = main.volume
        player.volume = volume
        player.play()
        main.volume = 0f
        if (fade.incomingCueMs > 0L) {
            main.seekTo(main.nextMediaItemIndex, fade.incomingCueMs)
        } else {
            main.seekToNextMediaItem()
        }
        state = State.Fading(System.nanoTime(), fade.fadeMs, volume)
    }

    private fun advance(fading: State.Fading) {
        if (!main.playWhenReady) {
            abandon()
            return
        }
        val elapsedMs = (System.nanoTime() - fading.startedAtNanos) / 1_000_000L
        val progress = elapsedMs.toFloat() / fading.fadeMs
        if (progress >= 1f) {
            abandon()
            return
        }
        tail?.volume = fading.tailVolume * CrossfadeRules.outgoingGain(progress)
        main.volume = targetVolume() * CrossfadeRules.incomingGain(progress)
    }

    /** Ends any overlap in progress and hands the main player back at its normal volume. */
    fun abandon() {
        val wasFading = state is State.Fading
        state = State.Idle
        tail?.run {
            stop()
            clearMediaItems()
        }
        if (wasFading) main.volume = targetVolume()
    }

    private companion object {
        const val IDLE_TICK_MS = 500L
        const val ARMED_TICK_MS = 15L
        const val FADE_TICK_MS = 50L
    }
}
