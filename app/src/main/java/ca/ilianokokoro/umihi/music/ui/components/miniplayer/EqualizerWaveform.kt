package ca.ilianokokoro.umihi.music.ui.components.miniplayer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Micro-animation d'égaliseur (3 barres vagues audio) jouée lorsque la musique est en cours de lecture.
 */
@Composable
fun EqualizerWaveform(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val barCount = 3
    val animatables = remember { List(barCount) { Animatable(0.3f) } }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            animatables.forEachIndexed { index, animatable ->
                val duration = 400 + index * 120
                delay((index * 100).toLong())
                animatable.animateTo(
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = duration, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
        } else {
            animatables.forEach { it.animateTo(0.2f, tween(300)) }
        }
    }

    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        animatables.forEach { animatable ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((14 * animatable.value).dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}
