package ca.ilianokokoro.umihi.music.core.stats

import android.content.Context
import android.net.Uri
import ca.ilianokokoro.umihi.music.models.ArtistListeningStat
import ca.ilianokokoro.umihi.music.models.TrackListeningStat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

/** Versioned single-file export of the listening statistics. */
@Serializable
data class StatsExportFile(
    val version: Int,
    val exportedAt: Long,
    val periods: List<StatsExportPeriod> = emptyList(),
)

/** Totals and rankings for one time window ("week", "month", "year", "all"). */
@Serializable
data class StatsExportPeriod(
    val period: String,
    val sinceMillis: Long,
    val totalPlays: Int,
    val topTracks: List<StatsExportTrack> = emptyList(),
    val topArtists: List<StatsExportArtist> = emptyList(),
)

@Serializable
data class StatsExportTrack(
    val youtubeId: String,
    val title: String,
    val artist: String,
    val playCount: Int,
    val lastPlayedAt: Long,
)

@Serializable
data class StatsExportArtist(
    val artist: String,
    val playCount: Int,
    val lastPlayedAt: Long,
)

/**
 * Writes the listening stats to a JSON file picked through the Storage Access Framework
 * (ACTION_CREATE_DOCUMENT), following the same mechanism as [ca.ilianokokoro.umihi.music.core.backup.BackupManager]:
 * no storage permission required, plain versioned JSON.
 */
object StatsExporter {

    const val EXPORT_VERSION = 1
    const val SUGGESTED_FILE_NAME = "jelyvo-stats.json"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    /** Pure assembly of the export payload; kept separate so it stays trivially testable. */
    fun buildExport(
        exportedAt: Long,
        periods: List<StatsExportPeriod>,
    ) = StatsExportFile(
        version = EXPORT_VERSION,
        exportedAt = exportedAt,
        periods = periods,
    )

    fun buildPeriod(
        period: String,
        sinceMillis: Long,
        totalPlays: Int,
        topTracks: List<TrackListeningStat>,
        topArtists: List<ArtistListeningStat>,
    ) = StatsExportPeriod(
        period = period,
        sinceMillis = sinceMillis,
        totalPlays = totalPlays,
        topTracks = topTracks.map { track ->
            StatsExportTrack(
                youtubeId = track.youtubeId,
                title = track.title,
                artist = track.artist,
                playCount = track.playCount,
                lastPlayedAt = track.lastPlayedAt,
            )
        },
        topArtists = topArtists.map { artist ->
            StatsExportArtist(
                artist = artist.artist,
                playCount = artist.playCount,
                lastPlayedAt = artist.lastPlayedAt,
            )
        },
    )

    suspend fun exportToUri(context: Context, uri: Uri, export: StatsExportFile) =
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(json.encodeToString(export).toByteArray())
            } ?: throw IOException("Unable to open output stream")
        }
}
