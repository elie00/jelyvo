@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package ca.ilianokokoro.umihi.music.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import ca.ilianokokoro.umihi.music.core.Constants
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Applies a player-only color scheme extracted from the current artwork. */
@Composable
fun ArtworkPlayerTheme(
    artworkUri: String?,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val baseScheme = MaterialTheme.colorScheme
    val swatches by produceState<ArtworkSwatches?>(null, artworkUri) {
        value = artworkUri
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { uri ->
                runCatching {
                    val request = ImageRequest.Builder(context)
                        .data(uri)
                        .size(256)
                        .build()
                    val result = SingletonImageLoader.get(context).execute(request)
                    if (result !is SuccessResult) return@runCatching null

                    withContext(Dispatchers.Default) {
                        val palette = Palette.from(result.image.toBitmap())
                            .maximumColorCount(16)
                            .generate()
                        palette.toArtworkSwatches()
                    }
                }.getOrNull()
            }
    }

    val accentTarget = swatches?.accent ?: baseScheme.primary
    val mutedTarget = swatches?.muted ?: baseScheme.surfaceContainerLow
    val primary by animateColorAsState(
        targetValue = accentTarget,
        animationSpec = tween(Constants.Player.IMAGE_TRANSITION_DELAY),
        label = "artworkPrimary",
    )
    val surfaceLow by animateColorAsState(
        targetValue = lerp(baseScheme.surfaceContainerLow, mutedTarget, 0.32f),
        animationSpec = tween(Constants.Player.IMAGE_TRANSITION_DELAY),
        label = "artworkSurfaceLow",
    )
    val surfaceHigh by animateColorAsState(
        targetValue = lerp(baseScheme.surfaceContainerHigh, mutedTarget, 0.22f),
        animationSpec = tween(Constants.Player.IMAGE_TRANSITION_DELAY),
        label = "artworkSurfaceHigh",
    )
    val surfaceHighest by animateColorAsState(
        targetValue = lerp(baseScheme.surfaceContainerHighest, mutedTarget, 0.28f),
        animationSpec = tween(Constants.Player.IMAGE_TRANSITION_DELAY),
        label = "artworkSurfaceHighest",
    )

    val primaryContainer = lerp(primary, baseScheme.surface, 0.58f)
    val playerScheme = baseScheme.copy(
        primary = primary,
        onPrimary = primary.contrastingContentColor(),
        primaryContainer = primaryContainer,
        onPrimaryContainer = primaryContainer.contrastingContentColor(),
        secondary = lerp(primary, baseScheme.secondary, 0.45f),
        secondaryContainer = lerp(primary, baseScheme.secondaryContainer, 0.70f),
        surfaceContainerLow = surfaceLow,
        surfaceContainerHigh = surfaceHigh,
        surfaceContainerHighest = surfaceHighest,
        surfaceTint = primary,
    )

    MaterialExpressiveTheme(
        colorScheme = playerScheme,
        typography = MaterialTheme.typography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

private data class ArtworkSwatches(
    val accent: Color,
    val muted: Color,
)

private fun Palette.toArtworkSwatches(): ArtworkSwatches? {
    val accent = vibrantSwatch
        ?: lightVibrantSwatch
        ?: darkVibrantSwatch
        ?: dominantSwatch
        ?: return null
    val muted = mutedSwatch
        ?: darkMutedSwatch
        ?: lightMutedSwatch
        ?: dominantSwatch
        ?: accent
    return ArtworkSwatches(
        accent = Color(accent.rgb),
        muted = Color(muted.rgb),
    )
}

private fun Color.contrastingContentColor(): Color =
    if (luminance() > 0.42f) Color.Black else Color.White
