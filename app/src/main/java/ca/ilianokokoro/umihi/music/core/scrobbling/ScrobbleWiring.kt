package ca.ilianokokoro.umihi.music.core.scrobbling

import androidx.media3.common.MediaItem
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import kotlinx.coroutines.flow.first

/** Keeps each service's pending listens under its own, backup-excluded preference key. */
class DatastorePendingScrobbleStore(
    private val datastore: DatastoreRepository,
) : PendingScrobbleStore {
    private val keys = mapOf(
        LISTENBRAINZ_SINK_ID to Constants.Datastore.SCROBBLE_PENDING_LISTENBRAINZ,
        LASTFM_SINK_ID to Constants.Datastore.SCROBBLE_PENDING_LASTFM,
    )

    override suspend fun read(sinkId: String): List<ScrobbleTrack> =
        datastore.readPendingScrobbles(keys.getValue(sinkId))

    override suspend fun update(
        sinkId: String,
        transform: (List<ScrobbleTrack>) -> List<ScrobbleTrack>,
    ) = datastore.updatePendingScrobbles(keys.getValue(sinkId), transform)
}

private const val LISTENBRAINZ_SINK_ID = "listenbrainz"
private const val LASTFM_SINK_ID = "lastfm"

class LastFmSink(private val datastore: DatastoreRepository) : ScrobbleSink {
    override val id = LASTFM_SINK_ID

    private suspend fun credentials() = datastore.lastFmCredentials.first()

    override suspend fun isEnabled(): Boolean = credentials() != null

    override suspend fun nowPlaying(track: ScrobbleTrack): SubmitResult =
        credentials()?.let { LastFmClient.nowPlaying(it, track) } ?: SubmitResult.Unauthorized

    override suspend fun submit(tracks: List<ScrobbleTrack>): SubmitResult =
        credentials()?.let { LastFmClient.scrobble(it, tracks) } ?: SubmitResult.Unauthorized
}

class ListenBrainzSink(private val datastore: DatastoreRepository) : ScrobbleSink {
    override val id = LISTENBRAINZ_SINK_ID

    private suspend fun token() = datastore.listenBrainzToken.first()

    override suspend fun isEnabled(): Boolean = token().isNotBlank()

    override suspend fun nowPlaying(track: ScrobbleTrack): SubmitResult =
        ListenBrainzClient.nowPlaying(token(), track)

    override suspend fun submit(tracks: List<ScrobbleTrack>): SubmitResult =
        ListenBrainzClient.submitListens(token(), tracks)
}

/** What a queue item is worth to a scrobbling service, or null if it has no usable metadata. */
fun MediaItem.toScrobbleTrack(startedAtMs: Long): ScrobbleTrack? {
    val artist = mediaMetadata.artist?.toString()?.trim().orEmpty()
    val title = mediaMetadata.title?.toString()?.trim().orEmpty()
    if (artist.isEmpty() || title.isEmpty()) return null
    return ScrobbleTrack(
        artist = artist,
        title = title,
        album = mediaMetadata.albumTitle?.toString(),
        startedAtMs = startedAtMs,
    )
}
