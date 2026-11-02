package ca.ilianokokoro.umihi.music.core.jam

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the player and the party in step, in both directions. Everything runs on the main thread.
 *
 * Remote changes reach the player through the same callbacks as a person's, so local events are
 * only sent when they disagree with the party: applying a remote state therefore never echoes
 * back. A local change is not undone while the server's answer is on its way, for [ECHO_WAIT_MS].
 */
class JamBridge(
    private val player: () -> Player,
    private val scope: CoroutineScope,
    private val isSuspended: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var ignoreRemoteUntilMs = 0L
    private var ignoreRemoteUntilSeq = Long.MIN_VALUE

    fun start() {
        scope.launch {
            JamManager.playback.collect { sync() }
        }
        // Applying only on change would never correct slow drift between two equal states.
        scope.launch {
            while (true) {
                delay(DRIFT_CHECK_MS)
                sync()
            }
        }
    }

    private fun sync() {
        if (isSuspended()) return
        val party = JamManager.playback.value ?: return
        val serverNow = JamManager.clock.serverNowMs() ?: return
        if (party.seq <= ignoreRemoteUntilSeq && now() < ignoreRemoteUntilMs) return

        val current = player()
        val item = current.currentMediaItem
        if (item != null && JamSync.shouldPublishCurrentTrack(party, JamManager.isHost, item.mediaId)) {
            beforeLocalSend(party)
            JamManager.sendTrack(item.toPartyTrack(current.duration), current.playWhenReady, current.currentPosition)
            return
        }
        val local = LocalPlayback(
            mediaId = current.currentMediaItem?.mediaId,
            positionMs = current.currentPosition,
            // Buffering still counts as playing, or a slow network would read as a pause.
            isPlaying = current.playWhenReady,
        )
        JamSync.plan(party, serverNow, local).forEach { action -> apply(current, action) }
    }

    private fun apply(current: Player, action: JamAction) {
        when (action) {
            is JamAction.ChangeTrack -> {
                val queueIndex = (0 until current.mediaItemCount).firstOrNull { index ->
                    current.getMediaItemAt(index).mediaId == action.track.videoId
                }
                if (queueIndex != null) {
                    current.seekTo(queueIndex, action.positionMs)
                } else {
                    current.setMediaItem(action.track.toSong().mediaItem, action.positionMs)
                    current.prepare()
                }
                current.playWhenReady = action.play
            }

            is JamAction.Seek -> current.seekTo(action.positionMs)

            is JamAction.SetPlaying -> current.playWhenReady = action.play
        }
    }

    private fun beforeLocalSend(party: PartyPlayback) {
        ignoreRemoteUntilSeq = party.seq
        ignoreRemoteUntilMs = now() + ECHO_WAIT_MS
    }

    fun onLocalTrack(item: MediaItem?) {
        val party = JamManager.playback.value ?: return
        if (isSuspended() || item == null || item.mediaId == party.track?.videoId) return
        beforeLocalSend(party)
        JamManager.sendTrack(item.toPartyTrack(player().duration), player().playWhenReady)
    }

    fun onLocalPlayWhenReady(play: Boolean) {
        val party = JamManager.playback.value ?: return
        if (isSuspended() || play == party.isPlaying || party.track == null) return
        beforeLocalSend(party)
        val position = player().currentPosition
        if (play) JamManager.sendPlay(position) else JamManager.sendPause(position)
    }

    fun onLocalSeek(positionMs: Long) {
        val party = JamManager.playback.value ?: return
        val serverNow = JamManager.clock.serverNowMs() ?: return
        if (isSuspended() || !JamSync.isLocalSeek(party, serverNow, positionMs)) return
        beforeLocalSend(party)
        JamManager.sendSeek(positionMs)
    }

    private fun PartyTrack.toSong() = Song(
        youtubeId = videoId,
        title = title,
        artist = artist,
        thumbnailHref = thumbnailUrl.orEmpty(),
    )

    private fun MediaItem.toPartyTrack(durationMs: Long) = PartyTrack(
        videoId = mediaId,
        title = mediaMetadata.title?.toString().orEmpty(),
        artist = mediaMetadata.artist?.toString().orEmpty(),
        thumbnailUrl = mediaMetadata.artworkUri?.toString()?.takeIf { it.startsWith("https://") },
        durationMs = durationMs.takeIf { it > 0L },
    )

    private companion object {
        const val ECHO_WAIT_MS = 2_000L
        const val DRIFT_CHECK_MS = 3_000L
    }
}
