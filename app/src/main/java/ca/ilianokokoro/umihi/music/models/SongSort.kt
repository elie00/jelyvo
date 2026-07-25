package ca.ilianokokoro.umihi.music.models

import ca.ilianokokoro.umihi.music.extensions.durationToSeconds
import ca.ilianokokoro.umihi.music.extensions.normalizedForSearch

/**
 * Sort axes offered inside a playlist.
 *
 * [PLAYLIST_ORDER] is the stored order and is also what a "date added" axis would be: neither
 * [Song] nor [LocalPlaylistSong] carries a timestamp, and both lists are only ever appended to, so
 * the stored order already is the order the songs were added. A separate "Date added" entry would
 * be a second label for this one.
 */
enum class SongSort {
    PLAYLIST_ORDER,
    TITLE,
    ARTIST,
    DURATION,
}

/**
 * Reorders [this] on a display field, keeping equal keys in their existing order (sortedBy is a
 * stable sort). Text keys go through [normalizedForSearch] so "Élan" sorts next to "Elan" instead
 * of after "Zoo" — the same comparison the search filter already uses.
 */
fun List<Song>.sortedForPlaylist(sort: SongSort): List<Song> = when (sort) {
    SongSort.PLAYLIST_ORDER -> this
    SongSort.TITLE -> sortedBy { it.title.normalizedForSearch() }
    SongSort.ARTIST -> sortedBy { it.artist.normalizedForSearch() }
    SongSort.DURATION -> sortedBy { it.duration.durationToSeconds() }
}

// Same body for the on-device row type. List<Song> and List<LocalPlaylistSong> erase to the same
// JVM signature, hence the explicit name.
@JvmName("sortedForPlaylistLocal")
fun List<LocalPlaylistSong>.sortedForPlaylist(sort: SongSort): List<LocalPlaylistSong> =
    when (sort) {
        SongSort.PLAYLIST_ORDER -> this
        SongSort.TITLE -> sortedBy { it.title.normalizedForSearch() }
        SongSort.ARTIST -> sortedBy { it.artist.normalizedForSearch() }
        SongSort.DURATION -> sortedBy { it.duration.durationToSeconds() }
    }
