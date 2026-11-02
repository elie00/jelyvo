package ca.ilianokokoro.umihi.music.automix

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelAnalysisTest {
    private fun frames(seconds: Double) = (seconds / LevelAnalysis.FRAME_SECONDS).toInt()

    /** Sections of constant loudness, in dB. Null is digital silence. */
    private fun track(vararg sections: Pair<Double, Double?>): FloatArray =
        sections.flatMap { (seconds, db) ->
            val level = db?.let { 10.0.pow(it / 20).toFloat() } ?: 0f
            List(frames(seconds)) { level }
        }.toFloatArray()

    private val withQuietEnding = track(
        3.0 to null,
        180.0 to -12.0,
        5.0 to -40.0,
        4.0 to null,
    )

    @Test
    fun `finds where the sound starts and ends`() {
        val analysis = LevelAnalysis.analyze("a", withQuietEnding)
        assertEquals(TrackAnalysis.STATUS_READY, analysis.status)
        assertEquals("a", analysis.trackId)
        assertEquals(192.0, analysis.duration, 0.01)
        assertEquals(3.0, analysis.audibleStartTime!!, 0.01)
        assertEquals(188.0, analysis.contentEndTime, 0.01)
    }

    @Test
    fun `finds a quiet ending`() {
        assertEquals(183.0, LevelAnalysis.analyze("a", withQuietEnding).outroStartTime, 1.0)
    }

    @Test
    fun `a track at full level to its end has no quiet ending`() {
        val analysis = LevelAnalysis.analyze("a", track(200.0 to -12.0))
        assertEquals(0.0, analysis.outroStartTime, 0.0)
        assertEquals(200.0, analysis.contentEndTime, 0.01)
    }

    @Test
    fun `a quiet passage in the middle is not an ending`() {
        val analysis = LevelAnalysis.analyze(
            "a",
            track(100.0 to -12.0, 20.0 to -40.0, 80.0 to -12.0),
        )
        assertEquals(0.0, analysis.outroStartTime, 0.0)
    }

    @Test
    fun `a brief quiet moment at the end is not an ending`() {
        val analysis = LevelAnalysis.analyze("a", track(200.0 to -12.0, 1.0 to -40.0))
        assertEquals(0.0, analysis.outroStartTime, 0.0)
    }

    @Test
    fun `a very long quiet ending is part of the music`() {
        val analysis = LevelAnalysis.analyze("a", track(100.0 to -12.0, 60.0 to -40.0))
        assertEquals(0.0, analysis.outroStartTime, 0.0)
    }

    @Test
    fun `silence is ready but claims nothing`() {
        val analysis = LevelAnalysis.analyze("a", track(200.0 to null))
        assertEquals(TrackAnalysis.STATUS_READY, analysis.status)
        assertNull(analysis.audibleStartTime)
        assertEquals(0.0, analysis.contentEndTime, 0.0)
    }

    @Test
    fun `it never claims a tempo`() {
        val analysis = LevelAnalysis.analyze("a", withQuietEnding)
        assertEquals(0.0, analysis.bpm, 0.0)
        assertEquals(0.0, analysis.beatConfidence, 0.0)
    }

    @Test
    fun `the energy curve stays small`() {
        val curve = LevelAnalysis.analyze("a", withQuietEnding).energyCurve
        assertTrue(curve.size in 200..400, "was ${curve.size}")
    }

    private fun plan(current: FloatArray, next: FloatArray, at: Double = 0.0): TransitionPlan =
        planTransition(
            analysis = LevelAnalysis.analyze("a", current),
            nextAnalysis = LevelAnalysis.analyze("b", next),
            currentTrack = TransitionTrackInfo("a", (current.size * 100).toLong(), title = "A"),
            nextTrack = TransitionTrackInfo("b", (next.size * 100).toLong(), title = "B"),
            currentTime = at,
            duration = current.size * LevelAnalysis.FRAME_SECONDS,
            fadeSeconds = 6.0,
            mode = CrossfadeMode.SMART,
            mixWindow = true,
        )

    @Test
    fun `the planner leaves before a quiet ending and enters after leading silence`() {
        val plan = plan(withQuietEnding, track(2.5 to null, 180.0 to -12.0))
        assertFalse(plan.blocked, plan.reason)
        assertEquals(TransitionStyle.EQUAL_POWER, plan.transitionStyle)
        assertEquals(183.0 - 6.0, plan.transitionStart, 1.0)
        assertEquals(6.0, plan.fadeSeconds, 0.5)
        assertEquals(2.5, plan.incomingCueTime, 0.01)
    }

    @Test
    fun `a longer quiet ending is music the planner will not cut`() {
        val current = track(180.0 to -12.0, 20.0 to -40.0)
        assertEquals(180.0, LevelAnalysis.analyze("a", current).outroStartTime, 1.0)
        val plan = plan(current, track(180.0 to -12.0))
        assertEquals(200.0 - 6.0, plan.transitionStart, 0.5)
    }

    @Test
    fun `without a quiet ending the fade ends with the sound`() {
        val plan = plan(track(190.0 to -12.0, 5.0 to null), track(180.0 to -12.0))
        assertEquals(TransitionStyle.EQUAL_POWER, plan.transitionStyle)
        assertEquals(190.0 - 6.0, plan.transitionStart, 0.5)
        assertEquals(0.0, plan.incomingCueTime, 0.01)
    }

    @Test
    fun `with no tempo the plan never asks for a change of speed`() {
        val plan = plan(withQuietEnding, withQuietEnding)
        assertEquals(1.0, plan.incomingPlaybackRate, 0.0)
        assertEquals(1.0, plan.outgoingPlaybackRate, 0.0)
        assertFalse(plan.phaseLock)
    }

    @Test
    fun `the plan only starts once the song reaches it`() {
        val incoming = track(180.0 to -12.0)
        assertFalse(plan(withQuietEnding, incoming, at = 100.0).shouldStart)
        assertTrue(plan(withQuietEnding, incoming, at = 178.0).shouldStart)
    }
}

class LevelMeterTest {
    @Test
    fun `measures one level per frame across channels`() {
        val meter = LevelMeter(sampleRate = 100, channels = 2)
        repeat(20) { meter.add(0.5f) }
        repeat(20) { meter.add(-0.25f) }
        val levels = meter.finish()
        assertEquals(2, levels.size)
        assertEquals(0.5f, levels[0], 0.001f)
        assertEquals(0.25f, levels[1], 0.001f)
    }

    @Test
    fun `counts an unfinished last frame`() {
        val meter = LevelMeter(sampleRate = 100, channels = 1)
        repeat(15) { meter.add(0.5f) }
        assertEquals(2, meter.finish().size)
    }

    @Test
    fun `grows past its first allocation`() {
        val meter = LevelMeter(sampleRate = 10, channels = 1)
        repeat(5_000) { meter.add(0.1f) }
        assertEquals(5_000, meter.finish().size)
    }

    @Test
    fun `nothing in, nothing out`() {
        assertEquals(0, LevelMeter(sampleRate = 44_100, channels = 2).finish().size)
    }
}
