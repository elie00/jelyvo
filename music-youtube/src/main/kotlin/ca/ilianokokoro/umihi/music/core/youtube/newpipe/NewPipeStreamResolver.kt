package ca.ilianokokoro.umihi.music.core.youtube.newpipe

import ca.ilianokokoro.umihi.music.core.YoutubeExtractor
import ca.ilianokokoro.umihi.music.core.domain.ResolvedRemoteStream
import ca.ilianokokoro.umihi.music.core.domain.ResolvedStream
import ca.ilianokokoro.umihi.music.core.domain.StreamKind
import ca.ilianokokoro.umihi.music.core.domain.StreamResolver
import ca.ilianokokoro.umihi.music.core.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import java.io.IOException

/**
 * Resolves playable stream URLs through NewPipe, for clients with no Android extractor.
 *
 * [preferredFormat] exists because JVM players do not all decode the same containers: the caller
 * states what its engine can actually play instead of taking whatever ranks highest.
 */
class NewPipeStreamResolver(
    private val preferredFormat: MediaFormat = MediaFormat.M4A,
) : StreamResolver {

    override suspend fun resolve(
        track: Track,
        allowLocal: Boolean,
        kind: StreamKind,
    ): ResolvedStream = withContext(Dispatchers.IO) {
        if (allowLocal) {
            track.offlineAudioPath?.let { return@withContext ResolvedStream(it) }
        }
        ResolvedStream(bestAudioStream(track).content)
    }

    override suspend fun resolveRemote(track: Track): ResolvedRemoteStream =
        withContext(Dispatchers.IO) {
            val stream = bestAudioStream(track)
            ResolvedRemoteStream(
                url = stream.content,
                contentType = stream.format?.mimeType ?: "audio/mp4",
            )
        }

    private fun bestAudioStream(track: Track): AudioStream {
        YoutubeExtractor.ensureInitialized()

        val extractor = ServiceList.YouTube.getStreamExtractor(watchUrlOf(track))
        extractor.fetchPage()

        // Progressive streams only: DASH and HLS need a player that can drive a manifest, which
        // the JVM engines this resolver serves cannot do.
        val progressive = extractor.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }

        if (progressive.isEmpty()) {
            throw IOException("No progressive audio stream for ${track.id}")
        }

        return progressive
            .filter { it.format == preferredFormat }
            .maxByOrNull { it.averageBitrate }
            ?: progressive.maxByOrNull { it.averageBitrate }!!
    }

    private fun watchUrlOf(track: Track): String =
        track.sourceUri?.takeIf { it.startsWith("http") }
            ?: "https://www.youtube.com/watch?v=${track.id}"
}
