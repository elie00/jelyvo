package ca.ilianokokoro.umihi.music.ui.screens.home

import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.TrackListeningStat

/** The shortcut grid is 2 columns; 8 tiles fills 4 clean rows. */
const val HOME_SHORTCUTS_MAX = 8

/** Below 4 tiles the grid looks like an accident, so it is hidden entirely. */
const val HOME_SHORTCUTS_MIN = 4

/**
 * Builds the compact shortcut tiles shown at the top of the home tab. There is no per-item
 * open counter in the app, so the tiles are derived from what the history table already
 * knows: the most played tracks of the recent window, padded with the latest plays.
 *
 * The result is deduplicated, capped at [HOME_SHORTCUTS_MAX] and trimmed to an even count so
 * the 2-column grid never ends on a lonely half row; fewer than [HOME_SHORTCUTS_MIN] yields
 * an empty list (section hidden).
 */
fun buildHomeShortcuts(
    topTracks: List<TrackListeningStat>,
    recentlyPlayed: List<Song>,
): List<Song> {
    val tiles = (topTracks.map { it.toSong() } + recentlyPlayed)
        .distinctBy { it.youtubeId }
        .take(HOME_SHORTCUTS_MAX)

    val evenCount = tiles.size - (tiles.size % 2)
    if (evenCount < HOME_SHORTCUTS_MIN) return emptyList()
    return tiles.take(evenCount)
}
