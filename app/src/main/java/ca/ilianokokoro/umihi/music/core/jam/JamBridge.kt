package ca.ilianokokoro.umihi.music.core.jam

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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


    // What was last agreed: without these, applying a queue would read as someone changing it,
    // and two devices would answer each other for as long as they stayed in the party.
    private var lastLocalIds: List<String> = emptyList()
    private var lastSharedIds: List<String> = emptyList()
    private var publishJob: Job? = null

    fun start() {
        scope.launch {
            JamManager.playback.collect { sync() }
        }
        scope.launch {
            JamManager.queue.collect { queue ->
                if (queue == null) forgetQueue() else applyRemoteQueue(queue)
            }
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
            publishQueue()
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
                val queueIndex = JamQueueSync.nearestIndexOf(
                    current.mediaIds(),
                    action.track.videoId,
                    current.currentMediaItemIndex + 1,
                )
                when {
                    queueIndex >= 0 -> current.seekTo(queueIndex, action.positionMs)

                    current.mediaItemCount == 0 -> {
                        current.setMediaItem(action.track.toSong().mediaItem, action.positionMs)
                        current.prepare()
                    }

                    // Replacing the queue here would wipe it for everyone once it was shared.
                    else -> {
                        val next = current.currentMediaItemIndex + 1
                        current.addMediaItem(next, action.track.toSong().mediaItem)
                        current.seekTo(next, action.positionMs)
                    }
                }
                lastLocalIds = current.mediaIds()
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
        if (!JamManager.canControl) {
            // The server would refuse it, so go straight back to what the party is playing.
            scope.launch { sync() }
            return
        }
        beforeLocalSend(party)
        JamManager.sendTrack(item.toPartyTrack(player().duration), player().playWhenReady)
        topUpQueue()
    }

    fun onLocalPlayWhenReady(play: Boolean) {
        val party = JamManager.playback.value ?: return
        if (isSuspended() || play == party.isPlaying || party.track == null) return
        if (!JamManager.canControl) {
            // The server would refuse it, so go straight back to what the party is playing.
            scope.launch { sync() }
            return
        }
        beforeLocalSend(party)
        val position = player().currentPosition
        if (play) JamManager.sendPlay(position) else JamManager.sendPause(position)
    }

    fun onLocalSeek(positionMs: Long) {
        val party = JamManager.playback.value ?: return
        val serverNow = JamManager.clock.serverNowMs() ?: return
        if (isSuspended() || !JamSync.isLocalSeek(party, serverNow, positionMs)) return
        if (!JamManager.canControl) {
            // The server would refuse it, so go straight back to what the party is playing.
            scope.launch { sync() }
            return
        }
        beforeLocalSend(party)
        JamManager.sendSeek(positionMs)
    }

    /** The player's queue changed. Several edits in a row become one message. */
    fun onLocalQueueChanged() {
        if (JamManager.playback.value == null || isSuspended()) return
        publishJob?.cancel()
        publishJob = scope.launch {
            delay(QUEUE_SETTLE_MS)
            if (!JamManager.canControl) {
                // The server would refuse it, so go straight back to what the party holds.
                JamManager.queue.value?.let { reapply(it) }
                return@launch
            }
            if (player().mediaIds() != lastLocalIds) publishQueue()
        }
    }

    private fun publishQueue() {
        val current = player()
        if (current.mediaItemCount == 0) return
        val tracks = (0 until current.mediaItemCount).map { current.getMediaItemAt(it).toPartyTrack() }
        val window = JamQueueSync.window(tracks, current.currentMediaItemIndex, JamManager::encodedBytes)
        if (window.tracks.isEmpty()) return
        lastLocalIds = current.mediaIds()
        lastSharedIds = window.tracks.map(PartyTrack::videoId)
        JamManager.sendQueue(window)
    }

    /** Shares the next stretch of a queue too long to send at once, before the others run out. */
    private fun topUpQueue() {
        val current = player()
        val upcoming = current.mediaItemCount - 1 - current.currentMediaItemIndex
        if (JamQueueSync.needsTopUp(lastSharedIds, current.currentMediaItem?.mediaId, upcoming)) {
            publishQueue()
        }
    }

    private fun applyRemoteQueue(queue: PartyQueue) {
        // This device's own message coming back: its queue may rightly be longer than what it sent.
        if (isSuspended() || queue.items.map(PartyTrack::videoId) == lastSharedIds) return
        reapply(queue)
    }

    private fun reapply(queue: PartyQueue) {
        val current = player()
        val change = JamQueueSync.plan(
            current.mediaIds(),
            current.currentMediaItem?.mediaId,
            queue,
            lastSharedIds,
        )
        when (change) {
            QueueChange.None -> Unit

            is QueueChange.AroundCurrent -> {
                val index = current.currentMediaItemIndex
                val tailStart = (current.mediaItemCount - change.keptTail).coerceAtLeast(index + 1)
                if (index + 1 < tailStart) {
                    current.removeMediaItems(index + 1, tailStart)
                }
                current.addMediaItems(index + 1, change.after.map { it.toSong().mediaItem })
                if (index > 0) current.removeMediaItems(0, index)
                current.addMediaItems(0, change.before.map { it.toSong().mediaItem })
            }

            is QueueChange.Replace -> {
                current.setMediaItems(change.tracks.map { it.toSong().mediaItem }, change.index, 0L)
                current.prepare()
            }
        }
        lastSharedIds = queue.items.map(PartyTrack::videoId)
        lastLocalIds = current.mediaIds()
    }

    private fun forgetQueue() {
        publishJob?.cancel()
        lastSharedIds = emptyList()
        lastLocalIds = emptyList()
    }

    private fun Player.mediaIds(): List<String> =
        (0 until mediaItemCount).map { getMediaItemAt(it).mediaId }

    private fun MediaItem.toPartyTrack(): PartyTrack =
        toPartyTrack(
            mediaMetadata.durationMs
                ?: JamQueueSync.durationMillis(mediaMetadata.extras?.getString(Constants.ExoPlayer.SongMetadata.DURATION))
                ?: 0L
        )

    private fun PartyTrack.toSong() = Song(
        youtubeId = videoId,
        title = title,
        artist = artist,
        duration = JamQueueSync.durationLabel(durationMs),
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
        const val QUEUE_SETTLE_MS = 500L
    }
}
