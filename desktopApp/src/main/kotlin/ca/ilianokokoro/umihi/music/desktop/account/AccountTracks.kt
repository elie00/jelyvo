package ca.ilianokokoro.umihi.music.desktop.account

import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import ca.ilianokokoro.umihi.music.core.youtube.parseYoutubeTrackPage
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** Read-only account library: never merges remote likes into this Mac's local favorites. */
suspend fun likedAccountTracks(session: DesktopYoutubeSession): List<Track> {
    var page = parseYoutubeTrackPage(YoutubeApiClient.browse("FEmusic_liked_videos", session))
    check(!page.signedOut) { "Reconnectez votre compte YouTube." }
    val tracks = page.tracks.toMutableList()
    val seen = mutableSetOf<String>()
    while (page.continuation != null) {
        coroutineContext.ensureActive()
        val token = page.continuation!!
        check(seen.add(token) && seen.size <= 200) { "Pagination de bibliothèque interrompue. Réessayez." }
        page = parseYoutubeTrackPage(YoutubeApiClient.requestContinuation(token, session))
        check(!page.signedOut) { "Reconnectez votre compte YouTube." }
        tracks += page.tracks
    }
    return tracks.distinctBy { it.id }
}
