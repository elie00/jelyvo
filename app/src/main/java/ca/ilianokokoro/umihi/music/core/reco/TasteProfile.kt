package ca.ilianokokoro.umihi.music.core.reco

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.core.playback.PLAY_END_COMPLETED_MIN_RATIO
import ca.ilianokokoro.umihi.music.core.playback.PlayEndReason
import ca.ilianokokoro.umihi.music.extensions.normalizedForSearch
import ca.ilianokokoro.umihi.music.models.PlayEvent
import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.ln

/**
 * Local taste profile: pure scoring functions turning raw [PlayEvent] rows, cached likes and the
 * not-interested veto list into per-artist and per-track affinity scores. Everything takes `nowMs`
 * as a parameter so the whole pipeline stays deterministic and unit-testable.
 *
 * Artist key: play events only carry a `youtubeId` — there is no reliable per-track artistId in
 * the local data (play_history stores a display name). The artist key is therefore the display
 * name run through [normalizedForSearch] (lowercased, accents stripped), so "Mylène Farmer" and
 * "mylene farmer" fold into one artist. Collisions between distinct same-named artists are
 * accepted as the cost of that tradeoff.
 */

/** The whole locally computed taste profile, serializable so a snapshot survives restarts. */
@Immutable
@Serializable
data class TasteProfile(
    /** Wall clock the profile was computed at (epoch ms). */
    val computedAtMs: Long,
    val affinity: AffinityScores = AffinityScores(),
    val coListen: CoListenGraph = CoListenGraph(),
    /** Top artists (display names) per time-of-day bucket, see [hourlyArtistDistribution]. */
    val hourlyTopArtists: Map<DayBucket, List<String>> = emptyMap(),
    /** How many play events fed the profile; consumers can gate features on sample size. */
    val eventCount: Int = 0,
)

/** Assembles the full [TasteProfile] from the raw signals. Pure: `nowMs` is injected. */
fun buildTasteProfile(
    events: List<PlayEvent>,
    likedIds: Set<String>,
    notInterestedIds: Set<String>,
    artistByYoutubeId: Map<String, String>,
    nowMs: Long,
): TasteProfile = TasteProfile(
    computedAtMs = nowMs,
    affinity = affinityScores(events, likedIds, notInterestedIds, artistByYoutubeId, nowMs),
    coListen = buildCoListenGraph(sessionize(events), nowMs),
    hourlyTopArtists = hourlyArtistDistribution(events, artistByYoutubeId),
    eventCount = events.size,
)

/** Weight of a like: the strongest positive signal, and one with no decay (no timestamp). */
const val TASTE_WEIGHT_LIKE = 2.0

/** Weight added to every voluntary replay (second and later non-rejected play of a track). */
const val TASTE_WEIGHT_REPLAY = 1.0

/** Weight of a completed listen (>= 80% of the track). */
const val TASTE_WEIGHT_COMPLETED = 0.5

/** Weight of a late abandon: mild dislike. */
const val TASTE_WEIGHT_SKIPPED_LATE = -0.3

/** Weight of an early skip: strong dislike. */
const val TASTE_WEIGHT_SKIPPED_EARLY = -1.0

/** Half-life of a listening signal: after 30 days an event only counts for half. */
const val TASTE_HALF_LIFE_DAYS = 30.0

private const val DAY_MS = 24.0 * 60 * 60 * 1000

/** Exponential decay factor of an event of age `nowMs - playedAtMs`: e^(-ln(2)/30 * ageDays). */
fun tasteDecay(playedAtMs: Long, nowMs: Long): Double {
    val ageDays = ((nowMs - playedAtMs).coerceAtLeast(0L)) / DAY_MS
    return exp(-ln(2.0) / TASTE_HALF_LIFE_DAYS * ageDays)
}

/** Normalized artist grouping key (see the file doc for why a name, not an id). */
fun artistKey(rawArtist: String): String = rawArtist.trim().normalizedForSearch()

/** Affinity maps keyed by [artistKey] and by youtubeId. Positive = liked, negative = rejected. */
@Immutable
@Serializable
data class AffinityScores(
    val artistAffinity: Map<String, Double> = emptyMap(),
    val songAffinity: Map<String, Double> = emptyMap(),
)

/**
 * Scores every listened track and its artist from the raw signals.
 *
 * Per event: COMPLETED (or >= 80% listened when the reason is missing) weighs
 * [TASTE_WEIGHT_COMPLETED], SKIPPED_LATE [TASTE_WEIGHT_SKIPPED_LATE], SKIPPED_EARLY
 * [TASTE_WEIGHT_SKIPPED_EARLY]; APP_KILLED and UNKNOWN carry no intent, so no weight. Every
 * play of a track after its first non-early-skipped one adds [TASTE_WEIGHT_REPLAY] (a voluntary
 * replay). Each event weight is multiplied by [tasteDecay] of its start time. A like adds a flat
 * [TASTE_WEIGHT_LIKE] (likes have no timestamp locally, so they never decay).
 *
 * Not-interested ids are a veto, not a weight: their events and likes are dropped entirely and
 * they never appear in [AffinityScores.songAffinity].
 *
 * @param artistByYoutubeId display artist name per track (from play_history/liked songs); tracks
 * with no known artist still get a song score but contribute nothing to artist affinity.
 */
fun affinityScores(
    events: List<PlayEvent>,
    likedIds: Set<String>,
    notInterestedIds: Set<String>,
    artistByYoutubeId: Map<String, String>,
    nowMs: Long,
): AffinityScores {
    val songScores = HashMap<String, Double>()
    val playsSeen = HashMap<String, Int>()

    for (event in events.sortedBy { it.playedAt }) {
        val id = event.youtubeId
        if (id.isBlank() || id in notInterestedIds) continue

        val reason = runCatching { PlayEndReason.valueOf(event.endReason) }
            .getOrDefault(PlayEndReason.UNKNOWN)
        val base = when {
            reason == PlayEndReason.COMPLETED -> TASTE_WEIGHT_COMPLETED
            reason == PlayEndReason.SKIPPED_LATE -> TASTE_WEIGHT_SKIPPED_LATE
            reason == PlayEndReason.SKIPPED_EARLY -> TASTE_WEIGHT_SKIPPED_EARLY
            // No classified end but most of the track was heard: count it as a completion.
            event.completionRatio >= PLAY_END_COMPLETED_MIN_RATIO -> TASTE_WEIGHT_COMPLETED
            else -> 0.0
        }

        // A replay is voluntary only when the track was not rejected this time around.
        val isRejection = reason == PlayEndReason.SKIPPED_EARLY
        val replays = playsSeen.getOrDefault(id, 0)
        val replayBonus = if (!isRejection && replays > 0) TASTE_WEIGHT_REPLAY else 0.0
        if (!isRejection) playsSeen[id] = replays + 1

        val weight = (base + replayBonus) * tasteDecay(event.playedAt, nowMs)
        if (weight != 0.0) songScores.merge(id, weight, Double::plus)
    }

    for (id in likedIds) {
        if (id.isBlank() || id in notInterestedIds) continue
        songScores.merge(id, TASTE_WEIGHT_LIKE, Double::plus)
    }

    val artistScores = HashMap<String, Double>()
    for ((id, score) in songScores) {
        val artist = artistByYoutubeId[id]?.let(::artistKey).orEmpty()
        if (artist.isNotBlank()) artistScores.merge(artist, score, Double::plus)
    }

    return AffinityScores(artistAffinity = artistScores, songAffinity = songScores)
}
