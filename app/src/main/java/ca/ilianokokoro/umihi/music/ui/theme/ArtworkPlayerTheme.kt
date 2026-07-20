@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package ca.ilianokokoro.umihi.music.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import ca.ilianokokoro.umihi.music.core.Constants
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Applies a complete Material 3 color scheme seeded from the current artwork.
 *
 * The dominant hue and chroma of the artwork feed [SeedSchemes.colorScheme], producing a full
 * tonal scheme whose tone pairs guarantee text contrast in both light and dark mode. When the
 * artwork is missing, too desaturated, or too close to black/white, the surrounding app scheme
 * (the user's selected theme) is kept instead. Scheme changes between tracks are animated.
 */
@Composable
fun ArtworkPlayerTheme(
    artworkUri: String?,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val baseScheme = MaterialTheme.colorScheme
    val darkTheme = isSystemInDarkTheme()
    val seed by produceState<ArtworkSeed?>(null, artworkUri) {
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
                        palette.toArtworkSeed()
                    }
                }.getOrNull()
            }
    }

    val targetScheme = remember(seed, darkTheme, baseScheme) {
        seed
            ?.let { SeedSchemes.colorScheme(hue = it.hue, chroma = it.chroma, darkTheme = darkTheme) }
            ?: baseScheme
    }
    val playerScheme = animateColorSchemeAsState(targetScheme)

    MaterialExpressiveTheme(
        colorScheme = playerScheme,
        typography = MaterialTheme.typography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/**
 * Animates every color role toward [targetScheme] with a single tween, capturing the currently
 * displayed scheme as the new start whenever the target changes mid-flight.
 */
@Composable
private fun animateColorSchemeAsState(targetScheme: ColorScheme): ColorScheme {
    val fraction = remember { Animatable(1f) }
    var startScheme by remember { mutableStateOf(targetScheme) }
    var endScheme by remember { mutableStateOf(targetScheme) }
    if (endScheme !== targetScheme) {
        startScheme = lerpColorScheme(startScheme, endScheme, fraction.value)
        endScheme = targetScheme
    }
    LaunchedEffect(endScheme) {
        fraction.snapTo(0f)
        fraction.animateTo(
            targetValue = 1f,
            animationSpec = tween(Constants.Player.COLOR_TRANSITION_DURATION),
        )
    }
    return lerpColorScheme(startScheme, endScheme, fraction.value)
}

private fun lerpColorScheme(start: ColorScheme, stop: ColorScheme, fraction: Float): ColorScheme {
    if (fraction <= 0f) return start
    if (fraction >= 1f) return stop
    return start.copy(
        primary = lerp(start.primary, stop.primary, fraction),
        onPrimary = lerp(start.onPrimary, stop.onPrimary, fraction),
        primaryContainer = lerp(start.primaryContainer, stop.primaryContainer, fraction),
        onPrimaryContainer = lerp(start.onPrimaryContainer, stop.onPrimaryContainer, fraction),
        inversePrimary = lerp(start.inversePrimary, stop.inversePrimary, fraction),
        secondary = lerp(start.secondary, stop.secondary, fraction),
        onSecondary = lerp(start.onSecondary, stop.onSecondary, fraction),
        secondaryContainer = lerp(start.secondaryContainer, stop.secondaryContainer, fraction),
        onSecondaryContainer = lerp(start.onSecondaryContainer, stop.onSecondaryContainer, fraction),
        tertiary = lerp(start.tertiary, stop.tertiary, fraction),
        onTertiary = lerp(start.onTertiary, stop.onTertiary, fraction),
        tertiaryContainer = lerp(start.tertiaryContainer, stop.tertiaryContainer, fraction),
        onTertiaryContainer = lerp(start.onTertiaryContainer, stop.onTertiaryContainer, fraction),
        background = lerp(start.background, stop.background, fraction),
        onBackground = lerp(start.onBackground, stop.onBackground, fraction),
        surface = lerp(start.surface, stop.surface, fraction),
        onSurface = lerp(start.onSurface, stop.onSurface, fraction),
        surfaceVariant = lerp(start.surfaceVariant, stop.surfaceVariant, fraction),
        onSurfaceVariant = lerp(start.onSurfaceVariant, stop.onSurfaceVariant, fraction),
        surfaceTint = lerp(start.surfaceTint, stop.surfaceTint, fraction),
        inverseSurface = lerp(start.inverseSurface, stop.inverseSurface, fraction),
        inverseOnSurface = lerp(start.inverseOnSurface, stop.inverseOnSurface, fraction),
        error = lerp(start.error, stop.error, fraction),
        onError = lerp(start.onError, stop.onError, fraction),
        errorContainer = lerp(start.errorContainer, stop.errorContainer, fraction),
        onErrorContainer = lerp(start.onErrorContainer, stop.onErrorContainer, fraction),
        outline = lerp(start.outline, stop.outline, fraction),
        outlineVariant = lerp(start.outlineVariant, stop.outlineVariant, fraction),
        scrim = lerp(start.scrim, stop.scrim, fraction),
        surfaceBright = lerp(start.surfaceBright, stop.surfaceBright, fraction),
        surfaceDim = lerp(start.surfaceDim, stop.surfaceDim, fraction),
        surfaceContainer = lerp(start.surfaceContainer, stop.surfaceContainer, fraction),
        surfaceContainerHigh = lerp(start.surfaceContainerHigh, stop.surfaceContainerHigh, fraction),
        surfaceContainerHighest = lerp(
            start.surfaceContainerHighest,
            stop.surfaceContainerHighest,
            fraction
        ),
        surfaceContainerLow = lerp(start.surfaceContainerLow, stop.surfaceContainerLow, fraction),
        surfaceContainerLowest = lerp(
            start.surfaceContainerLowest,
            stop.surfaceContainerLowest,
            fraction
        ),
    )
}

private data class ArtworkSeed(
    val hue: Float,
    val chroma: Float,
)

// Swatches below this saturation, or outside this lightness range, read as grey/black/white and
// produce a muddy scheme, so the app's selected theme is kept instead.
private const val MIN_SEED_SATURATION = 0.18f
private const val MIN_SEED_LIGHTNESS = 0.05f
private const val MAX_SEED_LIGHTNESS = 0.95f

// Chroma range matching the hand-tuned SeedSchemes specs.
private const val MIN_SEED_CHROMA = 0.25f
private const val MAX_SEED_CHROMA = 0.70f

/**
 * Picks the most expressive swatch and converts it to a seed hue/chroma, or null when the artwork
 * is too desaturated or too close to black/white to seed a scheme.
 */
private fun Palette.toArtworkSeed(): ArtworkSeed? {
    val swatch = vibrantSwatch
        ?: lightVibrantSwatch
        ?: darkVibrantSwatch
        ?: dominantSwatch
        ?: return null
    val (hue, saturation, lightness) = swatch.hsl
    if (saturation < MIN_SEED_SATURATION) return null
    if (lightness !in MIN_SEED_LIGHTNESS..MAX_SEED_LIGHTNESS) return null
    return ArtworkSeed(
        hue = (hue % 360f + 360f) % 360f,
        chroma = saturation.coerceIn(MIN_SEED_CHROMA, MAX_SEED_CHROMA),
    )
}
