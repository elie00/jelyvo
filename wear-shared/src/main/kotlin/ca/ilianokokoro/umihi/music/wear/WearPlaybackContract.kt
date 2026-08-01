package ca.ilianokokoro.umihi.music.wear

/** Stable Data Layer contract shared by the phone and Wear OS applications. */
object WearPlaybackContract {
    const val STATE_PATH = "/jelyvo/playback/state"
    const val COMMAND_PATH = "/jelyvo/playback/command"

    const val COMMAND_PLAY = "play"
    const val COMMAND_PAUSE = "pause"
    const val COMMAND_TOGGLE = "toggle"
    const val COMMAND_PREVIOUS = "previous"
    const val COMMAND_NEXT = "next"
    const val COMMAND_REQUEST_STATE = "request_state"

    const val KEY_MEDIA_ID = "media_id"
    const val KEY_TITLE = "title"
    const val KEY_ARTIST = "artist"
    const val KEY_ARTWORK_URI = "artwork_uri"
    const val KEY_POSITION_MS = "position_ms"
    const val KEY_DURATION_MS = "duration_ms"
    const val KEY_IS_PLAYING = "is_playing"
    const val KEY_HAS_PREVIOUS = "has_previous"
    const val KEY_HAS_NEXT = "has_next"
    const val KEY_UPDATED_AT_MS = "updated_at_ms"
}

data class WearPlaybackSnapshot(
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val artworkUri: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val updatedAtMs: Long = 0L,
) {
    val hasMedia: Boolean get() = mediaId.isNotBlank() || title.isNotBlank()

    fun estimatedPositionMs(nowMs: Long): Long {
        val elapsed = if (isPlaying) (nowMs - updatedAtMs).coerceAtLeast(0L) else 0L
        val upperBound = durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        return (positionMs + elapsed).coerceIn(0L, upperBound)
    }
}
