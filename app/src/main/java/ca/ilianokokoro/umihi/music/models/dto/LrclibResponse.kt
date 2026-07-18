package ca.ilianokokoro.umihi.music.models.dto

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO for the LRCLIB API responses.
 *
 * Used both by `GET /api/get` (returns a single object) and by
 * `GET /api/search` (returns a JSON array of these objects).
 *
 * See https://lrclib.net/docs
 */
@Immutable
@Serializable
data class LrclibResponse(
    val id: Long? = null,
    @SerialName("trackName")
    val trackName: String? = null,
    @SerialName("artistName")
    val artistName: String? = null,
    @SerialName("albumName")
    val albumName: String? = null,
    val duration: Double? = null,
    val instrumental: Boolean = false,
    @SerialName("plainLyrics")
    val plainLyrics: String? = null,
    @SerialName("syncedLyrics")
    val syncedLyrics: String? = null,
) {
    /** True when the track has neither plain nor synced lyrics available. */
    val hasLyrics: Boolean
        get() = !plainLyrics.isNullOrBlank() || !syncedLyrics.isNullOrBlank()
}
