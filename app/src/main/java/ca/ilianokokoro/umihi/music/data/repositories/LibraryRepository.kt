package ca.ilianokokoro.umihi.music.data.repositories

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.helpers.LogHelper.printe
import ca.ilianokokoro.umihi.music.data.datasources.LibraryDataSource
import ca.ilianokokoro.umihi.music.extensions.toException
import ca.ilianokokoro.umihi.music.models.HomeFeedItem
import ca.ilianokokoro.umihi.music.models.Library
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class LibraryRepository {
    private val libraryDataSource = LibraryDataSource()

    /**
     * Loads the three library sections concurrently. A failed section degrades to an empty list
     * (with a logged cause) so the rest of the page still renders; only a full failure surfaces
     * as an error.
     */
    fun getLibrary(settings: UmihiSettings): Flow<ApiResult<Library>> {
        return flow {
            emit(ApiResult.Loading)

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

            emit(
                ApiResult.Success(
                    Library(
                        likedSongs = likedSongs.getOrDefault(emptyList()),
                        savedAlbums = savedAlbums.getOrDefault(emptyList()),
                        followedArtists = followedArtists.getOrDefault(emptyList()),
                    )
                )
            )
        }.catch { e ->
            emit(ApiResult.Error(e.toException()))
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Recent releases from the account's followed artists: fans out over the first
     * [MAX_NEW_RELEASE_ARTISTS] followed artists, merges their release carousels and keeps the
     * most recent ones. A failing artist page is skipped so the section degrades gracefully.
     */
    fun getNewReleases(settings: UmihiSettings): Flow<ApiResult<List<HomeFeedItem.Album>>> {
        return flow {
            emit(ApiResult.Loading)

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
                                        subtitle = album.subtitle.ifBlank { artist.name },
                                        thumbnail = album.thumbnail,
                                    ) to album.year
                                }
                        }.getOrDefault(emptyList())
                    }
                }.awaitAll()
            }

            val newReleases = albums
                .flatten()
                .distinctBy { (album, _) -> album.browseId }
                .sortedByDescending { (_, year) -> year ?: Int.MIN_VALUE }
                .take(MAX_NEW_RELEASES)
                .map { (album, _) -> album }

            emit(ApiResult.Success(newReleases))
        }.catch { e ->
            emit(ApiResult.Error(e.toException()))
        }.flowOn(Dispatchers.IO)
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
    }
}
