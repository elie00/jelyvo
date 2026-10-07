package ca.ilianokokoro.umihi.music.desktop.account

import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiClient
import kotlinx.coroutines.CancellationException
import ca.ilianokokoro.umihi.music.core.youtube.parseYoutubeTrackPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asks YouTube for the account's liked songs and reports what came back.
 *
 * This is the honest test of a pasted cookie header: the endpoint returns the account's own
 * content, so a signed-out or truncated header yields a page with no track rows even though the
 * request itself succeeds. Counting the renderers separates "HTTP worked" from "YouTube knows who
 * you are", which a status code alone cannot.
 */
suspend fun checkAccount(session: DesktopYoutubeSession): String = withContext(Dispatchers.IO) {
    runCatching {
        YoutubeApiClient.browse(browseId = LIKED_SONGS_BROWSE_ID, settings = session)
    }.fold(
        onSuccess = { json ->
            val page = parseYoutubeTrackPage(json)
            val rows = page.tracks.size
            when {
                rows > 0 -> "Connexion vérifiée : $rows titres likés visibles sur le compte."
                page.signedOut ->
                    "YouTube répond mais vous demande de vous connecter : l'en-tête Cookie n'est " +
                        "pas reconnu. Recopiez-le depuis une requête de music.youtube.com."

                else -> "YouTube a répondu, mais aucun titre liké n'a été trouvé. Le compte " +
                    "n'en a peut-être aucun, ou l'en-tête est incomplet."
            }
        },
        onFailure = { if (it is CancellationException) throw it; "Échec de la requête. Réessayez." },
    )
}

/** YouTube Music's own browse id for the liked-songs collection. */
private const val LIKED_SONGS_BROWSE_ID = "FEmusic_liked_videos"
