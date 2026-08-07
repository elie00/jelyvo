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
    suspend fun resolve(
        track: Track,
        allowLocal: Boolean = false,
        kind: StreamKind = StreamKind.Combined,
    ): ResolvedStream

    /**
     * Resolves a stream reachable by a remote receiver (e.g. Cast): never a local file, with the
     * provider's longer freshness margin and an explicit content type.
     */
    suspend fun resolveRemote(track: Track): ResolvedRemoteStream
}

/**
 * Which part of a stream is wanted. Providers cap their combined audio+video rendition (YouTube
 * stops at 720p), so anything sharper must be fetched as [VideoOnly] plus [Audio] and merged by
 * the player.
 */
enum class StreamKind { Combined, VideoOnly, Audio }

data class ResolvedStream(
    val url: String,
)

data class ResolvedRemoteStream(
    val url: String,
    val contentType: String,
)
