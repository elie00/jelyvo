package ca.ilianokokoro.umihi.music.core.reco

import android.content.Context
import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.ResponseCache
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.data.repositories.HistoryRepository
import ca.ilianokokoro.umihi.music.data.repositories.LibraryRepository
import ca.ilianokokoro.umihi.music.data.repositories.PlayEventRepository
import ca.ilianokokoro.umihi.music.data.repositories.RadioRepository
import ca.ilianokokoro.umihi.music.models.Library
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.adapters.toTrack
import kotlinx.coroutines.flow.first

/**
 * Gathers the candidate pool the daily and context mixes are sequenced from, four sources deep,
 * each tagged with its [CandidateSource] so downstream waves can measure what converts:
 *
 *  1. [CandidateSource.RADIO_RELATED] — RDAMVM radio seeded on the current top-affinity tracks,
 *     the discovery source. Strictly best-effort: offline or failing seeds are skipped.
 *  2. [CandidateSource.LIKED_RELATED] — the same radio fan-out seeded on the most recent liked
 *     songs (library cache order), so a fresh like shifts tomorrow's discoveries.
 *  3. [CandidateSource.CO_LISTEN] — co-listen graph neighbors of the top tracks.
 *  4. [CandidateSource.HISTORY_TOP] — all-time top local tracks, the always-available backbone.
 *
 * New releases of followed artists were considered as a fifth source but skipped:
 * [ca.ilianokokoro.umihi.music.core.workers.NewReleasesWorker] only persists the *notified album
 * browseIds* (DataStore), no reusable song rows — turning albums into candidates would cost one
 * extra network browse per album.
 *
 * Each source is capped so no single one drowns out the others. The returned order (radio,
 * liked-related, neighbors, history) is meaningful: [buildDailyMix] keeps it as the discovery
 * ranking.
 */
class DailyMixCandidateSource(context: Context) {
    private val appContext = context.applicationContext
    private val playEventRepository = PlayEventRepository(appContext)
    private val historyRepository = HistoryRepository(appContext)
    private val datastoreRepository = DatastoreRepository(appContext)
    private val radioRepository = RadioRepository()

    suspend fun collect(profile: TasteProfile): List<DailyMixCandidate<Song>> {
        // youtubeId -> start of its latest play; drives both novelty and the fatigue rule.
        val lastPlayedAt = playEventRepository.aggregatesSince(0L)
            .associate { it.youtubeId to it.lastPlayedAt }

        val historyTop = historyRepository.topTracks(since = 0L, limit = MAX_PER_SOURCE)
            .first()
            .map { it.toSong() }

        val topAffinityIds = profile.affinity.songAffinity
            .filterValues { it > 0.0 }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .map { it.key }

        // Radio seeds need full Song rows; history resolves them, top tracks pad the shortfall.
        val seeds = (resolveSongs(topAffinityIds.take(RADIO_SEED_COUNT)) + historyTop)
            .distinctBy { it.youtubeId }
            .take(RADIO_SEED_COUNT)
        val radioSongs = seeds
            .flatMap { seed -> relatedSongsOrEmpty(seed) }
            .distinctBy { it.youtubeId }
            .take(MAX_PER_SOURCE)

        // Liked songs come straight from the library ResponseCache entry (offline-graceful,
        // like the taste profile) and keep the library order — most recent likes first.
        val likedRelatedSongs = recentLikedSongs()
            .flatMap { seed -> relatedSongsOrEmpty(seed) }
            .distinctBy { it.youtubeId }
            .take(MAX_LIKED_RELATED)

        val neighborIds = topAffinityIds.take(CO_LISTEN_SEED_COUNT)
            .flatMap { id ->
                coListenNeighbors(profile.coListen, id, CO_LISTEN_NEIGHBORS_PER_SEED)
                    .map { it.youtubeId }
            }
            .distinct()
            .take(MAX_PER_SOURCE)
        val neighborSongs = resolveSongs(neighborIds)

        fun tag(
            songs: List<Song>,
            source: CandidateSource,
        ): List<DailyMixCandidate<Song>> = songs
            .filter { it.youtubeId.isNotBlank() }
            .map {
                DailyMixCandidate(
                    track = it.toTrack(),
                    value = it,
                    lastPlayedAtMs = lastPlayedAt[it.youtubeId],
                    source = source,
                )
            }

        return tag(radioSongs, CandidateSource.RADIO_RELATED) +
            tag(likedRelatedSongs, CandidateSource.LIKED_RELATED) +
            tag(neighborSongs, CandidateSource.CO_LISTEN) +
            tag(historyTop, CandidateSource.HISTORY_TOP)
    }

    /** One radio fan-out; any failure (offline, API change) contributes nothing. */
    private suspend fun relatedSongsOrEmpty(seed: Song): List<Song> = runCatching {
        when (val result = radioRepository.getRelated(seed).first { it !is ApiResult.Loading }) {
            is ApiResult.Success -> result.data.take(MAX_PER_RADIO_SEED)
            else -> emptyList()
        }
    }.getOrDefault(emptyList())

    /** The first liked songs of the cached library, whatever the cache age; empty when logged out. */
    private suspend fun recentLikedSongs(): List<Song> = runCatching {
        val settings = datastoreRepository.getSettings()
        ResponseCache.get<Library>(
            key = LibraryRepository.cacheKey(settings),
            ttlMs = Long.MAX_VALUE,
        )?.likedSongs.orEmpty().take(LIKED_SEED_COUNT)
    }.getOrDefault(emptyList())

    /** Resolves ids to full Song rows through the local history, keeping the requested order. */
    private suspend fun resolveSongs(ids: List<String>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val byId = historyRepository.songsByIds(ids).associateBy { it.youtubeId }
        return ids.mapNotNull { byId[it] }
    }

    companion object {
        /** Diversity cap: no source may contribute more than this many candidates. */
        const val MAX_PER_SOURCE = 30

        /** Radio fan-outs per day; each one is a network browse. */
        const val RADIO_SEED_COUNT = 3

        /** Cap per radio seed so one seed cannot fill the whole radio quota alone. */
        const val MAX_PER_RADIO_SEED = 15

        /** Most recent likes used as radio seeds; each one is another network browse. */
        const val LIKED_SEED_COUNT = 2

        /** Cap of the liked-related source, tighter than [MAX_PER_SOURCE] (secondary source). */
        const val MAX_LIKED_RELATED = 20

        /** How many top tracks get their co-listen neighborhoods explored. */
        const val CO_LISTEN_SEED_COUNT = 5

        /** Strongest neighbors kept per explored track. */
        const val CO_LISTEN_NEIGHBORS_PER_SEED = 10
    }
}
