package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.cachedApiFlow
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.data.datasources.LibraryDataSource
import ca.ilianokokoro.umihi.music.models.HomeFeedItem
import ca.ilianokokoro.umihi.music.models.Library
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

class LibraryRepository {
    private val libraryDataSource = LibraryDataSource()

    /**
     * Loads the three library sections concurrently. A failed section degrades to an empty list
     * (with a logged cause) so the rest of the page still renders; only a full failure surfaces
     * as an error.
     */
    fun getLibrary(
        settings: UmihiSettings,
        useCache: Boolean = true,
    ): Flow<ApiResult<Library>> =
        cachedApiFlow(key = cacheKey(settings), useCache = useCache) {
            fetchLibrary(settings)
        }

    private suspend fun fetchLibrary(settings: UmihiSettings): Library {
        val (likedSongs, savedAlbums, followedArtists) = coroutineScope {
            val likedSongs = async {
                sectionResult("liked songs") { libraryDataSource.retrieveLikedSongs(settings) }
            }
            val savedAlbums = async {
                sectionResult("saved albums") { libraryDataSource.retrieveSavedAlbums(settings) }
            }
            val followedArtists = async {
                sectionResult("followed artists") {
                    libraryDataSource.retrieveFollowedArtists(settings)
                }
            }
            Triple(likedSongs.await(), savedAlbums.await(), followedArtists.await())
        }

        val results = listOf(likedSongs, savedAlbums, followedArtists)
        val firstFailure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
        if (firstFailure != null && results.all { it.isFailure }) {
            throw firstFailure
        }

        return Library(
            likedSongs = likedSongs.getOrDefault(emptyList()),
            savedAlbums = savedAlbums.getOrDefault(emptyList()),
            followedArtists = followedArtists.getOrDefault(emptyList()),
        )
    }

    /**
     * Recent releases from the account's followed artists: fans out over the first
     * [MAX_NEW_RELEASE_ARTISTS] followed artists, merges their release carousels and keeps the
     * most recent ones. A failing artist page is skipped so the section degrades gracefully.
     */
    fun getNewReleases(
        settings: UmihiSettings,
        useCache: Boolean = true,
    ): Flow<ApiResult<List<HomeFeedItem.Album>>> =
        cachedApiFlow(key = "new-releases:${settings.dataSyncId.orEmpty()}", useCache = useCache) {
            fetchNewReleases(settings)
        }

    private suspend fun fetchNewReleases(settings: UmihiSettings): List<HomeFeedItem.Album> {
        val artists = libraryDataSource.retrieveFollowedArtists(settings)
            .take(MAX_NEW_RELEASE_ARTISTS)

        val albums = coroutineScope {
            artists.map { artist ->
                async {
                    sectionResult("releases of ${artist.name}") {
                        libraryDataSource.retrieveArtistReleases(artist.id, settings)
                            .map { album ->
                                HomeFeedItem.Album(
                                    browseId = album.browseId,
                                    title = album.title,
                                    // Artist-page cards usually expose only "Single • 2026".
                                    // Prefix the followed artist so the home recommendation keeps
                                    // its most useful context even when several releases sit side by side.
                                    subtitle = newReleaseSubtitle(
                                        artistName = artist.name,
                                        releaseSubtitle = album.subtitle,
                                    ),
                                    thumbnail = album.thumbnail,
                                ) to album.year
                            }
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }

        return albums
            .flatten()
            .distinctBy { (album, _) -> album.browseId }
            .sortedByDescending { (_, year) -> year ?: Int.MIN_VALUE }
            .take(MAX_NEW_RELEASES)
            .map { (album, _) -> album }
    }

    private suspend fun <T> sectionResult(name: String, block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            printe(message = "Failed to load library section: $name", exception = e)
            Result.failure(e)
        }
    }

    companion object {
        private const val MAX_NEW_RELEASE_ARTISTS = 6
        private const val MAX_NEW_RELEASES = 20

        /**
         * ResponseCache key of the library page. Exposed so the local library search can read the
         * cached liked songs without triggering a fetch of its own.
         */
        fun cacheKey(settings: UmihiSettings) = "library:${settings.dataSyncId.orEmpty()}"
    }
}

internal fun newReleaseSubtitle(
    artistName: String,
    releaseSubtitle: String,
): String {
    val parts = releaseSubtitle
        .split("•")
        .map(String::trim)
        .filter(String::isNotBlank)
    val artist = artistName.trim()
    return buildList {
        if (artist.isNotBlank() && parts.none { it.equals(artist, ignoreCase = true) }) {
            add(artist)
        }
        addAll(parts)
    }.joinToString(" • ")
}
