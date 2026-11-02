package ca.ilianokokoro.umihi.music.core.playback

import ca.ilianokokoro.umihi.music.automix.TrackAnalysis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Keeps the analyses of the tracks around the playhead, working on one track at a time. Decoding a
 * track is slow, so whatever stops being wanted is dropped, even halfway through.
 *
 * Only to be used from the thread behind [scope].
 *
 * @param analyze null means this track cannot be analysed. It is not tried again.
 */
class AutomixAnalyses(
    private val scope: CoroutineScope,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val analyze: suspend (id: String) -> TrackAnalysis?,
) {
    private val finished = object : LinkedHashMap<String, TrackAnalysis?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackAnalysis?>) =
            size > maxEntries
    }
    private var wanted: List<String> = emptyList()
    private var working: String? = null
    private var job: Job? = null

    operator fun get(id: String): TrackAnalysis? = finished[id]

    /** Says which tracks matter now, most urgent first. Safe to call on every tick. */
    fun want(ids: List<String>) {
        wanted = ids
        val current = working
        if (current != null && current !in ids) {
            job?.cancel()
            working = null
            job = null
        }
        startNext()
    }

    fun release() {
        job?.cancel()
        job = null
        working = null
        wanted = emptyList()
    }

    private fun startNext() {
        if (working != null) return
        val id = wanted.firstOrNull { it !in finished } ?: return
        working = id
        job = scope.launch {
            val analysis = try {
                analyze(id)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A track that cannot be read only loses its smart transition.
                null
            }
            finished[id] = analysis
            working = null
            job = null
            startNext()
        }
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 24
    }
}
