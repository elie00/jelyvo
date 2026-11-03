package ca.ilianokokoro.umihi.music.wearapp

import android.content.Context
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

@androidx.annotation.OptIn(UnstableApi::class)
class StandalonePlaybackService : MediaSessionService() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(WatchLocale.wrap(newBase))
    }

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val youtubeClient = WatchYoutubeClient()
        val networkClient = OkHttpClient.Builder().build()
        val httpFactory = OkHttpDataSource.Factory(networkClient)
            .setUserAgent("Jelyvo-Watch/1")
        val upstreamFactory = DefaultDataSource.Factory(this, httpFactory)
        val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
            dataSpec.resolveJelyvoUri(youtubeClient, this)
        }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(resolvingFactory),
            )
            .setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .setSuppressPlaybackOnUnsuitableOutput(true)
            .build()
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        if (controllerInfo.packageName == packageName || controllerInfo.isTrusted) mediaSession else null

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

@androidx.annotation.OptIn(UnstableApi::class)
private fun DataSpec.resolveJelyvoUri(client: WatchYoutubeClient, context: android.content.Context): DataSpec {
    if (uri.scheme != "jelyvo" || uri.host != "youtube") return this
    val videoId = uri.lastPathSegment?.takeIf(String::isNotBlank)
        ?: error("Identifiant du titre manquant")
    val localFile = WatchLibraryStore(context).audioFile(videoId)
    if (localFile.isFile && localFile.length() > 0) return withUri(Uri.fromFile(localFile))
    val directUrl = runBlocking { client.resolveAudioUrl(videoId) }
    return withUri(Uri.parse(directUrl))
}
