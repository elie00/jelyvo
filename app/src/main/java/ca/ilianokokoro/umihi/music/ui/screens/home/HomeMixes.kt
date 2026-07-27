package ca.ilianokokoro.umihi.music.ui.screens.home

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.TrackListeningStat

/**
 * Locally generated "For you" mixes, derived from the on-device play history. Nothing is
 * fetched: the history table is the single source of truth, so the mixes stay honest about
 * what was actually listened to.
 */
enum class HomeMixKind {
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
