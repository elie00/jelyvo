package ca.ilianokokoro.umihi.music.core.stats

import ca.ilianokokoro.umihi.music.models.ArtistListeningStat
import ca.ilianokokoro.umihi.music.models.TrackListeningStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the pure assembly of the listening-stats export payload: rankings are mapped
 * field-by-field and the file carries its version so future readers can migrate.
 */
class StatsExporterTest {

    private fun track(id: String, plays: Int) = TrackListeningStat(
        youtubeId = id,
        title = "Title $id",
        artist = "Artist $id",
        thumbnailHref = "https://example.com/$id.jpg",
        duration = "3:00",
        isExplicit = false,
        playCount = plays,
        lastPlayedAt = 1_000L,
    )

    @Test
    fun `buildPeriod maps tracks and artists field by field`() {
        val period = StatsExporter.buildPeriod(
            period = "month",
            sinceMillis = 42L,
            totalPlays = 7,
            topTracks = listOf(track("a", 5), track("b", 2)),
            topArtists = listOf(ArtistListeningStat("Artist a", 5, 1_000L)),
        )

        assertEquals("month", period.period)
        assertEquals(42L, period.sinceMillis)
        assertEquals(7, period.totalPlays)
        assertEquals(
            listOf(
                StatsExportTrack("a", "Title a", "Artist a", 5, 1_000L),
                StatsExportTrack("b", "Title b", "Artist b", 2, 1_000L),
            ),
            period.topTracks,
        )
        assertEquals(
            listOf(StatsExportArtist("Artist a", 5, 1_000L)),
            period.topArtists,
        )
    }

    @Test
    fun `buildExport stamps the format version and timestamp`() {
        val export = StatsExporter.buildExport(exportedAt = 123L, periods = emptyList())

        assertEquals(StatsExporter.EXPORT_VERSION, export.version)
        assertEquals(123L, export.exportedAt)
        assertTrue(export.periods.isEmpty())
    }

    @Test
    fun `buildPeriod keeps empty rankings empty`() {
        val period = StatsExporter.buildPeriod(
            period = "all",
            sinceMillis = 0L,
            totalPlays = 0,
            topTracks = emptyList(),
            topArtists = emptyList(),
        )

        assertTrue(period.topTracks.isEmpty())
        assertTrue(period.topArtists.isEmpty())
    }
}
