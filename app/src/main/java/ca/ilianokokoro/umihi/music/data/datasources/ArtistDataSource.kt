package ca.ilianokokoro.umihi.music.data.datasources

import ca.ilianokokoro.umihi.music.core.youtube.YoutubeDataExtractor
import ca.ilianokokoro.umihi.music.models.Artist
import ca.ilianokokoro.umihi.music.models.ArtistPage
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

/**
 * Resolves an artist page (name, avatar and tracks) for a given song via NewPipe. The song's
 * uploader channel is treated as the "artist"; its Videos tab supplies the track list.
 */
class ArtistDataSource {
    suspend fun getArtistForSong(song: Song): ArtistPage = withContext(Dispatchers.IO) {
        val stream = StreamInfo.getInfo(ServiceList.YouTube, song.youtubeUrl)
        val channelUrl = stream.uploaderUrl
            ?: error("No uploader channel for ${song.youtubeId}")
        getArtistPage(channelUrl, fallbackName = stream.uploaderName.orEmpty())
    }

    suspend fun getArtistPage(
        channelUrl: String,
        fallbackName: String = "",
    ): ArtistPage = withContext(Dispatchers.IO) {
        val channelInfo = ChannelInfo.getInfo(ServiceList.YouTube, channelUrl)

        val videosTab = channelInfo.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
            ?: channelInfo.tabs.firstOrNull()

        val items = videosTab
            ?.let { ChannelTabInfo.getInfo(ServiceList.YouTube, it).relatedItems }
            .orEmpty()

        val songs = items
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { it.toSongOrNull() }
            .distinctBy { it.youtubeId }

        val name = channelInfo.name.ifBlank { fallbackName }
        val avatar = channelInfo.avatars.lastOrNull()?.url.orEmpty()

        ArtistPage(
            artist = Artist(name = name, thumbnailHref = avatar, channelUrl = channelUrl),
            songs = songs,
        )
    }

    private fun StreamInfoItem.toSongOrNull(): Song? {
        val videoId = YoutubeDataExtractor.extractYouTubeVideoId(url) ?: return null
        return Song(
            youtubeId = videoId,
            title = name.orEmpty(),
            artist = uploaderName.orEmpty(),
            duration = if (duration > 0) formatDuration(duration) else "",
            thumbnailHref = thumbnails.lastOrNull()?.url.orEmpty(),
        )
    }

    private fun formatDuration(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }
}
