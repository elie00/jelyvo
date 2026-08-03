package ca.ilianokokoro.umihi.music.models.adapters

import ca.ilianokokoro.umihi.music.core.domain.CatalogAlbumPage
import ca.ilianokokoro.umihi.music.core.domain.CatalogArtistPage
import ca.ilianokokoro.umihi.music.core.domain.CatalogPlaylistPage
import ca.ilianokokoro.umihi.music.core.domain.CatalogSearchItem
import ca.ilianokokoro.umihi.music.core.domain.CatalogShelf
import ca.ilianokokoro.umihi.music.core.domain.CatalogShelfToken
import ca.ilianokokoro.umihi.music.core.domain.Track
import ca.ilianokokoro.umihi.music.models.AlbumPage
import ca.ilianokokoro.umihi.music.models.Artist
import ca.ilianokokoro.umihi.music.models.ArtistPage
import ca.ilianokokoro.umihi.music.models.ArtistAlbum
import ca.ilianokokoro.umihi.music.models.ArtistSection
import ca.ilianokokoro.umihi.music.models.Playlist
import ca.ilianokokoro.umihi.music.models.PlaylistInfo
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

fun Playlist.toCatalogPlaylistPage(): CatalogPlaylistPage = CatalogPlaylistPage(
    id = info.id,
    title = info.title,
    artworkUrl = info.coverHref,
    tracks = songs.map(Song::toTrack),
)

/** Local-only fields such as [PlaylistInfo.coverPath] are absent from remote responses. */
fun CatalogPlaylistPage.toPlaylist(): Playlist = Playlist(
    info = PlaylistInfo(id = id, title = title, coverHref = artworkUrl),
    songs = tracks.map(Track::toSong),
)

fun ArtistPage.toCatalogArtistPage(): CatalogArtistPage = CatalogArtistPage(
    name = artist.name,
    artworkUrl = artist.thumbnailHref,
    channelId = artist.channelUrl,
    description = artist.description,
    followerCountLabel = artist.subscriberCount,
    topTracks = topSongs.map(Song::toTrack),
    allTracks = songs.map(Song::toTrack),
    shelves = sections.map(ArtistSection::toCatalogShelf),
)

fun CatalogArtistPage.toArtistPage(): ArtistPage = ArtistPage(
    artist = Artist(
        name = name,
        thumbnailHref = artworkUrl,
        channelUrl = channelId,
        description = description,
        subscriberCount = followerCountLabel,
    ),
    songs = allTracks.map(Track::toSong),
    topSongs = topTracks.map(Track::toSong),
    sections = shelves.map(CatalogShelf::toArtistSection),
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
