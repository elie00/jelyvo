package ca.ilianokokoro.umihi.music.wearapp

/** A media id is also a filename; reject paths and local-phone identifiers at the boundary. */
internal fun isWatchVideoId(value: String): Boolean = Regex("[A-Za-z0-9_-]{11}").matches(value)
