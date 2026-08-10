package ca.ilianokokoro.umihi.music.core.datasources

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.core.net.toUri
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiConstants

/**
 * Plays video above 720p by merging two streams.
 *
 * YouTube's progressive ladder — one URL carrying audio and video together — stops at 720p.
 * Sharper renditions exist only as video-only tracks that must be paired with a separate audio
 * one. This factory therefore builds two sources for such items and hands them to a
 * [MergingMediaSource], which keeps them in sync; everything else goes to [delegate] untouched.
 *
 * The two halves are told apart by a marker appended to their URI, so each resolves through the
 * ordinary resolving data source rather than needing its own plumbing.
 */
@UnstableApi
class HdVideoMediaSourceFactory(
    private val delegate: MediaSource.Factory,
    private val wantsMergedVideo: () -> Boolean,
) : MediaSource.Factory {

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun setDrmSessionManagerProvider(
        provider: DrmSessionManagerProvider,
    ): MediaSource.Factory = apply { delegate.setDrmSessionManagerProvider(provider) }

    override fun setLoadErrorHandlingPolicy(
        policy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory = apply { delegate.setLoadErrorHandlingPolicy(policy) }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri?.toString()
            ?: return delegate.createMediaSource(mediaItem)

        val isVideoRequest = uri.contains("${YoutubeApiConstants.FORCE_VIDEO_PARAM}=1")
        if (!isVideoRequest || !wantsMergedVideo()) {
            return delegate.createMediaSource(mediaItem)
        }

        val video = delegate.createMediaSource(
            mediaItem.markedWith(YoutubeApiConstants.VIDEO_ONLY_PARAM)
        )
        val audio = delegate.createMediaSource(
            mediaItem.markedWith(YoutubeApiConstants.AUDIO_TRACK_PARAM)
        )
        return MergingMediaSource(video, audio)
    }

    /**
     * Keeps the original media id so the queue, notification and session still identify the item;
     * only the URI differs, which is what selects the track.
     */
    private fun MediaItem.markedWith(param: String): MediaItem {
        val base = localConfiguration?.uri?.toString().orEmpty()
        return buildUpon()
            .setUri("$base&$param=1".toUri())
            .build()
    }
}
