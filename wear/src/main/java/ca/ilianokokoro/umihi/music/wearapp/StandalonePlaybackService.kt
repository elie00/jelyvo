package ca.ilianokokoro.umihi.music.wearapp

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

@UnstableApi
class StandalonePlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val youtubeClient = WatchYoutubeClient()
        val networkClient = OkHttpClient.Builder().build()
        val httpFactory = OkHttpDataSource.Factory(networkClient)
            .setUserAgent("Jelyvo-Watch/1")
        val upstreamFactory = DefaultDataSource.Factory(this, httpFactory)
        val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
            dataSpec.resolveJelyvoUri(youtubeClient)
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(resolvingFactory),
            )
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        mediaSession?.player?.let { player ->
            if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

private fun DataSpec.resolveJelyvoUri(client: WatchYoutubeClient): DataSpec {
    if (uri.scheme != "jelyvo" || uri.host != "youtube") return this
    val videoId = uri.lastPathSegment?.takeIf(String::isNotBlank)
        ?: error("Identifiant du titre manquant")
    val directUrl = runBlocking { client.resolveAudioUrl(videoId) }
    return withUri(Uri.parse(directUrl))
}
