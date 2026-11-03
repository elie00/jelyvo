package ca.ilianokokoro.umihi.music.core.jam

import kotlin.math.abs

/** What this device's queue must do to match the party's. */
sealed interface QueueChange {
    data object None : QueueChange

    /** Keeps what is playing untouched and replaces what surrounds it. */
    data class AroundCurrent(
        val before: List<PartyTrack>,
        val after: List<PartyTrack>,
    ) : QueueChange

    /** The playing track is not in the party's queue, so the whole queue goes. */
    data class Replace(val tracks: List<PartyTrack>, val index: Int) : QueueChange
}

/** The part of a device's queue it shares, and where the playing track is in it. */
data class QueueWindow(val tracks: List<PartyTrack>, val index: Int)

/** The queue rules, free of any player so they can be tested. */
object JamQueueSync {
    /** Enough to go back a little, without paying for a whole history on every change. */
    const val HISTORY_TRACKS = 5

    /** The server holds at most this many tracks after the playing one. */
    const val UPCOMING_TRACKS = 25

    /** The server's limit on one message is 16 KiB. This leaves room for the rest of the frame. */
    const val MAX_TRACK_BYTES = 14_000

    /** Below this, a device holding more of the queue shares the next stretch of it. */
    const val TOP_UP_BELOW_TRACKS = 5

    /**
     * Cuts a device's queue down to what fits in one message. History goes first, because a track
     * still to come matters more than one already heard.
     */
    fun window(
        tracks: List<PartyTrack>,
        currentIndex: Int,
        encodedBytes: (PartyTrack) -> Int,
    ): QueueWindow {
        if (tracks.isEmpty() || currentIndex !in tracks.indices) {
            return QueueWindow(emptyList(), -1)
        }
        var start = (currentIndex - HISTORY_TRACKS).coerceAtLeast(0)
        var end = (currentIndex + 1 + UPCOMING_TRACKS).coerceAtMost(tracks.size)
        var bytes = (start until end).sumOf { encodedBytes(tracks[it]) }
        while (bytes > MAX_TRACK_BYTES && start < currentIndex) {
            bytes -= encodedBytes(tracks[start])
            start++
        }
        while (bytes > MAX_TRACK_BYTES && end > currentIndex + 1) {
            end--
            bytes -= encodedBytes(tracks[end])
        }
        return QueueWindow(tracks.subList(start, end).toList(), currentIndex - start)
    }

    /** How to bring a device's queue to the party's, disturbing what is playing as little as possible. */
    fun plan(
        localIds: List<String>,
        currentId: String?,
        remote: PartyQueue,
    ): QueueChange {
        if (remote.items.isEmpty() || localIds == remote.items.map(PartyTrack::videoId)) {
            return QueueChange.None
        }
        val remoteIds = remote.items.map(PartyTrack::videoId)
        val anchor = currentId?.let { nearestIndexOf(remoteIds, it, remote.index) } ?: -1
        return if (anchor >= 0) {
            QueueChange.AroundCurrent(
                before = remote.items.subList(0, anchor).toList(),
                after = remote.items.subList(anchor + 1, remote.items.size).toList(),
            )
        } else {
            QueueChange.Replace(remote.items, remote.index.coerceIn(0, remote.items.lastIndex))
        }
    }

    /** Finds a track near a position: a queue can hold one track twice, once played and once to come. */
    fun nearestIndexOf(ids: List<String>, id: String, around: Int): Int =
        ids.indices
            .filter { ids[it] == id }
            // The next slot wins a tie, as the queue moving on is the likeliest reason to look.
            .minWithOrNull(compareBy({ abs(it - around) }, { if (it > around) 0 else 1 }))
            ?: -1

    /**
     * Whether this device should share more of its queue. Only one that really holds more does, so
     * two devices at the end of the same queue cannot keep answering each other.
     */
    fun needsTopUp(sharedIds: List<String>, currentId: String?, localUpcoming: Int): Boolean {
        val index = sharedIds.indexOf(currentId)
        if (index < 0) return false
        val sharedUpcoming = sharedIds.size - 1 - index
        return sharedUpcoming < TOP_UP_BELOW_TRACKS && localUpcoming > sharedUpcoming
    }
}
