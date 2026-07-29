package ca.ilianokokoro.umihi.music.core.reco

import ca.ilianokokoro.umihi.music.core.playback.PlayEndReason
import ca.ilianokokoro.umihi.music.models.PlayEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoListenGraphTest {

    private val now = 1_700_000_000_000L
    private val minuteMs = 60L * 1000
    private val dayMs = 24L * 60 * minuteMs

    private fun event(
        youtubeId: String,
        playedAt: Long,
        endReason: PlayEndReason = PlayEndReason.COMPLETED,
        listenedMs: Long = 3 * minuteMs,
    ) = PlayEvent(
        youtubeId = youtubeId,
        playedAt = playedAt,
        durationListenedMs = listenedMs,
        trackDurationMs = 3 * minuteMs,
        completionRatio = 1f,
        endReason = endReason.name,
        hourOfDay = 12,
        dayOfWeek = 1,
        audioOutput = null,
        sourceContext = null,
    )

    // --- sessionize ---

    @Test
    fun `no events yields no sessions`() {
        assertTrue(sessionize(emptyList()).isEmpty())
    }

    @Test
    fun `plays within the gap stay in one session`() {
        val sessions = sessionize(
            listOf(
                event("a", now),
                event("b", now + 4 * minuteMs),
                event("c", now + 8 * minuteMs),
            )
        )
        assertEquals(1, sessions.size)
        assertEquals(listOf("a", "b", "c"), sessions.single().map { it.youtubeId })
    }

    @Test
    fun `a silence longer than the gap starts a new session`() {
        val sessions = sessionize(
            listOf(
                event("a", now, listenedMs = 3 * minuteMs),
                // Starts 31 min after "a" *ended* (now + 3 min): a new session.
                event("b", now + 3 * minuteMs + 31 * minuteMs),
            )
        )
        assertEquals(2, sessions.size)
    }

    @Test
    fun `the gap is measured from the end of the previous listen, not its start`() {
        val sessions = sessionize(
            listOf(
                // A one-hour listen followed 10 min after its end: same session.
                event("a", now, listenedMs = 60 * minuteMs),
                event("b", now + 70 * minuteMs),
            )
        )
        assertEquals(1, sessions.size)
    }

    @Test
    fun `events are re-sorted by start time before splitting`() {
        val sessions = sessionize(
            listOf(
                event("b", now + 4 * minuteMs),
                event("a", now),
            )
        )
        assertEquals(listOf("a", "b"), sessions.single().map { it.youtubeId })
    }

    // --- buildCoListenGraph ---

    @Test
    fun `consecutive completed plays build a symmetric edge`() {
        val graph = buildCoListenGraph(
            sessionize(listOf(event("a", now), event("b", now + 4 * minuteMs))),
            nowMs = now + 4 * minuteMs,
        )
        assertEquals(1.0, graph.edges.getValue("a").getValue("b"), 1e-6)
        assertEquals(1.0, graph.edges.getValue("b").getValue("a"), 1e-6)
    }

    @Test
    fun `a skip before the transition cuts the edge`() {
        val graph = buildCoListenGraph(
            sessionize(
                listOf(
                    event("a", now, endReason = PlayEndReason.SKIPPED_EARLY),
                    event("b", now + minuteMs),
                    event("c", now + 5 * minuteMs),
                )
            ),
            nowMs = now + 5 * minuteMs,
        )
        // a -> b was skipped away, only b -> c remains.
        assertTrue(graph.edges["a"].orEmpty().isEmpty())
        assertTrue("c" in graph.edges.getValue("b"))
    }

    @Test
    fun `a session boundary cuts the edge`() {
        val graph = buildCoListenGraph(
            sessionize(
                listOf(
                    event("a", now, listenedMs = 3 * minuteMs),
                    event("b", now + 3 * minuteMs + 31 * minuteMs),
                )
            ),
            nowMs = now + dayMs,
        )
        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun `a back-to-back replay of the same track adds no self edge`() {
        val graph = buildCoListenGraph(
            sessionize(listOf(event("a", now), event("a", now + 4 * minuteMs))),
            nowMs = now + 4 * minuteMs,
        )
        assertTrue(graph.edges.isEmpty())
    }

    @Test
    fun `edge weights decay with the age of the pairing`() {
        val graph = buildCoListenGraph(
            sessionize(
                listOf(
                    event("a", now - 30 * dayMs),
                    event("b", now - 30 * dayMs + 4 * minuteMs),
                )
            ),
            nowMs = now,
        )
        assertEquals(0.5, graph.edges.getValue("a").getValue("b"), 1e-3)
    }

    @Test
    fun `repeated pairings accumulate weight`() {
        val sessionAtDay = { start: Long ->
            listOf(event("a", start), event("b", start + 4 * minuteMs))
        }
        val graph = buildCoListenGraph(
            sessionize(sessionAtDay(now - dayMs)) + sessionize(sessionAtDay(now)),
            nowMs = now + 5 * minuteMs,
        )
        val weight = graph.edges.getValue("a").getValue("b")
        assertTrue("expected a cumulated weight above 1, got $weight", weight > 1.0)
    }

    // --- coListenNeighbors ---

    @Test
    fun `neighbors are ranked by weight and capped at topN`() {
        val graph = CoListenGraph(
            edges = mapOf(
                "seed" to mapOf("weak" to 0.2, "strong" to 2.0, "medium" to 1.0),
            )
        )
        val neighbors = coListenNeighbors(graph, "seed", topN = 2)
        assertEquals(listOf("strong", "medium"), neighbors.map { it.youtubeId })
    }

    @Test
    fun `unknown song has no neighbors`() {
        assertTrue(coListenNeighbors(CoListenGraph(), "missing", topN = 5).isEmpty())
    }
}
