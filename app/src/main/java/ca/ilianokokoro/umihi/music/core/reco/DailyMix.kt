package ca.ilianokokoro.umihi.music.core.reco

import ca.ilianokokoro.umihi.music.core.domain.Track
import java.time.Instant
import java.time.ZoneId

/**
 * "Daily mix" sequencing: a pure function turning the taste profile and a pool of candidate
 * tracks into an ordered queue for the day. Everything takes `nowMs` as a parameter so the
 * logic stays deterministic and unit-testable.
 *
 * Sequencing rules, in order of authority:
 *  - not-interested ids are a veto applied before anything else, and tracks with a negative
 *    affinity (net dislike) are dropped too;
 *  - fatigue: a track played less than [DAILY_MIX_FATIGUE_MS] ago is kept out of the primary
 *    pools — but not discarded: when both pools run dry the mix degrades to those fatigued
 *    tracks rather than staying too short;
 *  - the opener is the highest-affinity familiar track not played within the fatigue window;
 *  - roughly [DAILY_MIX_FAMILIAR_RATIO] of the mix is familiar (affinity > 0 or already
 *    played), the rest is new (never seen in play_events);
 *  - two new tracks never play back to back — a hard rule: if only new candidates remain right
 *    after a new pick, the mix ends shorter instead of breaking it;
 *  - the same artist never appears twice within a sliding window of [DAILY_MIX_ARTIST_WINDOW]
 *    tracks; with a pool too poor to honour it the constraint is relaxed as a last resort.
 */

/** Number of tracks a full daily mix aims for. */
const val DAILY_MIX_TARGET_SIZE = 28

/** Share of the mix reserved for familiar tracks; the remainder is discovery. */
const val DAILY_MIX_FAMILIAR_RATIO = 0.7

/** A track played less recently than this is eligible again (48 h). */
const val DAILY_MIX_FATIGUE_MS = 48L * 60 * 60 * 1000

/** Sliding window within which an artist must not repeat. */
const val DAILY_MIX_ARTIST_WINDOW = 5

/** A mix below this size would feel broken, so it is not surfaced at all. */
const val DAILY_MIX_MIN_SONGS = 5

/**
 * Stable identifier stored in [ca.ilianokokoro.umihi.music.models.PlayEvent.sourceContext] for
 * plays started from the daily mix card — the feedback loop keys on this exact value, never on
 * the localized card title.
 */
const val DAILY_MIX_SOURCE_CONTEXT = "daily-mix"

/**
 * Where a mix candidate came from. The label travels with the candidate so later waves can
 * measure which sources actually convert into listens.
 */
enum class CandidateSource {
    /** RDAMVM radio fan-out seeded on top-affinity tracks. */
    RADIO_RELATED,

    /** RDAMVM radio fan-out seeded on the most recent liked songs. */
    LIKED_RELATED,

    /** Co-listen graph neighbors of the top-affinity tracks. */
    CO_LISTEN,

    /** All-time top tracks of the local play history. */
    HISTORY_TOP,
}

/**
 * One platform-neutral track offered to [buildDailyMix], with its original platform value carried
 * opaquely so the pure ranking layer never needs to know about Media3 or Room.
 */
data class DailyMixCandidate<out T>(
    val track: Track,
    val value: T,
    /** Start of the most recent play event of this track; null when never played locally. */
    val lastPlayedAtMs: Long? = null,
    /** Which source suggested the track; null for callers that don't track provenance. */
    val source: CandidateSource? = null,
)

