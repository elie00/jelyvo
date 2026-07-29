package ca.ilianokokoro.umihi.music.core.reco

import ca.ilianokokoro.umihi.music.core.playback.PlayEndReason
import ca.ilianokokoro.umihi.music.models.PlayEvent

/**
 * The feedback loop of the recommendation engine: reads how the generated mixes were actually
 * received (the [PlayEvent]s whose sourceContext identifies a generated mix) and nudges
 * [RecoWeights] by small, bounded steps. Everything here is pure — events, profile and clock all
 * come in as parameters — so every rule is unit-testable.
 *
 * Design constraints, on purpose:
 *  - simple threshold rules only, no gradient descent: each rule is written out and documented;
 *  - every adjusted weight stays within [TUNER_WEIGHT_MIN_FACTOR] .. [TUNER_WEIGHT_MAX_FACTOR]
 *    of its default, so no amount of noisy feedback can run a weight away;
 *  - only the positive selection terms (song/artist affinity, co-listen, context match, novelty)
 *    are tuned. Fatigue and the skip penalty are computed FROM the same skip data the tuner
 *    reads, so tuning them from it would be circular — they always keep their defaults.
 */

/** Feedback window: only mix plays of the last 7 days influence the weights. */
const val TUNER_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

/** Below this many mix plays in the window, the tuner does nothing (not enough signal). */
const val TUNER_MIN_EVENTS = 20

/** A per-term rule needs at least this many plays dominated by that term to fire. */
const val TUNER_MIN_TERM_EVENTS = 8

/** Early-skip rate above which a mix (or a term's picks) counts as rejected. */
const val TUNER_SKIP_RATE_HIGH = 0.40

/** Early-skip rate below which the mix is healthy and weights relax back toward defaults. */
const val TUNER_SKIP_RATE_LOW = 0.15

/** Correction step: a fired rule moves the targeted weight down by 10 %. */
const val TUNER_STEP = 0.10

/** Relaxation step: a healthy week moves every tuned weight 5 % back toward its default. */
const val TUNER_RELAX_STEP = 0.05

/** Lower bound of a tuned weight, as a factor of its default. */
const val TUNER_WEIGHT_MIN_FACTOR = 0.25

/** Upper bound of a tuned weight, as a factor of its default. */
const val TUNER_WEIGHT_MAX_FACTOR = 2.0

/** Health bucket for events whose sourceContext is null (single-song queues). */
const val HEALTH_CONTEXT_OTHER = "other"

/** The positive [recoScore] terms the tuner can attribute a pick to. */
enum class RecoTerm { SONG_AFFINITY, ARTIST_AFFINITY, CO_LISTEN, CONTEXT_MATCH, NOVELTY }

/**
 * Early-skip health of one sourceContext — the north-star metric of the engine: how often the
 * listener rejects what a given surface (daily mix, context mix, radio…) queues up.
 */
data class SourceContextHealth(
    val sourceContext: String,
    val plays: Int,
    val earlySkips: Int,
) {
    val skipRate: Double get() = if (plays == 0) 0.0 else earlySkips.toDouble() / plays
}

/**
 * Groups [events] played at or after [sinceMs] by sourceContext and computes the early-skip
 * rate of each group, most-played first. APP_KILLED events are excluded (a process death says
 * nothing about the track), and a null sourceContext lands in [HEALTH_CONTEXT_OTHER].
 */
fun skipRatesBySourceContext(events: List<PlayEvent>, sinceMs: Long): List<SourceContextHealth> =
    events
        .filter { it.playedAt >= sinceMs && it.endReason != PlayEndReason.APP_KILLED.name }
        .groupBy { it.sourceContext ?: HEALTH_CONTEXT_OTHER }
        .map { (context, group) ->
            SourceContextHealth(
                sourceContext = context,
                plays = group.size,
                earlySkips = group.count { it.endReason == PlayEndReason.SKIPPED_EARLY.name },
            )
        }
        .sortedWith(compareByDescending<SourceContextHealth> { it.plays }.thenBy { it.sourceContext })

/**
 * Which score term most likely put this track in the mix, recomputed against the CURRENT
 * profile with the CURRENT weights (the profile at generation time is not persisted — a known
 * approximation: a track skipped since then has lost song affinity, which shifts attribution
 * toward the artist/co-listen/context terms). Ties resolve in declaration order of [RecoTerm].
 */
fun dominantTerm(
    event: PlayEvent,
    profile: TasteProfile,
    artistByYoutubeId: Map<String, String>,
    seedIds: Set<String>,
    weights: RecoWeights,
): RecoTerm {
    val id = event.youtubeId
    val songAffinity = profile.affinity.songAffinity[id]
    val artist = artistByYoutubeId[id].orEmpty()
    val key = artistKey(artist)
    val artistAffinity = profile.affinity.artistAffinity[key] ?: 0.0
    val coListenToSeeds = seedIds.sumOf { seed -> profile.coListen.edges[seed]?.get(id) ?: 0.0 }

    val topArtists = profile.hourlyTopArtists[dayBucket(event.hourOfDay)].orEmpty()
    val rank = if (key.isBlank()) -1 else topArtists.indexOfFirst { artistKey(it) == key }
    val contextMatch =
        if (rank >= 0) (topArtists.size - rank).toDouble() / topArtists.size else 0.0

    // Same weighting as recoScore; novelty is approximated by "no song affinity yet".
    return listOf(
        RecoTerm.SONG_AFFINITY to weights.songAffinity * squash01(songAffinity ?: 0.0),
        RecoTerm.ARTIST_AFFINITY to weights.artistAffinity * squash01(artistAffinity),
        RecoTerm.CO_LISTEN to weights.coListen * squash01(coListenToSeeds),
        RecoTerm.CONTEXT_MATCH to weights.contextMatch * contextMatch,
        RecoTerm.NOVELTY to if (songAffinity == null) weights.novelty else 0.0,
    ).maxBy { it.second }.first
}

