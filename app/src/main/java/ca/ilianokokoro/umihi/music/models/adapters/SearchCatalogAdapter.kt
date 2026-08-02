package ca.ilianokokoro.umihi.music.models.adapters

import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchFilter
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchItem
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchPage
import ca.ilianokokoro.umihi.music.core.domain.CatalogSpellCorrection
import ca.ilianokokoro.umihi.music.models.SearchFilter
import ca.ilianokokoro.umihi.music.models.SearchItem
import ca.ilianokokoro.umihi.music.models.SearchResult
import ca.ilianokokoro.umihi.music.models.SpellCorrection

fun SearchFilter.toCatalogSearchFilter(): CatalogSearchFilter? = when (this) {
    SearchFilter.All -> CatalogSearchFilter.ALL
    SearchFilter.Songs -> CatalogSearchFilter.SONGS
    SearchFilter.Videos -> CatalogSearchFilter.VIDEOS
    SearchFilter.Albums -> CatalogSearchFilter.ALBUMS
    SearchFilter.Artists -> CatalogSearchFilter.ARTISTS
    SearchFilter.Library -> null
}

fun CatalogSearchFilter.toSearchFilter(): SearchFilter = when (this) {
    CatalogSearchFilter.ALL -> SearchFilter.All
    CatalogSearchFilter.SONGS -> SearchFilter.Songs
    CatalogSearchFilter.VIDEOS -> SearchFilter.Videos
    CatalogSearchFilter.ALBUMS -> SearchFilter.Albums
    CatalogSearchFilter.ARTISTS -> SearchFilter.Artists
}

fun SearchResult.toCatalogSearchPage(): CatalogSearchPage = CatalogSearchPage(
    items = items.map(SearchItem::toCatalogSearchItem),
    continuation = continuation,
    topResult = topResult?.toCatalogSearchItem(),
    spellCorrection = spellCorrection?.let {
        CatalogSpellCorrection(
            correctedQuery = it.correctedQuery,
            correctedToken = it.correctedParams,
            originalQuery = it.originalQuery,
            originalToken = it.originalParams,
            autoCorrected = it.autoCorrected,
        )
    },
)

fun CatalogSearchPage.toSearchResult(): SearchResult = SearchResult(
    items = items.map(CatalogSearchItem::toSearchItem),
    continuation = continuation,
    topResult = topResult?.toSearchItem(),
    spellCorrection = spellCorrection?.let {
        SpellCorrection(
            correctedQuery = it.correctedQuery,
            correctedParams = it.correctedToken,
            originalQuery = it.originalQuery,
            originalParams = it.originalToken,
            autoCorrected = it.autoCorrected,
        )
    },
)

private fun SearchItem.toCatalogSearchItem(): CatalogSearchItem = when (this) {
    is SearchItem.Track -> CatalogSearchItem.TrackItem(song.toTrack())
    is SearchItem.Album -> CatalogSearchItem.Album(id, title, subtitle, thumbnail)
    is SearchItem.Artist -> CatalogSearchItem.Artist(id, name, thumbnail)
}

private fun CatalogSearchItem.toSearchItem(): SearchItem = when (this) {
    is CatalogSearchItem.TrackItem -> SearchItem.Track(track.toSong())
    is CatalogSearchItem.Album -> SearchItem.Album(id, title, subtitle, artworkUrl)
    is CatalogSearchItem.Artist -> SearchItem.Artist(id, name, artworkUrl)
}
