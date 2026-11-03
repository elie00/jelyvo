/*
 * Adapted from BitChord's ArtworkPalette (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package ca.ilianokokoro.umihi.music.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * The colours a page paints itself in, derived from its artwork down to which grey the metadata line
 * is. Every value follows the theme: one sleeve gives a deep tint in dark mode and a pale wash of the
 * same hue in light mode, which is what keeps these pages readable when the two disagree.
 */
@Immutable
data class ArtworkPalette(
    /** The page of a release, whose sleeve sits framed on one flat colour. */
    val background: Color,
    /** The page of an artist, what the photograph's foot dissolves into. Follows the edge of the art. */
    val wash: Color,
    /** Fill for whatever sits on the page. */
    val elevated: Color,
    /** The artwork's own colour, contrast-corrected. */
    val accent: Color,
    val onBackground: Color,
    val onBackgroundVariant: Color,
    val divider: Color,
)

/** What the page is mostly made of, its brightest note, and what its bottom edge averages out to. */
@Immutable
data class PageArtworkSeed(
    val dominant: Color,
    val vibrant: Color,
    val edge: Color,
)

private const val PALETTE_PX = 128
private const val SWATCH_COUNT = 24
private const val SEED_CACHE_ENTRIES = 128
private const val TINT_FADE_MS = 260
private const val EDGE_BAND = 0.18f
/** Below this, boosting saturation would only make quantisation noise visible as a tint. */
private const val CHROMATIC_SATURATION_THRESHOLD = 0.12f
private const val MIN_TEXT_CONTRAST = 4.5
private const val LIGHTNESS_STEP = 0.02f

/** Read once per address: the artwork at a URL is the artwork at that URL. Used from the main thread. */
private val seedCache = object : LinkedHashMap<String, PageArtworkSeed>(0, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<String, PageArtworkSeed>) =
        size > SEED_CACHE_ENTRIES
}

/**
 * Keeps neutral artwork neutral. HSL represents grey with hue zero, so raising a grey to a saturation
 * floor manufactures red, which turns brown or maroon once the page's lightness is lowered.
 */
internal fun adaptedArtworkSaturation(source: Float, minimum: Float, maximum: Float): Float {
    val saturation = source.coerceIn(0f, 1f)
    return if (saturation < CHROMATIC_SATURATION_THRESHOLD) {
        saturation
    } else {
        saturation.coerceIn(minimum, maximum)
    }
}

private fun Color.withHsl(
    saturation: (Float) -> Float = { it },
    lightness: (Float) -> Float = { it },
): Color {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }
    hsl[1] = saturation(hsl[1]).coerceIn(0f, 1f)
    hsl[2] = lightness(hsl[2]).coerceIn(0f, 1f)
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Moves [color] toward white or black only as far as it takes to stay readable on every page colour.
 * A departure from BitChord's fixed lightness, under which a yellow or cyan sleeve in light mode left
 * secondary text as low as 2.8:1.
 */
private fun readableOn(color: Color, pages: List<Color>, dark: Boolean): Color {
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(color.toArgb(), it) }
    val step = if (dark) LIGHTNESS_STEP else -LIGHTNESS_STEP
    var result = color
    while (
        pages.any { ColorUtils.calculateContrast(result.toArgb(), it.toArgb()) < MIN_TEXT_CONTRAST } &&
        hsl[2] in 0f..1f
    ) {
        hsl[2] = (hsl[2] + step).coerceIn(-0.01f, 1.01f)
        result = Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1], hsl[2].coerceIn(0f, 1f))))
    }
    return result
}

internal fun PageArtworkSeed.toPalette(dark: Boolean): ArtworkPalette = basePalette(dark).let {
    it.copy(onBackgroundVariant = readableOn(it.onBackgroundVariant, listOf(it.background, it.wash), dark))
}

private fun PageArtworkSeed.basePalette(dark: Boolean): ArtworkPalette = if (dark) {
    ArtworkPalette(
        // Deep enough that white text clears contrast on any sleeve, not so deep the hue is gone.
        background = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.20f, maximum = 0.62f) },
            lightness = { 0.13f },
        ),
        wash = edge.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.18f, maximum = 0.58f) },
            lightness = { it.coerceIn(0.14f, 0.24f) },
        ),
        elevated = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.20f, maximum = 0.62f) },
            lightness = { 0.22f },
        ),
        accent = vibrant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.55f, maximum = 1f) },
            lightness = { it.coerceIn(0.62f, 0.78f) },
        ),
        onBackground = Color.White,
        // The sleeve's colour lifted, so a blue record's credits read light blue on its blue page.
        onBackgroundVariant = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.22f, maximum = 0.50f) },
            lightness = { 0.80f },
        ),
        divider = Color.White.copy(alpha = 0.12f),
    )
} else {
    ArtworkPalette(
        background = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.14f, maximum = 0.50f) },
            lightness = { 0.91f },
        ),
        wash = edge.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.12f, maximum = 0.46f) },
            lightness = { it.coerceIn(0.78f, 0.90f) },
        ),
        elevated = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.14f, maximum = 0.50f) },
            lightness = { 0.83f },
        ),
        accent = vibrant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.55f, maximum = 1f) },
            lightness = { it.coerceIn(0.30f, 0.44f) },
        ),
        onBackground = Color.Black,
        onBackgroundVariant = dominant.withHsl(
            saturation = { adaptedArtworkSaturation(it, minimum = 0.22f, maximum = 0.55f) },
            lightness = { 0.34f },
        ),
        divider = Color.Black.copy(alpha = 0.10f),
    )
}

