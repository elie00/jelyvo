package ca.ilianokokoro.umihi.music.core.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printd
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.core.managers.NotificationManager
import ca.ilianokokoro.umihi.music.data.datasources.LibraryDataSource
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException

/**
 * Periodic background check for new releases from the account's followed artists. For each of the
 * first [MAX_ARTISTS] followed artists it loads the release carousels, compares their browseIds
 * against the persisted "already notified" set (DataStore) and posts a local notification for every
 * previously unseen release.
 *
 * The very first successful run seeds the baseline set silently, so enabling the feature never
 * floods the shade with the whole back-catalogue.
 */
class NewReleasesWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val datastoreRepository = DatastoreRepository(appContext)
    private val libraryDataSource = LibraryDataSource()

    override suspend fun doWork(): Result {
        return try {
            val settings = datastoreRepository.getSettings()

            // Double guard: the work is cancelled when the toggle is turned off, but a run already
            // enqueued could still fire.
            if (!settings.notifyNewReleases) {
                return Result.success()
            }

            // Followed artists are only reachable on an authenticated library browse.
            if (settings.cookies.isEmpty()) {
                return Result.success()
            }

            val artists = libraryDataSource.retrieveFollowedArtists(settings)
                .take(MAX_ARTISTS)
            if (artists.isEmpty()) {
                return Result.success()
            }

            // (release, artistName) across the checked artists; a failing artist page is skipped.
            val releases = coroutineScope {
                artists.map { artist ->
                    async {
                        runCatching {
                            libraryDataSource.retrieveArtistReleases(artist.id, settings)
                                .map { album -> album to artist.name }
                        }.getOrDefault(emptyList())
                    }
                }.awaitAll()
            }.flatten().distinctBy { (album, _) -> album.browseId }

            val currentIds = releases.map { (album, _) -> album.browseId }.toSet()
            val notifiedIds = datastoreRepository.getNotifiedReleaseIds()

            if (notifiedIds.isEmpty()) {
                if (currentIds.isNotEmpty()) {
                    datastoreRepository.setNotifiedReleaseIds(currentIds)
                    printd("New releases baseline seeded with ${currentIds.size} releases")
                }
                return Result.success()
            }

            val newReleases = releases
                .filter { (album, _) -> album.browseId !in notifiedIds }
                .take(MAX_NOTIFICATIONS)

            newReleases.forEach { (album, artistName) ->
                NotificationManager.showNewRelease(appContext, album, artistName)
            }
            if (newReleases.size > 1) {
                NotificationManager.showNewReleasesSummary(appContext, newReleases.size)
            }

            // Union so a release is never notified twice, even if an artist page fetch fails on a
            // later run and its releases temporarily drop out of [currentIds].
            datastoreRepository.setNotifiedReleaseIds(notifiedIds + currentIds)

            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printe(message = "New releases check failed", exception = e)
            Result.retry()
        }
    }

    companion object {
        // Cap the fan-out: each artist is a separate network browse, so checking every followed
        // artist on every run would be expensive on large libraries.
        private const val MAX_ARTISTS = 15

        // Never post more than this many notifications in a single run.
        private const val MAX_NOTIFICATIONS = 10
    }
}
