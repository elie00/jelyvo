package ca.ilianokokoro.umihi.music.core.cast

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import ca.ilianokokoro.umihi.music.core.youtube.CAST_URL_MIN_LIFETIME_SECONDS
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.core.youtube.isPlaybackUrlFresh
import ca.ilianokokoro.umihi.music.extensions.toSong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Keeps CastPlayer's full queue visible while replacing receiver-inaccessible watch-page URLs with
 * signed CDN streams before each entry is consumed.
 */
@OptIn(UnstableApi::class)
internal class CastResolvingPlayer(
    private val context: Context,
    private val castPlayer: CastPlayer,
    private val scope: CoroutineScope,
) : ForwardingPlayer(castPlayer) {

    private val transitionListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = castPlayer.currentMediaItemIndex
            if (index < 0) return

            if (mediaItem.needsCastResolution()) {
                val resumeAfterResolution = castPlayer.playWhenReady
                castPlayer.pause()
                resolveIndex(
                    index = index,
                    recoverCurrent = true,
                    resumeAfterResolution = resumeAfterResolution,
                )
            }
            resolveWindow(index)
        }
    }

    init {
        castPlayer.addListener(transitionListener)
    }

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        castPlayer.setMediaItems(mediaItems, startIndex, startPositionMs)
        resolveWindow(startIndex)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        castPlayer.setMediaItems(mediaItems)
        resolveWindow(0)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        castPlayer.setMediaItems(mediaItems, resetPosition)
        resolveWindow(if (resetPosition) 0 else castPlayer.currentMediaItemIndex)
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        castPlayer.setMediaItem(mediaItem)
        resolveWindow(0)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        castPlayer.setMediaItem(mediaItem, startPositionMs)
        resolveWindow(0)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        castPlayer.setMediaItem(mediaItem, resetPosition)
        resolveWindow(0)
    }

    override fun addMediaItem(mediaItem: MediaItem) {
        castPlayer.addMediaItem(mediaItem)
        resolveWindow(castPlayer.currentMediaItemIndex)
    }

    override fun addMediaItem(index: Int, mediaItem: MediaItem) {
        castPlayer.addMediaItem(index, mediaItem)
        resolveWindow(castPlayer.currentMediaItemIndex)
    }

    override fun addMediaItems(mediaItems: List<MediaItem>) {
        castPlayer.addMediaItems(mediaItems)
        resolveWindow(castPlayer.currentMediaItemIndex)
    }

    override fun addMediaItems(index: Int, mediaItems: List<MediaItem>) {
        castPlayer.addMediaItems(index, mediaItems)
        resolveWindow(castPlayer.currentMediaItemIndex)
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        castPlayer.replaceMediaItem(index, mediaItem)
        resolveWindow(castPlayer.currentMediaItemIndex)
    }

    override fun replaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: List<MediaItem>,
    ) {
        castPlayer.replaceMediaItems(fromIndex, toIndex, mediaItems)
        resolveWindow(castPlayer.currentMediaItemIndex)
    }

    override fun release() {
        castPlayer.removeListener(transitionListener)
        super.release()
    }

    private fun resolveWindow(currentIndex: Int) {
        resolutionWindow(currentIndex, castPlayer.mediaItemCount).forEach { index ->
            resolveIndex(index)
        }
    }

    private fun resolveIndex(
        index: Int,
        recoverCurrent: Boolean = false,
        resumeAfterResolution: Boolean = false,
    ) {
        if (index !in 0 until castPlayer.mediaItemCount) return
        val unresolved = castPlayer.getMediaItemAt(index)
        if (!unresolved.needsCastResolution()) return
        val song = unresolved.toSong()
        if (!song.isCastable()) return

        scope.launch {
            try {
                val stream = YoutubeDataExtractor.getCastableStream(context, song)
                val resolved = stream.toCastMediaItem(song)
                withContext(Dispatchers.Main.immediate) {
                    if (index !in 0 until castPlayer.mediaItemCount) return@withContext
                    if (castPlayer.getMediaItemAt(index).mediaId != unresolved.mediaId) {
                        return@withContext
                    }
                    castPlayer.replaceMediaItem(index, resolved)
                    if (recoverCurrent && castPlayer.currentMediaItemIndex == index) {
                        castPlayer.seekTo(index, 0L)
                        if (resumeAfterResolution) castPlayer.play()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // PlaybackService reports the resulting Cast load error and can skip the item.
            }
        }
    }

    private fun MediaItem?.needsCastResolution(): Boolean {
        val uri = this?.localConfiguration?.uri?.toString() ?: return false
        return isUnresolvedWatchUrl(uri) ||
            !isPlaybackUrlFresh(
                url = uri,
                minRemainingSeconds = CAST_URL_MIN_LIFETIME_SECONDS,
            )
    }
}