internal fun artworkSeedOf(bitmap: Bitmap): PageArtworkSeed? {
    // The default filter throws away near-black and near-white. That helps when looking for an
    // accent, but is the wrong answer to "what is this page mostly made of?": a dark photograph
    // would come down to whatever small coloured detail survived.
    val all = Palette.from(bitmap)
        .maximumColorCount(SWATCH_COUNT)
        .clearFilters()
        .generate()
        .swatches
    if (all.isEmpty()) return null
    val candidates = Palette.from(bitmap)
        .maximumColorCount(SWATCH_COUNT)
        .generate()
        .swatches
        .ifEmpty { all }

    val dominant = all.maxBy { it.population }
    // An accent earns its place twice over: a colour nobody sees reads as arbitrary, and a grey one
    // is no accent. The square root stops a sleeve that is mostly black sky accenting in black.
    val vibrant = candidates.maxBy { swatch ->
        val hsl = FloatArray(3).also { ColorUtils.colorToHSL(swatch.rgb, it) }
        hsl[1] * sqrt(swatch.population.toFloat())
    }
    return PageArtworkSeed(Color(dominant.rgb), Color(vibrant.rgb), bitmap.bottomEdgeColor())
}

/** A flat mean: the page under the artwork has to match what a wide blur actually leaves behind. */
private fun Bitmap.bottomEdgeColor(): Color {
    val band = (height * EDGE_BAND).toInt().coerceIn(1, height)
    val pixels = IntArray(width * band)
    getPixels(pixels, 0, width, 0, height - band, width, band)
    var red = 0L
    var green = 0L
    var blue = 0L
    pixels.forEach { pixel ->
        red += (pixel shr 16) and 0xFF
        green += (pixel shr 8) and 0xFF
        blue += pixel and 0xFF
    }
    val count = pixels.size.coerceAtLeast(1)
    return Color(
        red = (red / count).toInt(),
        green = (green / count).toInt(),
        blue = (blue / count).toInt(),
    )
}

/**
 * The palette of the artwork at [imageUrl]. Artwork already read is tinted on the first frame; a sleeve
 * seen for the first time starts from the theme's colours and warms into its own, so it never flashes
 * a placeholder tint. Missing or unreadable artwork simply keeps the theme.
 */
@Composable
fun rememberArtworkPalette(imageUrl: String?): ArtworkPalette {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    val context = LocalContext.current
    val url = imageUrl?.takeIf { it.isNotBlank() && it != "null" }
    var seed by remember(url) { mutableStateOf(url?.let(seedCache::get)) }
    val knownUpFront = remember(url) { seed != null }

    LaunchedEffect(url) {
        if (url == null || seed != null) return@LaunchedEffect
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(PALETTE_PX)
            // Palette reads pixels, which a hardware bitmap does not allow.
            .allowHardware(false)
            .build()
        val result = SingletonImageLoader.get(context).execute(request) as? SuccessResult
            ?: return@LaunchedEffect
        val found = withContext(Dispatchers.Default) {
            runCatching { artworkSeedOf(result.image.toBitmap()) }.getOrNull()
        } ?: return@LaunchedEffect
        seedCache[url] = found
        seed = found
    }

    val target = seed?.toPalette(dark) ?: ArtworkPalette(
        background = scheme.background,
        wash = scheme.background,
        elevated = scheme.surfaceVariant,
        accent = scheme.primary,
        onBackground = scheme.onBackground,
        onBackgroundVariant = scheme.onSurfaceVariant,
        divider = scheme.outlineVariant,
    )
    val spec: AnimationSpec<Color> = if (knownUpFront) snap() else tween(TINT_FADE_MS)
    return ArtworkPalette(
        background = animateColorAsState(target.background, spec, label = "tintBackground").value,
        wash = animateColorAsState(target.wash, spec, label = "tintWash").value,
        elevated = animateColorAsState(target.elevated, spec, label = "tintElevated").value,
        accent = animateColorAsState(target.accent, spec, label = "tintAccent").value,
        onBackground = animateColorAsState(target.onBackground, spec, label = "tintOn").value,
        onBackgroundVariant = animateColorAsState(
            target.onBackgroundVariant, spec, label = "tintOnVariant",
        ).value,
        divider = animateColorAsState(target.divider, spec, label = "tintDivider").value,
    )
}

/**
 * Paints everything inside in the palette through the theme, so rows, bars, buttons and dialogs follow
 * without each being told. [pageColor] is the palette's background, or its wash on an artist page.
 */
@Composable
fun ArtworkPageTheme(
    palette: ArtworkPalette,
    pageColor: Color = palette.background,
    content: @Composable () -> Unit,
) {
    val base = MaterialTheme.colorScheme
    val scheme = base.copy(
        background = pageColor,
        onBackground = palette.onBackground,
        surface = pageColor,
        onSurface = palette.onBackground,
        surfaceVariant = palette.elevated,
        onSurfaceVariant = palette.onBackgroundVariant,
        surfaceContainerLow = palette.elevated,
        surfaceContainer = palette.elevated,
        surfaceContainerHigh = palette.elevated,
        surfaceContainerHighest = palette.elevated,
        primary = palette.accent,
        outlineVariant = palette.divider,
    )
    MaterialExpressiveTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