/** Local-time day stamp (epoch day) a generated mix is valid for. */
fun dailyMixEpochDay(epochMs: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().toEpochDay()

/**
 * Builds the ordered daily mix from [candidates]. Candidate order is meaningful for discovery:
 * new tracks keep their source ranking, so callers should list their strongest suggestions
 * first. Duplicate track ids keep their first occurrence only.
 */
fun <T> buildDailyMix(
    profile: TasteProfile,
    candidates: List<DailyMixCandidate<T>>,
    notInterestedIds: Set<String>,
    nowMs: Long,
    targetSize: Int = DAILY_MIX_TARGET_SIZE,
    weights: RecoWeights = RecoWeights.DAILY,
    dayBucket: DayBucket? = null,
): List<T> {
    if (targetSize <= 0) return emptyList()

    val affinity = profile.affinity.songAffinity
    fun affinityOf(candidate: DailyMixCandidate<T>): Double =
        affinity[candidate.track.id] ?: 0.0

    // Veto first, dedupe second (first occurrence wins), net dislikes out last.
    val unique = candidates
        .filter { it.track.id.isNotBlank() && it.track.id !in notInterestedIds }
        .distinctBy { it.track.id }
        .filter { affinityOf(it) >= 0.0 }

    // Internal ranking goes through the shared scorer; the sequencing constraints below stay
    // authoritative over it. Scores are precomputed once — sorting recomputes comparisons.
    val recoContext = RecoContext(
        nowMs = nowMs,
        dayBucket = dayBucket,
        seedIds = topAffinitySeeds(profile),
        notInterestedIds = notInterestedIds,
    )
    val scores = unique.associate {
        it.track.id to (recoScore(it, profile, recoContext, weights) ?: 0.0)
    }
    val byScoreDesc =
        compareByDescending<DailyMixCandidate<T>> { scores[it.track.id] ?: 0.0 }
            .thenBy { it.track.id }

    val fatigueCutoff = nowMs - DAILY_MIX_FATIGUE_MS
    fun isFresh(candidate: DailyMixCandidate<T>): Boolean {
        val lastPlayedAt = candidate.lastPlayedAtMs
        return lastPlayedAt == null || lastPlayedAt < fatigueCutoff
    }

    // Familiar = a positive affinity (plays or likes) or any local play at all. What is neither
    // familiar nor vetoed has never been heard: that is the discovery pool.
    fun isFamiliar(candidate: DailyMixCandidate<T>): Boolean =
        affinityOf(candidate) > 0.0 || candidate.lastPlayedAtMs != null

    // Sorted by score, so the natural first pick is the anchor: the highest-scored familiar
    // track outside the fatigue window. The new pool keeps its source ranking on purpose.
    val familiarPool = unique.filter { isFamiliar(it) && isFresh(it) }
        .sortedWith(byScoreDesc)
        .toMutableList()
    val newPool = unique.filter { !isFamiliar(it) }.toMutableList()

    // Degraded pool: played less than 48 h ago. Only drawn from once both fresh pools are
    // exhausted, so a poor candidate day still yields a usable mix.
    val fatiguedPool = unique.filter { isFamiliar(it) && !isFresh(it) }
        .sortedWith(byScoreDesc)
        .toMutableList()

    val result = mutableListOf<DailyMixCandidate<T>>()
    var familiarCount = 0
    var lastWasNew = false

    fun artistOk(candidate: DailyMixCandidate<T>): Boolean {
        val key = artistKey(candidate.track.artist.name)
        if (key.isBlank()) return true
        return result.takeLast(DAILY_MIX_ARTIST_WINDOW - 1)
            .none { artistKey(it.track.artist.name) == key }
    }

    fun pickFrom(
        pool: MutableList<DailyMixCandidate<T>>,
        enforceArtistWindow: Boolean,
    ): DailyMixCandidate<T>? {
        val index = pool.indexOfFirst { !enforceArtistWindow || artistOk(it) }
        return if (index >= 0) pool.removeAt(index) else null
    }

    while (result.size < targetSize) {
        // Familiar is due whenever picking anything else would drop below the target ratio;
        // it is also forced right after a new track (never two discoveries in a row).
        val wantFamiliar =
            lastWasNew || familiarCount < DAILY_MIX_FAMILIAR_RATIO * (result.size + 1)

        val fromNewAllowed = !lastWasNew
        val pick = if (wantFamiliar) {
            pickFrom(familiarPool, true)
                ?: (if (fromNewAllowed) pickFrom(newPool, true) else null)
                ?: pickFrom(fatiguedPool, true)
                // Last resort for poor pools: relax the artist window, same pool order.
                ?: pickFrom(familiarPool, false)
                ?: (if (fromNewAllowed) pickFrom(newPool, false) else null)
                ?: pickFrom(fatiguedPool, false)
        } else {
            pickFrom(newPool, true)
                ?: pickFrom(familiarPool, true)
                ?: pickFrom(fatiguedPool, true)
                ?: pickFrom(newPool, false)
                ?: pickFrom(familiarPool, false)
                ?: pickFrom(fatiguedPool, false)
        } ?: break

        val pickedNew = !isFamiliar(pick)
        if (!pickedNew) familiarCount++
        lastWasNew = pickedNew
        result += pick
    }

    return result.map { it.value }
}
