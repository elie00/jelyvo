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
import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.io.IOException

/**
 * Resolves playable stream URLs through NewPipe, for clients with no Android extractor.
 *
 * [preferredFormat] exists because JVM players do not all decode the same containers: the caller
 * states what its engine can actually play instead of taking whatever ranks highest.
 *
 * [preferMuxedProgressive] trades bandwidth for compatibility. YouTube's audio-only renditions are
 * fragmented MP4, which JavaFX Media misparses — it reports twice the real duration and stops after
 * the first fragment. The muxed progressive rendition (itag 18) is an ordinary MP4 that plays
 * straight from its URL, at the cost of fetching 360p video bytes the audio client discards.
 */
class NewPipeStreamResolver(
    private val preferredFormat: MediaFormat = MediaFormat.M4A,
    private val preferMuxedProgressive: Boolean = false,
) : StreamResolver {

    override suspend fun resolve(
        track: Track,
        allowLocal: Boolean,
        kind: StreamKind,
    ): ResolvedStream = withContext(Dispatchers.IO) {
        if (allowLocal) {
            track.offlineAudioPath?.let { return@withContext ResolvedStream(it) }
        }
        ResolvedStream(bestStreamUrl(track))
    }

    override suspend fun resolveRemote(track: Track): ResolvedRemoteStream =
        withContext(Dispatchers.IO) {
            val stream = bestAudioStream(extractorFor(track))
            ResolvedRemoteStream(
                url = stream.content,
                contentType = stream.format?.mimeType ?: "audio/mp4",
            )
        }

    private fun bestStreamUrl(track: Track): String {
        val extractor = extractorFor(track)
        if (preferMuxedProgressive) {
            muxedProgressiveUrl(extractor)?.let { return it }
        }
        return bestAudioStream(extractor).content
    }

    private fun extractorFor(track: Track): StreamExtractor {
        YoutubeExtractor.ensureInitialized()
        return ServiceList.YouTube.getStreamExtractor(watchUrlOf(track)).also { it.fetchPage() }
    }

    /** The audio+video rendition served as a plain, non-fragmented MP4. */
    private fun muxedProgressiveUrl(extractor: StreamExtractor): String? =
        extractor.videoStreams
            .filter { !it.isVideoOnly && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }
            .minByOrNull { it.itag }
            ?.content

    private fun bestAudioStream(extractor: StreamExtractor): AudioStream {
        // Progressive streams only: DASH and HLS need a player that can drive a manifest, which
        // the JVM engines this resolver serves cannot do.
        val progressive = extractor.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.isUrl }

        if (progressive.isEmpty()) {
            throw IOException("No progressive audio stream available")
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
