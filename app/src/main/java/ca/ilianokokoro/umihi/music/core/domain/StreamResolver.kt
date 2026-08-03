package ca.ilianokokoro.umihi.music.core.domain

/**
 * Platform-neutral resolution of a playable stream for a [Track].
 *
 * Implementations own provider quirks (signed URLs, expiry, quality selection, local file
 * shortcuts); callers only see a URL they can hand to their platform's player.
 */
interface StreamResolver {
    /**
     * Returns a playable URL for [track]. [allowLocal] lets an implementation short-circuit to an
     * on-device file when the track is already downloaded.
     */
    suspend fun resolve(track: Track, allowLocal: Boolean = false): ResolvedStream
}

data class ResolvedStream(
    val url: String,
)
