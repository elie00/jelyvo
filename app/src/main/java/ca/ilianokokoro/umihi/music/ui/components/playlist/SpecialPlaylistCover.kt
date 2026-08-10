package ca.ilianokokoro.umihi.music.ui.components.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.WatchLater
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeApiConstants

/**
 * Les playlists automatiques (telechargements, titres likes, episodes de podcasts) n'ont pas de
 * vraie pochette : YouTube sert un visuel generique et les telechargements n'en ont aucun. Chacune
 * recoit a la place une tuile en degrade aux couleurs du theme actif, avec une icone qui dit son
 * contenu. Les couleurs viennent de [MaterialTheme.colorScheme] pour suivre le preset choisi
 * (Jelyvo, Minuit, Vinyle, Neon, Oled) a chaque changement.
 */
enum class SpecialPlaylistCover {
    Downloads,
    Liked,
    NewEpisodes,
    EpisodesForLater,
}

/** Le type de pochette de marque pour cet id de playlist, ou null pour une playlist ordinaire. */
fun specialPlaylistCover(playlistId: String): SpecialPlaylistCover? = when (playlistId) {
    Constants.Downloads.DOWNLOADED_PLAYLIST_ID -> SpecialPlaylistCover.Downloads
    "LM", YoutubeApiConstants.Browse.LIKED_SONGS_BROWSE_ID -> SpecialPlaylistCover.Liked
    YoutubeApiConstants.PODCAST_PLAYLIST_ID -> SpecialPlaylistCover.NewEpisodes
    // « À ecouter plus tard » : id observe cote YT Music pour les episodes sauvegardes.
    "VLLL", "LL" -> SpecialPlaylistCover.EpisodesForLater
    else -> null
}

@Composable
fun JelyvoPlaylistCover(
    cover: SpecialPlaylistCover,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val (gradient, iconTint, icon) = when (cover) {
        SpecialPlaylistCover.Downloads -> Triple(
            listOf(scheme.primaryContainer, scheme.primary.copy(alpha = 0.55f)),
            scheme.onPrimaryContainer,
            Icons.Rounded.Download,
        )

        SpecialPlaylistCover.Liked -> Triple(
            listOf(scheme.primary, scheme.tertiary), scheme.onPrimary, Icons.Rounded.Favorite
        )

        SpecialPlaylistCover.NewEpisodes -> Triple(
            listOf(scheme.tertiaryContainer, scheme.tertiary),
            scheme.onTertiaryContainer,
            Icons.Rounded.Podcasts,
        )

        SpecialPlaylistCover.EpisodesForLater -> Triple(
            listOf(scheme.secondaryContainer, scheme.secondary.copy(alpha = 0.45f)),
            scheme.onSecondaryContainer,
            Icons.Rounded.WatchLater,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium)
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconTint,
            modifier = Modifier.fillMaxSize(0.45f)
        )
    }
}
