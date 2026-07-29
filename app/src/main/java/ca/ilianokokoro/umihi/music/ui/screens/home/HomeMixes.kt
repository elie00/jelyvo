package ca.ilianokokoro.umihi.music.ui.screens.home

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.core.reco.DayBucket
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.TrackListeningStat

/**
 * Locally generated "For you" mixes, derived from the on-device play history. Nothing is
 * fetched: the history table is the single source of truth, so the mixes stay honest about
 * what was actually listened to.
 */
enum class HomeMixKind {
    /** The taste-profile-driven mix of the day, regenerated daily; see core/reco/DailyMix.kt. */
    DAILY,

    /** The morning slice of the context mix (5-11 h); see core/reco/ContextMix.kt. */
    CONTEXT_MORNING,

    /** The evening slice of the context mix (from 18 h); see core/reco/ContextMix.kt. */
    CONTEXT_EVENING,

    /** The most played tracks of the recent period. */
    REPLAY,

    /** Tracks that used to be on repeat but have not been played for a while. */
    REDISCOVER,
}

@Immutable
data class HomeMix(
    val kind: HomeMixKind,
    val songs: List<Song>,
)

/** A mix below this size would feel like a broken playlist, so it is not offered at all. */
const val HOME_MIX_MIN_SONGS = 5

/** Queue length cap so a mix stays a session, not the whole history. */
const val HOME_MIX_MAX_SONGS = 25

/** A track qualifies for rediscovery after this much silence (30 days). */
const val HOME_MIX_REDISCOVER_GAP_MS = 30L * 24 * 60 * 60 * 1000

/**
 * Builds the "For you" mixes from listening stats.
 *
 * @param recentTopTracks top tracks of the replay window, already sorted by play count.
 * @param allTimeTopTracks all-time top tracks (larger sample), already sorted by play count.
 * @param nowMs current wall clock, injected so the logic stays pure and testable.
 */
fun buildForYouMixes(
    recentTopTracks: List<TrackListeningStat>,
    allTimeTopTracks: List<TrackListeningStat>,
    nowMs: Long,
): List<HomeMix> {
    val mixes = mutableListOf<HomeMix>()

    val replay = recentTopTracks
        .take(HOME_MIX_MAX_SONGS)
        .map { it.toSong() }
    if (replay.size >= HOME_MIX_MIN_SONGS) {
        mixes += HomeMix(HomeMixKind.REPLAY, replay)
    }

    // Rediscoveries: tracks played more than once historically but silent for a month.
    val rediscover = allTimeTopTracks
        .filter { it.playCount >= 2 && it.lastPlayedAt < nowMs - HOME_MIX_REDISCOVER_GAP_MS }
        .take(HOME_MIX_MAX_SONGS)
        .map { it.toSong() }
    if (rediscover.size >= HOME_MIX_MIN_SONGS) {
        mixes += HomeMix(HomeMixKind.REDISCOVER, rediscover)
    }

    return mixes
}

/**
 * Prepends the daily mix card to the history-derived mixes. The daily mix always takes the
 * first slot: it is the freshest, most personalized entry of the row. Skipped entirely while
 * too small to feel like a real mix (below [HOME_MIX_MIN_SONGS]).
 */
fun withDailyMix(dailyMixSongs: List<Song>, mixes: List<HomeMix>): List<HomeMix> =
    if (dailyMixSongs.size >= HOME_MIX_MIN_SONGS) {
        listOf(HomeMix(HomeMixKind.DAILY, dailyMixSongs)) + mixes
    } else {
        mixes
    }

/**
 * Inserts the context (morning/evening) mix card right behind the daily mix — or in front when
 * there is no daily mix. Only the two slots that actually have a card are honoured: any other
 * bucket (or none — outside both slots) leaves the row untouched, as does a too-small mix.
 */
fun withContextMix(
    contextMixSongs: List<Song>,
    bucket: DayBucket?,
    mixes: List<HomeMix>,
): List<HomeMix> {
    val kind = when (bucket) {
        DayBucket.MORNING -> HomeMixKind.CONTEXT_MORNING
        DayBucket.EVENING -> HomeMixKind.CONTEXT_EVENING
        else -> return mixes
    }
    if (contextMixSongs.size < HOME_MIX_MIN_SONGS) return mixes

    val insertAt = if (mixes.firstOrNull()?.kind == HomeMixKind.DAILY) 1 else 0
    return mixes.toMutableList().apply { add(insertAt, HomeMix(kind, contextMixSongs)) }
}
