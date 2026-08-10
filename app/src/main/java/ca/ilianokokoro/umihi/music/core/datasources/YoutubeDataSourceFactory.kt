package ca.ilianokokoro.umihi.music.core.datasources

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.domain.StreamKind
import ca.ilianokokoro.umihi.music.core.domain.StreamResolver
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiConstants
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeStreamResolver
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.adapters.toTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File

@UnstableApi
class YoutubeDataSourceFactory(
    private val application: Application,
    private val cacheDataSourceFactory: DataSource.Factory,
    private val streamResolver: StreamResolver = YoutubeStreamResolver(application),
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        return ResolvingDataSource(cacheDataSourceFactory.createDataSource()) { dataSpec ->
            dataSpec.withUri(resolveUri(dataSpec.uri))
        }
    }

    private fun resolveUri(uri: Uri): Uri {
        if (!uri.toString()
                .startsWith(YoutubeApiConstants.YOUTUBE_URL_PREFIX) && uri.scheme != null
        ) {
            return uri
        }

        val raw = uri.toString()
        // Each half of a merged HD stream resolves independently through this same path.
        val kind = when {
            raw.contains("${YoutubeApiConstants.VIDEO_ONLY_PARAM}=1") -> StreamKind.VideoOnly
            raw.contains("${YoutubeApiConstants.AUDIO_TRACK_PARAM}=1") -> StreamKind.Audio
            else -> StreamKind.Combined
        }

        val streamUri = runBlocking(Dispatchers.IO) {
            streamResolver.resolve(
                track = Song.createFromYoutubeUrl(raw).toTrack(),
                allowLocal = kind == StreamKind.Combined,
                kind = kind,
            ).url
        }

        return if (streamUri.startsWith("/")) {
            Uri.fromFile(File(streamUri))
        } else {
            streamUri.toUri()
        }
    }
}