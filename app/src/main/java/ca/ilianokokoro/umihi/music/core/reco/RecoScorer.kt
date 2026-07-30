package ca.ilianokokoro.umihi.music.core.reco

import kotlinx.serialization.Serializable
import kotlin.math.max

/**
 * The single candidate scoring function every locally generated mix ranks with. Pure: all inputs
 * (profile, context, weights) are parameters, so a score is fully reproducible in tests.
 *
 * score = w_songAffinity * songAffinity+ + w_artistAffinity * artistAffinity+
 *       + w_coListen * coListenToSeeds + w_contextMatch * contextMatch
 *       + w_novelty * novelty - w_fatigue * fatigue - w_skipPenalty * songAffinity-
 * and the not-interested list stays a hard veto (a vetoed track has no score at all).
 *
 * Normalization: every term is brought into [0, 1] BEFORE weighting, so the weights express
 * relative intent instead of compensating for units. Natively bounded terms (context match,
 * novelty, fatigue) are used as-is; unbounded cumulative quantities (affinities, co-listen
 * weight) go through [squash01] — x / (1 + x), a monotonic map of [0, +inf) onto [0, 1) that
 * preserves ordering while taming outliers. Affinity is split at zero: the positive part feeds
 * the affinity terms, the negative part (accumulated skips) feeds the skip penalty.
 */

/** How many top-affinity tracks act as co-listen seeds when a caller has no better set. */
const val RECO_SEED_COUNT = 5

/**
 * Term weights of [recoScore]. All defaults are named so later waves can tune them; product
 * presets live in the companion ([DAILY][Companion.DAILY], [CONTEXT][Companion.CONTEXT]).
 * Serializable because the tuned copies persist across restarts ([RecoWeightsStore]).
 */
@Serializable
data class RecoWeights(
    /** Positive per-track affinity: the main "you love this exact song" signal. */
    val songAffinity: Double = 1.0,
    /** Positive per-artist affinity: softer, catches unheard tracks of loved artists. */
    val artistAffinity: Double = 0.4,
    /** Co-listen edge weight from the seed tracks: "you chain these". */
    val coListen: Double = 0.6,
    /** Artist presence in the current time-of-day bucket's top artists. */
    val contextMatch: Double = 0.5,
    /** Bonus for tracks never played locally (discovery). */
    val novelty: Double = 0.3,
    /** Penalty for tracks played within the last 48 h, strongest right after a play. */
    val fatigue: Double = 0.8,
    /** Penalty for accumulated negative affinity (skips). */
    val skipPenalty: Double = 1.0,
) {
    companion object {
        /** The daily mix profile: balanced, affinity-led. */
        val DAILY = RecoWeights()

        /** The context (morning/evening) mix profile: the hour bucket leads the ranking. */
        val CONTEXT = RecoWeights(contextMatch = 2.0, novelty = 0.2)
    }
}

/**
 * The listening situation a score is computed for.
 *
 * [audioOutput] is accepted for symmetry with [ca.ilianokokoro.umihi.music.models.PlayEvent]
 * but is not scored yet: the wave-B profile only aggregates by hour bucket, not by output. It
 * rides along so wave E can start weighing it without touching every call site.
 */
data class RecoContext(
    val nowMs: Long,
    /** Current time-of-day bucket; null disables the context-match term. */
    val dayBucket: DayBucket? = null,
    /** Current audio output ([ca.ilianokokoro.umihi.music.core.managers.AudioOutputManager]). */
    val audioOutput: String? = null,
    /** Tracks whose co-listen neighborhoods count as "close to what you play"; see [topAffinitySeeds]. */
    val seedIds: Set<String> = emptySet(),
    val notInterestedIds: Set<String> = emptySet(),
)

/** Monotonic squash of [0, +inf) onto [0, 1): x / (1 + x). Negative inputs clamp to 0. */
fun squash01(x: Double): Double = max(0.0, x) / (1.0 + max(0.0, x))

/** Default seed set for [RecoContext.seedIds]: the top positive-affinity tracks, ties by id. */
fun topAffinitySeeds(profile: TasteProfile, count: Int = RECO_SEED_COUNT): Set<String> =
    profile.affinity.songAffinity
        .filterValues { it > 0.0 }
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
        .take(count.coerceAtLeast(0))
        .mapTo(LinkedHashSet()) { it.key }

/**
 * Scores one candidate; higher is a better pick right now. Returns null when the track is
 * vetoed (blank id or not-interested) — a veto is an absence, not a very low score.
 */
fun <T> recoScore(
    candidate: DailyMixCandidate<T>,
    profile: TasteProfile,
    context: RecoContext,
    weights: RecoWeights = RecoWeights.DAILY,
): Double? {
    val id = candidate.track.id
    if (id.isBlank() || id in context.notInterestedIds) return null

    val songAffinity = profile.affinity.songAffinity[id] ?: 0.0
    val artistAffinity =
        profile.affinity.artistAffinity[artistKey(candidate.track.artist.name)] ?: 0.0
    val coListenToSeeds = context.seedIds.sumOf { seed ->
        profile.coListen.edges[seed]?.get(id) ?: 0.0
    }

    return weights.songAffinity * squash01(songAffinity) +
        weights.artistAffinity * squash01(artistAffinity) +
        weights.coListen * squash01(coListenToSeeds) +
        weights.contextMatch * contextMatchTerm(candidate, profile, context.dayBucket) +
        weights.novelty * (if (candidate.lastPlayedAtMs == null) 1.0 else 0.0) -
        weights.fatigue * fatigueTerm(candidate, context.nowMs) -
        weights.skipPenalty * squash01(-songAffinity)
}

/**
 * Context match in [0, 1]: rank of the candidate's artist among the current bucket's top
 * artists, 1.0 for the first, sliding down to 1/n for the last, 0 outside the list (or with no
 * bucket). Names are compared through [artistKey], matching how the hourly profile groups them.
 */
private fun <T> contextMatchTerm(
    candidate: DailyMixCandidate<T>,
    profile: TasteProfile,
    dayBucket: DayBucket?,
): Double {
    val bucket = dayBucket ?: return 0.0
    val topArtists = profile.hourlyTopArtists[bucket].orEmpty()
    if (topArtists.isEmpty()) return 0.0

    val key = artistKey(candidate.track.artist.name)
    if (key.isBlank()) return 0.0
    val rank = topArtists.indexOfFirst { artistKey(it) == key }
    return if (rank >= 0) (topArtists.size - rank).toDouble() / topArtists.size else 0.0
}

/**
 * Fatigue in [0, 1]: 1.0 right after a play, linearly down to 0 at [DAILY_MIX_FATIGUE_MS] (48 h),
 * 0 beyond. Clock skew (a play "in the future") clamps to full fatigue.
 */
private fun <T> fatigueTerm(candidate: DailyMixCandidate<T>, nowMs: Long): Double {
    val lastPlayedAt = candidate.lastPlayedAtMs ?: return 0.0
    val age = nowMs - lastPlayedAt
    if (age < 0) return 1.0
    if (age >= DAILY_MIX_FATIGUE_MS) return 0.0
    return 1.0 - age.toDouble() / DAILY_MIX_FATIGUE_MS
}
