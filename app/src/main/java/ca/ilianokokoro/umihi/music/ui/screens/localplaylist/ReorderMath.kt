package ca.ilianokokoro.umihi.music.ui.screens.localplaylist

/**
 * Number of non-song items rendered above the song rows in [LocalPlaylistScreen]'s LazyColumn: the
 * cover/description header, the filter/sort row, then the play/shuffle row. The reorder callback reports FLAT LazyColumn
 * indices, so every index it hands back has to be shifted by this before it can address the song
 * list.
 *
 * MUST match the number of `item()` calls preceding `itemsIndexed` in that screen — an `item` whose
 * content composes to nothing still occupies an index. Adding one without bumping this moves the
 * wrong song and throws on the last row; ReorderMathTest is what catches it.
 */
internal const val SONG_LIST_HEADER_ITEMS = 3

/**
 * Moves a song inside [songs] from one flat LazyColumn index to another.
 *
 * Kept as a pure function so the index arithmetic is unit-testable: using the flat index directly
 * moved the wrong song and threw IndexOutOfBoundsException on the last row, because the header
 * offsets every song by [SONG_LIST_HEADER_ITEMS]. Out-of-range indices return the list unchanged
 * rather than throwing — a drag that ends on the header is a no-op, not a crash.
 */
internal fun <T> moveByFlatIndex(
    songs: List<T>,
    fromFlatIndex: Int,
    toFlatIndex: Int,
    headerItems: Int = SONG_LIST_HEADER_ITEMS,
): List<T> {
    val from = fromFlatIndex - headerItems
    val to = toFlatIndex - headerItems

    if (from !in songs.indices || to !in songs.indices || from == to) {
        return songs
    }

    return songs.toMutableList().apply { add(to, removeAt(from)) }
}