/**
 * One tuning pass for one mix surface. [events] may be the raw window — only those whose
 * sourceContext equals [sourceContext], played within [TUNER_WINDOW_MS] of [nowMs] and not
 * ended by a process death are considered. Rules, in order:
 *
 *  1. Fewer than [TUNER_MIN_EVENTS] qualifying plays -> return [current] unchanged.
 *  2. Overall early-skip rate > [TUNER_SKIP_RATE_HIGH] -> the mix is too adventurous as a
 *     whole: the novelty weight drops by [TUNER_STEP].
 *  3. Overall early-skip rate < [TUNER_SKIP_RATE_LOW] -> the mix is healthy: every tuned
 *     weight relaxes [TUNER_RELAX_STEP] of the way back toward its default, so past
 *     corrections fade instead of sticking forever.
 *  4. Per term: when at least [TUNER_MIN_TERM_EVENTS] plays are attributed to one term
 *     ([dominantTerm]) and their early-skip rate exceeds [TUNER_SKIP_RATE_HIGH], that term's
 *     weight drops by [TUNER_STEP] — e.g. skips concentrated on high-context-match tracks
 *     lower the contextMatch weight.
 *
 * Every result is clamped to [TUNER_WEIGHT_MIN_FACTOR]..[TUNER_WEIGHT_MAX_FACTOR] of
 * [defaults]; fatigue and skipPenalty pass through untouched (see the file doc).
 */
fun tuneWeights(
    current: RecoWeights,
    events: List<PlayEvent>,
    profile: TasteProfile,
    artistByYoutubeId: Map<String, String>,
    sourceContext: String,
    nowMs: Long,
    defaults: RecoWeights,
): RecoWeights {
    val window = events.filter {
        it.sourceContext == sourceContext &&
            it.playedAt >= nowMs - TUNER_WINDOW_MS &&
            it.playedAt <= nowMs &&
            it.endReason != PlayEndReason.APP_KILLED.name
    }
    if (window.size < TUNER_MIN_EVENTS) return current

    fun earlySkipRate(group: List<PlayEvent>): Double =
        group.count { it.endReason == PlayEndReason.SKIPPED_EARLY.name }.toDouble() / group.size

    var tuned = current
    val overallSkipRate = earlySkipRate(window)
    if (overallSkipRate > TUNER_SKIP_RATE_HIGH) {
        // Rule 2: a rejected mix first loses its taste for discovery.
        tuned = tuned.copy(novelty = tuned.novelty * (1 - TUNER_STEP))
    } else if (overallSkipRate < TUNER_SKIP_RATE_LOW) {
        // Rule 3: a healthy mix slowly forgets past corrections.
        tuned = relaxTowardDefaults(tuned, defaults)
    }

    // Rule 4: term-targeted correction.
    val seeds = topAffinitySeeds(profile)
    window
        .groupBy { dominantTerm(it, profile, artistByYoutubeId, seeds, current) }
        .forEach { (term, group) ->
            if (group.size < TUNER_MIN_TERM_EVENTS) return@forEach
            if (earlySkipRate(group) <= TUNER_SKIP_RATE_HIGH) return@forEach
            val step = 1 - TUNER_STEP
            tuned = when (term) {
                RecoTerm.SONG_AFFINITY -> tuned.copy(songAffinity = tuned.songAffinity * step)
                RecoTerm.ARTIST_AFFINITY ->
                    tuned.copy(artistAffinity = tuned.artistAffinity * step)

                RecoTerm.CO_LISTEN -> tuned.copy(coListen = tuned.coListen * step)
                RecoTerm.CONTEXT_MATCH -> tuned.copy(contextMatch = tuned.contextMatch * step)
                RecoTerm.NOVELTY -> tuned.copy(novelty = tuned.novelty * step)
            }
        }

    return clampWeights(tuned, defaults)
}

/** Moves every tuned weight [TUNER_RELAX_STEP] of the way back toward its default. */
fun relaxTowardDefaults(current: RecoWeights, defaults: RecoWeights): RecoWeights {
    fun relax(value: Double, default: Double) = value + (default - value) * TUNER_RELAX_STEP
    return current.copy(
        songAffinity = relax(current.songAffinity, defaults.songAffinity),
        artistAffinity = relax(current.artistAffinity, defaults.artistAffinity),
        coListen = relax(current.coListen, defaults.coListen),
        contextMatch = relax(current.contextMatch, defaults.contextMatch),
        novelty = relax(current.novelty, defaults.novelty),
    )
}

/** Clamps every tuned weight into its bounded range around [defaults]. */
fun clampWeights(current: RecoWeights, defaults: RecoWeights): RecoWeights {
    fun clamp(value: Double, default: Double) = value.coerceIn(
        default * TUNER_WEIGHT_MIN_FACTOR,
        default * TUNER_WEIGHT_MAX_FACTOR,
    )
    return current.copy(
        songAffinity = clamp(current.songAffinity, defaults.songAffinity),
        artistAffinity = clamp(current.artistAffinity, defaults.artistAffinity),
        coListen = clamp(current.coListen, defaults.coListen),
        contextMatch = clamp(current.contextMatch, defaults.contextMatch),
        novelty = clamp(current.novelty, defaults.novelty),
    )
}
