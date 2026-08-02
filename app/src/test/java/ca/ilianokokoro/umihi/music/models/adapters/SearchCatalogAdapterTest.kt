package ca.ilianokokoro.umihi.music.models.adapters

import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchFilter
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchItem
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.SearchResult
import ca.ilianokokoro.umihi.music.models.Song
import ca.ilianokokoro.umihi.music.models.SpellCorrection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCatalogAdapterTest {
    @Test
    fun `remote search page crosses catalog boundary without losing display data`() {
        val source = SearchResult(
            items = listOf(
                SearchItem.Track(
                    Song(
                        youtubeId = "track-id",
                        title = "Titre",
                        artist = "Artiste",
                        thumbnailHref = "https://example.test/cover.jpg",
                    )
                ),
                SearchItem.Album("album-id", "Album", "2026", "album-cover"),
                SearchItem.Artist("artist-id", "Artiste", "artist-cover"),
            ),
            continuation = "next-page",
            topResult = SearchItem.Artist("top-artist", "Top", "top-cover"),
            spellCorrection = SpellCorrection(
                correctedQuery = "jelyvo",
                correctedParams = "corrected-token",
                originalQuery = "jelivo",
                originalParams = "original-token",
                autoCorrected = true,
            ),
        )

        val catalogPage = source.toCatalogSearchPage()
        val restored = catalogPage.toSearchResult()

        val catalogTrack = catalogPage.items.first() as CatalogSearchItem.TrackItem
        assertEquals("track-id", catalogTrack.track.id)
        assertEquals("Artiste", catalogTrack.track.artist.name)
        assertEquals(source.continuation, restored.continuation)
        assertEquals(source.spellCorrection, restored.spellCorrection)
        assertEquals(source.items.map(SearchItem::id), restored.items.map(SearchItem::id))
        assertTrue(restored.topResult is SearchItem.Artist)
    }

    @Test
    fun `local library filter never crosses remote catalog boundary`() {
        assertNull(SearchFilter.Library.toCatalogSearchFilter())
        assertEquals(CatalogSearchFilter.ALL, SearchFilter.All.toCatalogSearchFilter())
        assertEquals(SearchFilter.Videos, CatalogSearchFilter.VIDEOS.toSearchFilter())
    }
}
