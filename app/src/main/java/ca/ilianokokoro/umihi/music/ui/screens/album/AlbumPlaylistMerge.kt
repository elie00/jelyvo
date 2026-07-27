package ca.ilianokokoro.umihi.music.ui.screens.album

import ca.ilianokokoro.umihi.music.models.LocalPlaylistSong

/**
 * Appends [added] to [existing] while keeping the playlist's current order and skipping tracks
 * already present (same id). Duplicates inside [added] itself are collapsed too, so adding an
 * album twice (or an album that repeats a track) never creates double entries.
 */
fun mergeSongsIntoLocalPlaylist(
    existing: List<LocalPlaylistSong>,
    added: List<LocalPlaylistSong>,
): List<LocalPlaylistSong> {
    val knownIds = existing.mapTo(mutableSetOf()) { it.id }
    return existing + added.filter { knownIds.add(it.id) }
}
