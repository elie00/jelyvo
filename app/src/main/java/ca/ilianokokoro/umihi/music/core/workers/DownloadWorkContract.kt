package ca.ilianokokoro.umihi.music.core.workers

/** Shared, stable WorkManager metadata consumed by the download manager UI. */
object DownloadWorkContract {
    const val TAG_ALL = "jelyvo_download"
    const val TAG_PLAYLIST_PREFIX = "jelyvo_download_playlist:"
    const val TAG_SONG_PREFIX = "jelyvo_download_song:"
    const val TAG_PARENT_PLAYLIST_PREFIX = "jelyvo_download_parent_playlist:"

    const val KEY_KIND = "download_kind"
    const val KEY_TITLE = "download_title"
    const val KEY_COMPLETED = "download_completed"
    const val KEY_TOTAL = "download_total"
    const val KEY_FAILED = "download_failed"

    const val KIND_PLAYLIST = "playlist"
    const val KIND_SONG = "song"

    fun playlistId(tags: Set<String>): String? = tags
        .firstOrNull { it.startsWith(TAG_PLAYLIST_PREFIX) }
        ?.removePrefix(TAG_PLAYLIST_PREFIX)
        ?: tags.firstOrNull { it.startsWith(TAG_PARENT_PLAYLIST_PREFIX) }
            ?.removePrefix(TAG_PARENT_PLAYLIST_PREFIX)

    fun songId(tags: Set<String>): String? = tags
        .firstOrNull { it.startsWith(TAG_SONG_PREFIX) }
        ?.removePrefix(TAG_SONG_PREFIX)
}
