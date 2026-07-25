package ca.ilianokokoro.umihi.music.ui.components.song

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.R

/** Per-bar animation, staggered so the three bars never move in lockstep. */
private val BAR_DURATIONS_MS = listOf(600, 430, 520)
private val BAR_MIN_SCALE = listOf(0.35f, 0.55f, 0.25f)

private val INDICATOR_HEIGHT = 16.dp
private val BAR_WIDTH = 3.dp
private val BAR_SPACING = 2.dp

/**
 * The equalizer bars YouTube Music and Spotify put on the row that is currently playing. Bars
 * animate only while [isPlaying]; paused playback freezes them at full height so the row still
 * reads as "this is the loaded track" without an animation running behind a paused player.
 */
@Composable
fun NowPlayingIndicator(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val description = stringResource(R.string.playing_now)
    val transition = rememberInfiniteTransition(label = "nowPlayingBars")

    Row(
        modifier = modifier
            .height(INDICATOR_HEIGHT)
            .semantics { contentDescription = description },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(BAR_SPACING),
    ) {
        BAR_DURATIONS_MS.forEachIndexed { index, durationMs ->
            val scale by transition.animateFloat(
                initialValue = BAR_MIN_SCALE[index],
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = durationMs),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "nowPlayingBar$index",
            )

            Spacer(
                modifier = Modifier
                    .width(BAR_WIDTH)
                    .fillMaxHeight(if (isPlaying) scale else 1f)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(color),
            )
        }
    }
}
