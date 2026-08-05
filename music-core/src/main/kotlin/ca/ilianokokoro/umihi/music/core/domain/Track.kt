package ca.ilianokokoro.umihi.music.core.domain

import kotlinx.serialization.Serializable

/**
 * Platform-neutral musical identity.
 *
 * This model deliberately knows nothing about Room, Media3, Compose, Android URIs or bitmaps. It
 * can therefore move unchanged into a future common Kotlin module. Queue state and screen-only
 * presentation metadata remain outside it.
 */
@Serializable
data class Track(
    val id: String,
    val title: String = "",
    val artist: ArtistRef = ArtistRef(),
    val album: AlbumRef? = null,
    val durationMs: Long? = null,
    val artworkUrl: String? = null,
    val explicit: Boolean = false,
    val liked: Boolean? = null,
    val source: TrackSource = TrackSource.YOUTUBE,
    val sourceUri: String? = null,
    val kind: TrackKind = TrackKind.SONG,
    /**
     * Play the music video itself rather than the official-audio counterpart. Distinct from
     * [kind], which describes what the track *is*: a plain song can still be played as its clip.
     */
    val preferVideoStream: Boolean = false,
    /** 1-based position inside the collection (album, playlist) the track was loaded from. */
    val positionInCollection: Int? = null,
    /** Localized play-count label ("45 M plays") when the source ranks tracks by popularity. */
    val playCountLabel: String? = null,
)

@Serializable
data class ArtistRef(
    val id: String? = null,
    val name: String = "",
)

@Serializable
data class AlbumRef(
    val id: String,
    val name: String = "",
)

@Serializable
enum class TrackSource {
    YOUTUBE,
    LOCAL_DEVICE,
}

@Serializable
enum class TrackKind {
    SONG,
    VIDEO,
}

/**
 * Parses the display formats already used by Jelyvo (`m:ss` or `h:mm:ss`) without Android APIs.
 * Invalid, negative or partially numeric values are rejected rather than silently becoming zero.
 */
fun parseTrackDurationMillis(value: String): Long? {
    if (value.isBlank()) return null
    val parts = value.split(":")
    if (parts.size !in 2..3) return null
    val numbers = parts.map { it.trim().toLongOrNull() ?: return null }
    if (numbers.any { it < 0 }) return null
    if (numbers.drop(1).any { it >= 60 }) return null

    val seconds = numbers.fold(0L) { total, part ->
        if (total > (Long.MAX_VALUE - part) / 60L) return null
        total * 60L + part
    }
    return if (seconds > Long.MAX_VALUE / 1_000L) null else seconds * 1_000L
}

/** Formats a domain duration for the current Android `Song.duration` bridge. */
fun formatTrackDuration(durationMs: Long?): String {
    val totalSeconds = durationMs?.takeIf { it >= 0 }?.div(1_000L) ?: return ""
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

