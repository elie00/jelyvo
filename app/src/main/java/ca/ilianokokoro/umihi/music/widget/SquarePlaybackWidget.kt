package ca.ilianokokoro.umihi.music.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ca.ilianokokoro.umihi.music.MainActivity
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.services.PlaybackService

/**
 * Widget d'écran d'accueil compact au format carré 2x2.
 */
class SquarePlaybackWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = PlaybackWidgetStateStore.read(context)
        provideContent {
            GlanceTheme {
                SquarePlaybackWidgetContent(state)
            }
        }
    }
}

class SquarePlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SquarePlaybackWidget()
}

@Composable
private fun SquarePlaybackWidgetContent(state: PlaybackWidgetState) {
    val context = LocalContext.current
    val openApp = androidx.glance.action.actionStartActivity<MainActivity>()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(18.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(openApp)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Image(
                provider = state.artwork?.let(::ImageProvider) ?: ImageProvider(R.mipmap.icon),
                contentDescription = context.getString(R.string.album_artwork),
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier
                    .size(68.dp)
                    .cornerRadius(R.dimen.widget_artwork_corner_radius),
            )

            Spacer(GlanceModifier.height(4.dp))

            Text(
                text = state.title.ifBlank { context.getString(R.string.widget_nothing_playing) },
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )

            Text(
                text = state.artist.ifBlank { context.getString(R.string.app_name) },
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
            )

            Spacer(GlanceModifier.height(6.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                SquareControl(
                    context = context,
                    action = PlaybackService.ACTION_WIDGET_PREVIOUS,
                    icon = android.R.drawable.ic_media_previous,
                    description = R.string.previous,
                )
                Spacer(GlanceModifier.width(6.dp))
                SquareControl(
                    context = context,
                    action = PlaybackService.ACTION_WIDGET_PLAY_PAUSE,
                    icon = if (state.isPlaying) {
                        android.R.drawable.ic_media_pause
                    } else {
                        android.R.drawable.ic_media_play
                    },
                    description = if (state.isPlaying) R.string.pause else R.string.play,
                    primary = true,
                )
                Spacer(GlanceModifier.width(6.dp))
                SquareControl(
                    context = context,
                    action = PlaybackService.ACTION_WIDGET_NEXT,
                    icon = android.R.drawable.ic_media_next,
                    description = R.string.next,
                )
            }
        }
    }
}

@Composable
private fun SquareControl(
    context: Context,
    action: String,
    icon: Int,
    description: Int,
    primary: Boolean = false,
) {
    val intent = Intent(context, PlaybackService::class.java).setAction(action)
    val tint = if (primary) GlanceTheme.colors.onPrimaryContainer else GlanceTheme.colors.onSurface
    val background = if (primary) GlanceTheme.colors.primaryContainer else GlanceTheme.colors.surfaceVariant

    Box(
        modifier = GlanceModifier
            .size(if (primary) 36.dp else 30.dp)
            .background(background)
            .cornerRadius(if (primary) R.dimen.widget_control_radius_large else R.dimen.widget_control_radius)
            .clickable(actionStartService(intent, isForegroundService = false)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = context.getString(description),
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(if (primary) 20.dp else 16.dp),
        )
    }
}
