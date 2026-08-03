package ca.ilianokokoro.umihi.music.models.adapters

import ca.ilianokokoro.umihi.music.core.domain.CatalogAlbumPage
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchItem
import ca.ilianokokoro.umihi.music.core.domain.CatalogShelf
import ca.ilianokokoro.umihi.music.core.domain.CatalogShelfToken
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.models.AlbumPage
import ca.ilianokokoro.umihi.music.models.Artist
import ca.ilianokokoro.umihi.music.models.ArtistAlbum
import ca.ilianokokoro.umihi.music.models.ArtistSection
import ca.ilianokokoro.umihi.music.models.Song

fun AlbumPage.toCatalogAlbumPage(): CatalogAlbumPage = CatalogAlbumPage(
    title = name,
    tracks = songs.map(Song::toTrack),
    artworkUrl = cover,
    artistName = artist,
    artistId = artistId,
    subtitle = subtitle,
    secondSubtitle = secondSubtitle,
    description = description,
    shelves = sections.map(ArtistSection::toCatalogShelf),
    audioPlaylistId = playlistId,
    savedToLibrary = isSavedToLibrary,
)

fun CatalogAlbumPage.toAlbumPage(): AlbumPage = AlbumPage(
    name = title,
    songs = tracks.map(Track::toSong),
    cover = artworkUrl,
    artist = artistName,
    artistId = artistId,
    subtitle = subtitle,
    secondSubtitle = secondSubtitle,
    description = description,
    sections = shelves.map(CatalogShelf::toArtistSection),
    playlistId = audioPlaylistId,
    isSavedToLibrary = savedToLibrary,
)

fun ArtistSection.toCatalogShelf(): CatalogShelf = CatalogShelf(
    title = title,
    albums = albums.map {
        CatalogSearchItem.Album(
            id = it.browseId,
            title = it.title,
            subtitle = it.subtitle,
            artworkUrl = it.thumbnail,
        )
    },
    tracks = videos.map(Song::toTrack),
    artists = artists.map {
        CatalogSearchItem.Artist(
            id = it.channelUrl,
            name = it.name,
            artworkUrl = it.thumbnailHref,
        )
    },
    moreToken = moreBrowseId.takeIf(String::isNotBlank)?.let {
        CatalogShelfToken(browseId = it, params = moreParams)
    },
)

fun CatalogShelf.toArtistSection(): ArtistSection = ArtistSection(
    title = title,
    albums = albums.map {
        ArtistAlbum(
            browseId = it.id,
            title = it.title,
            thumbnail = it.artworkUrl,
            subtitle = it.subtitle,
        )
    },
    videos = tracks.map(Track::toSong),
    artists = artists.map {
        Artist(
            name = it.name,
            thumbnailHref = it.artworkUrl,
            channelUrl = it.id,
        )
    },
    moreBrowseId = moreToken?.browseId.orEmpty(),
    moreParams = moreToken?.params.orEmpty(),
)
