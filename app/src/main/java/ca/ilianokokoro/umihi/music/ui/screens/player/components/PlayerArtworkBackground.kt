package ca.ilianokokoro.umihi.music.ui.screens.player.components

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ca.ilianokokoro.umihi.music.core.Constants
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

// The blurred artwork is oversized so its slow drift never exposes an edge, and blurred hard
// enough that only color fields remain — the scrim on top restores text/control contrast.
private const val ARTWORK_SCALE = 1.6f
private val ARTWORK_BLUR_RADIUS = 60.dp
private const val MAX_DRIFT_OFFSET_FRACTION = 0.06f
private const val MAX_DRIFT_ROTATION_DEGREES = 4f

// The artwork is decoded tiny on purpose: the 60dp blur erases any detail a larger decode would
// add, and blurring a small layer is much cheaper.
private const val ARTWORK_DECODE_SIZE = 128

private const val SCRIM_ALPHA_DARK = 0.60f
private const val SCRIM_ALPHA_LIGHT = 0.68f

/**
 * Full-player background: the current artwork, heavily blurred and slowly drifting behind a
 * surface scrim, Apple Music style. Artwork changes crossfade between the two blurred layers.
 *
 * The artwork layer only exists on API 31+ (where [Modifier.blur] is backed by RenderEffect);
 * older devices — and tracks without artwork — keep the artwork-scheme gradient that is always
 * drawn underneath.
 */
@Composable
fun PlayerArtworkBackground(
    artworkUri: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.background(playerBackgroundBrush())) {
        val uri = artworkUri?.takeIf { it.isNotBlank() && it != "null" }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && uri != null) {
            DriftingBlurredArtwork(
                uri = uri,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        MaterialTheme.colorScheme.surface.copy(
                            alpha = if (isSystemInDarkTheme()) {
                                SCRIM_ALPHA_DARK
                            } else {
                                SCRIM_ALPHA_LIGHT
                            }
                        )
                    )
            )
        }
    }
}

/**
 * The blurred artwork layer itself, scaled to [ARTWORK_SCALE] and drifting on an infinite
 * transition. The three axes (x, y, rotation) run on different periods so the motion never
 * visibly repeats and feels organic rather than mechanical. The drift states are read only
 * inside the [graphicsLayer] lambda, so the animation redraws the layer without recomposing
 * anything.
 */
@Composable
private fun DriftingBlurredArtwork(
    uri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val drift = rememberInfiniteTransition(label = "artworkDrift")
    val driftX = drift.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = Constants.Player.BACKGROUND_DRIFT_PERIOD_MS,
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftX"
    )
    val driftY = drift.animateFloat(
        initialValue = 1f,
        targetValue = -1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = Constants.Player.BACKGROUND_DRIFT_PERIOD_MS * 4 / 3,
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftY"
    )
    val driftRotation = drift.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = Constants.Player.BACKGROUND_DRIFT_PERIOD_MS * 5 / 3,
                easing = EaseInOutSine
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "driftRotation"
    )

    Crossfade(
        targetState = uri,
        animationSpec = tween(Constants.Player.BACKGROUND_CROSSFADE_DURATION),
        label = "artworkCrossfade",
        modifier = modifier
            .graphicsLayer {
                scaleX = ARTWORK_SCALE
                scaleY = ARTWORK_SCALE
                translationX = size.width * MAX_DRIFT_OFFSET_FRACTION * driftX.value
                translationY = size.height * MAX_DRIFT_OFFSET_FRACTION * driftY.value
                rotationZ = MAX_DRIFT_ROTATION_DEGREES * driftRotation.value
            }
            .blur(ARTWORK_BLUR_RADIUS)
    ) { targetUri ->
        val request = remember(targetUri) {
            ImageRequest.Builder(context)
                .data(targetUri)
                .size(ARTWORK_DECODE_SIZE)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Soft vertical gradient from the artwork-derived container tones down to the player surface —
 * the pre-API-31 and no-artwork background, and the base layer under the blurred artwork.
 */
@Composable
private fun playerBackgroundBrush(): Brush {
    val colorScheme = MaterialTheme.colorScheme
    return Brush.verticalGradient(
        0f to lerp(colorScheme.surfaceContainerLow, colorScheme.primaryContainer, 0.35f),
        1f to colorScheme.surfaceContainerLow,
    )
}
