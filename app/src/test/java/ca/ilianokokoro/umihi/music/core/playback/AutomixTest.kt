package ca.ilianokokoro.umihi.music.core.playback

import ca.ilianokokoro.umihi.music.automix.TrackAnalysis
import ca.ilianokokoro.umihi.music.automix.TransitionPlan
import ca.ilianokokoro.umihi.music.automix.TransitionStyle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomixPlannedFadeTest {
    private val blend = TransitionPlan(
        transitionStart = 180.0,
        transitionEnd = 186.0,
        fadeSeconds = 6.0,
        incomingCueTime = 2.5,
        transitionStyle = TransitionStyle.EQUAL_POWER,
    )

    @Test
    fun `a plan becomes a fade`() {
        assertEquals(
            PlannedFade(startMs = 180_000L, fadeMs = 6_000L, incomingCueMs = 2_500L),
            AutomixPlanner.toPlannedFade(blend),
        )
    }

    @Test
    fun `a blocked pair is left alone`() {
        assertNull(AutomixPlanner.toPlannedFade(blend.copy(blocked = true)))
    }

    @Test
    fun `a gapless handoff is left alone`() {
        assertNull(AutomixPlanner.toPlannedFade(blend.copy(transitionStyle = TransitionStyle.GAPLESS)))
    }

    @Test
    fun `a fade too short to overlap is left alone`() {
        assertNull(AutomixPlanner.toPlannedFade(blend.copy(fadeSeconds = 1.0)))
    }

    @Test
    fun `styles it cannot play still fade`() {
        val fade = AutomixPlanner.toPlannedFade(blend.copy(transitionStyle = TransitionStyle.DJ_FILTER))
        assertEquals(6_000L, fade?.fadeMs)
    }

    @Test
    fun `the rules follow a planned start rather than the end of the track`() {
        val fade = PlannedFade(startMs = 100_000L, fadeMs = 6_000L)
        assertTrue(CrossfadeRules.shouldArm(96_000L, fade))
        assertEquals(false, CrossfadeRules.shouldArm(90_000L, fade))
        assertEquals(false, CrossfadeRules.shouldArm(100_000L, fade))
        assertEquals(false, CrossfadeRules.isTooLateToStart(100_200L, fade))
        assertTrue(CrossfadeRules.isTooLateToStart(100_400L, fade))
    }

    @Test
    fun `a plain fade covers the end of the track`() {
        assertEquals(
            PlannedFade(startMs = 194_000L, fadeMs = 6_000L),
            CrossfadeRules.plainFade(200_000L, 6_000L),
        )
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AutomixAnalysesTest {
    private val gates = mutableMapOf<String, CompletableDeferred<TrackAnalysis?>>()
    private val started = mutableListOf<String>()
    private val cancelled = mutableListOf<String>()

    private fun analyses(maxEntries: Int = 24) = AutomixAnalyses(
        scope = CoroutineScope(Dispatchers.Unconfined),
        maxEntries = maxEntries,
    ) { id ->
        started += id
        try {
            gates.getOrPut(id) { CompletableDeferred() }.await()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            cancelled += id
            throw cancellation
        }
    }

    private fun finish(id: String, analysis: TrackAnalysis? = TrackAnalysis(trackId = id)) {
        gates.getValue(id).complete(analysis)
    }

    @Test
    fun `works on one track at a time, most urgent first`() {
        val analyses = analyses()
        analyses.want(listOf("a", "b"))
        assertEquals(listOf("a"), started)
        finish("a")
        assertEquals("a", analyses["a"]?.trackId)
        assertEquals(listOf("a", "b"), started)
    }

    @Test
    fun `asking again on every tick starts nothing new`() {
        val analyses = analyses()
        repeat(5) { analyses.want(listOf("a", "b")) }
        assertEquals(listOf("a"), started)
    }

    @Test
    fun `a finished track is not analysed again`() {
        val analyses = analyses()
        analyses.want(listOf("a"))
        finish("a")
        analyses.want(listOf("a"))
        assertEquals(listOf("a"), started)
    }

    @Test
    fun `drops a track that stopped mattering, even halfway through`() {
        val analyses = analyses()
        analyses.want(listOf("a"))
        analyses.want(listOf("b"))
        assertEquals(listOf("a"), cancelled)
        assertEquals(listOf("a", "b"), started)
        assertNull(analyses["a"])
    }

    @Test
    fun `a track it cannot analyse is not retried and blocks nothing`() {
        val analyses = analyses()
        analyses.want(listOf("a", "b"))
        finish("a", null)
        assertEquals(listOf("a", "b"), started)
        analyses.want(listOf("a"))
        assertEquals(listOf("a", "b"), started)
    }

    @Test
    fun `a failure counts as a track it cannot analyse`() {
        val analyses = analyses()
        analyses.want(listOf("a", "b"))
        gates.getValue("a").completeExceptionally(IllegalStateException("bad file"))
        assertNull(analyses["a"])
        assertEquals(listOf("a", "b"), started)
    }

    @Test
    fun `forgets the oldest once full`() {
        val analyses = analyses(maxEntries = 1)
        analyses.want(listOf("a"))
        finish("a")
        analyses.want(listOf("b"))
        finish("b")
        assertNull(analyses["a"])
        assertEquals("b", analyses["b"]?.trackId)
    }

    @Test
    fun `release stops the work`() {
        val analyses = analyses()
        analyses.want(listOf("a", "b"))
        analyses.release()
        assertEquals(listOf("a"), cancelled)
        assertEquals(listOf("a"), started)
    }
}
