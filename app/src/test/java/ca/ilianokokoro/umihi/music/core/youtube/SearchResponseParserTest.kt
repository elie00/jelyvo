package ca.ilianokokoro.umihi.music.core.youtube

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResponseParserTest {

    private val parser = SearchResponseParser(
        songExtractor = { null },
        thumbnailExtractor = { "" },
        viewCountExtractor = { null },
    )

    @Test
    fun `suggestions are deduplicated in response order`() {
        val response = """
            {
              "contents": [
                {"searchSuggestionRenderer":{"suggestion":{"runs":[{"text":"Lefa"}]}}},
                {"searchSuggestionRenderer":{"suggestion":{"runs":[{"text":"Lefa"}]}}},
                {"searchSuggestionRenderer":{"suggestion":{"runs":[{"text":"Lefa albums"}]}}}
              ]
            }
        """.trimIndent()

        assertEquals(listOf("Lefa", "Lefa albums"), parser.extractSuggestions(response))
    }

    @Test
    fun `spell correction keeps endpoint queries and params`() {
        val response = """
            {
              "didYouMeanRenderer": {
                "correctedQueryEndpoint": {
                  "searchEndpoint": {"query":"Lefa","params":"corrected"}
                },
                "originalQueryEndpoint": {
                  "searchEndpoint": {"query":"Leffa","params":"original"}
                }
              }
            }
        """.trimIndent()

        val correction = parser.extractSpellCorrection(response)

        assertEquals("Lefa", correction?.correctedQuery)
        assertEquals("corrected", correction?.correctedParams)
        assertEquals("Leffa", correction?.originalQuery)
        assertEquals("original", correction?.originalParams)
        assertEquals(false, correction?.autoCorrected)
    }

    @Test
    fun `library album tiles are classified without the extractor facade`() {
        val response = """
            {
              "musicTwoRowItemRenderer": {
                "title": {
                  "runs": [{
                    "text": "Démineur",
                    "navigationEndpoint": {"browseEndpoint":{"browseId":"MPRE_album"}}
                  }]
                },
                "subtitle": {"runs":[{"text":"Album • 2025"}]}
              }
            }
        """.trimIndent()

        val albums = parser.extractLibraryAlbums(response)

        assertEquals(1, albums.size)
        assertEquals("MPRE_album", albums.single().id)
        assertEquals("Démineur", albums.single().title)
    }
}
