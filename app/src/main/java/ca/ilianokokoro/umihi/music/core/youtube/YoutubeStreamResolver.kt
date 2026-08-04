package ca.ilianokokoro.umihi.music.core.youtube

import android.content.Context
import ca.ilianokokoro.umihi.music.core.domain.ResolvedRemoteStream
import ca.ilianokokoro.umihi.music.core.domain.ResolvedStream
import ca.ilianokokoro.umihi.music.core.domain.StreamResolver
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.models.adapters.toSong

/** Android/InnerTube adapter for the platform-neutral [StreamResolver]. */
class YoutubeStreamResolver(context: Context) : StreamResolver {
    private val applicationContext = context.applicationContext

    override suspend fun resolve(track: Track, allowLocal: Boolean): ResolvedStream =
        ResolvedStream(
            url = YoutubeDataExtractor.getSongPlayerUrl(
                context = applicationContext,
                song = track.toSong(),
                allowLocal = allowLocal,
            ),
        )

    override suspend fun resolveRemote(track: Track): ResolvedRemoteStream {
        val stream = YoutubeDataExtractor.getCastableStream(applicationContext, track.toSong())
        return ResolvedRemoteStream(url = stream.url, contentType = stream.contentType)
    }
}
