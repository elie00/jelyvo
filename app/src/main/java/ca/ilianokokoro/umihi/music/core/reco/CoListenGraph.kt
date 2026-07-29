package ca.ilianokokoro.umihi.music.core.reco

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.core.playback.PlayEndReason
import ca.ilianokokoro.umihi.music.models.PlayEvent
import kotlinx.serialization.Serializable

/** Two plays further apart than this belong to different listening sessions (30 min). */
const val CO_LISTEN_SESSION_GAP_MS = 30L * 60 * 1000

/**
 * Session co-listen graph: an undirected weighted graph over youtubeIds where an edge means
 * "these two tracks were deliberately chained in one session". Stored as plain nested maps so it
 * serializes to JSON without any graph library.
 */
@Immutable
@Serializable
data class CoListenGraph(
    /** youtubeId -> neighbor youtubeId -> cumulative decayed weight. Symmetric by construction. */
    val edges: Map<String, Map<String, Double>> = emptyMap(),
)

/** One neighbor of a track in the co-listen graph, strongest first when ranked. */
@Immutable
@Serializable
data class CoListenNeighbor(
    val youtubeId: String,
    val weight: Double,
)

/**
 * Splits the events into listening sessions: a new session starts whenever the gap between the
 * end of one listen (start + effective listened time) and the start of the next exceeds [gapMs].
 * Events are re-sorted by start time so callers don't have to care about insertion order.
 */
fun sessionize(
    events: List<PlayEvent>,
    gapMs: Long = CO_LISTEN_SESSION_GAP_MS,
): List<List<PlayEvent>> {
    val sorted = events.filter { it.youtubeId.isNotBlank() }.sortedBy { it.playedAt }
    if (sorted.isEmpty()) return emptyList()

    val sessions = mutableListOf<MutableList<PlayEvent>>(mutableListOf(sorted.first()))
    for (event in sorted.drop(1)) {
        val previous = sessions.last().last()
        val previousEnd = previous.playedAt + previous.durationListenedMs
        if (event.playedAt - previousEnd > gapMs) {
            sessions += mutableListOf(event)
        } else {
            sessions.last() += event
        }
    }
    return sessions
}

/**
 * Builds the co-listen graph from [sessionize]d events. An edge (a, b) is added for every pair
 * of *consecutive* plays of a session where the first play was not cut short: a skip (early or
 * late) or a service death between two tracks breaks the chain, because the listener rejected or
 * never chose that transition. Self-transitions (a track replayed back to back) add no edge.
 * Each occurrence adds [tasteDecay] of the second play's start time, so recent pairings dominate.
 */
fun buildCoListenGraph(sessions: List<List<PlayEvent>>, nowMs: Long): CoListenGraph {
    val edges = HashMap<String, HashMap<String, Double>>()

    fun addHalfEdge(from: String, to: String, weight: Double) {
        edges.getOrPut(from) { HashMap() }.merge(to, weight, Double::plus)
    }

    for (session in sessions) {
        for ((first, second) in session.zipWithNext()) {
            if (first.youtubeId == second.youtubeId) continue
            val firstEnd = runCatching { PlayEndReason.valueOf(first.endReason) }
                .getOrDefault(PlayEndReason.UNKNOWN)
            val chained = firstEnd == PlayEndReason.COMPLETED || firstEnd == PlayEndReason.UNKNOWN
            if (!chained) continue

            val weight = tasteDecay(second.playedAt, nowMs)
            addHalfEdge(first.youtubeId, second.youtubeId, weight)
            addHalfEdge(second.youtubeId, first.youtubeId, weight)
        }
    }
    return CoListenGraph(edges = edges)
}

/** The [topN] strongest co-listen neighbors of [songId], heaviest first (ties: stable by id). */
fun coListenNeighbors(graph: CoListenGraph, songId: String, topN: Int): List<CoListenNeighbor> =
    graph.edges[songId].orEmpty()
        .map { (id, weight) -> CoListenNeighbor(youtubeId = id, weight = weight) }
        .sortedWith(compareByDescending<CoListenNeighbor> { it.weight }.thenBy { it.youtubeId })
        .take(topN.coerceAtLeast(0))
