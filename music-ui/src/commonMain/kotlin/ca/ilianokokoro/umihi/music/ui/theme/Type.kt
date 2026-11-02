package ca.ilianokokoro.umihi.music.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Jelyvo's type scale, over whichever family the client loaded.
 *
 * The family is a parameter rather than a shared resource: Compose Multiplatform's resource
 * generator does not package assets for AGP 9's multiplatform Android library, so each client
 * loads `music-ui/fonts/nunito.ttf` through its own platform's mechanism. The scale — sizes,
 * weights and letter spacing — stays defined here, once.
 */
@Composable
fun appTypography(fontFamily: FontFamily): Typography {
    val defaultTypography = Typography()
    return Typography(
        displayLarge = defaultTypography.displayLarge.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1.5).sp,
        ),
        displayMedium = defaultTypography.displayMedium.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1).sp,
        ),
        displaySmall = defaultTypography.displaySmall.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.75).sp,
        ),

        headlineLarge = defaultTypography.headlineLarge.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.75).sp,
        ),
        headlineMedium = defaultTypography.headlineMedium.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
        ),
        headlineSmall = defaultTypography.headlineSmall.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.25).sp,
        ),

        titleLarge = defaultTypography.titleLarge.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Bold
        ),
        titleMedium = defaultTypography.titleMedium.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold
        ),
        titleSmall = defaultTypography.titleSmall.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold
        ),

        bodyLarge = defaultTypography.bodyLarge.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium
        ),
        bodyMedium = defaultTypography.bodyMedium.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal
        ),
        bodySmall = defaultTypography.bodySmall.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal
        ),

        labelLarge = defaultTypography.labelLarge.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold
        ),
        labelMedium = defaultTypography.labelMedium.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold
        ),
        labelSmall = defaultTypography.labelSmall.copy(
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium
        )
    )
}

/**
 * The Studio theme's scale. Sizes and spacing follow BitChord's (GPL-3.0): small text opens up and
 * large titles close in. Its weights are drawn a step lighter than Jelyvo's, for a calmer page.
 * BitChord leaves the styles it does not use at Material's defaults, but Jelyvo's screens use them,
 * so those are filled in along the same trend.
 */
fun studioTypography(fontFamily: FontFamily): Typography = Typography(
    displayLarge = studioStyle(fontFamily, FontWeight.Bold, 34, 40, -0.5f),
    displayMedium = studioStyle(fontFamily, FontWeight.Bold, 32, 38, -0.45f),
    displaySmall = studioStyle(fontFamily, FontWeight.Bold, 30, 36, -0.4f),
    headlineLarge = studioStyle(fontFamily, FontWeight.Bold, 30, 36, -0.4f),
    headlineMedium = studioStyle(fontFamily, FontWeight.SemiBold, 22, 28, -0.25f),
    headlineSmall = studioStyle(fontFamily, FontWeight.SemiBold, 21, 27, -0.2f),
    titleLarge = studioStyle(fontFamily, FontWeight.SemiBold, 20, 26, -0.2f),
    titleMedium = studioStyle(fontFamily, FontWeight.Medium, 16, 22, 0.05f),
    titleSmall = studioStyle(fontFamily, FontWeight.Medium, 14, 20, 0.1f),
    bodyLarge = studioStyle(fontFamily, FontWeight.Normal, 16, 24, 0.2f),
    bodyMedium = studioStyle(fontFamily, FontWeight.Normal, 14, 20, 0.3f),
    bodySmall = studioStyle(fontFamily, FontWeight.Normal, 12, 16, 0.35f),
    labelLarge = studioStyle(fontFamily, FontWeight.Medium, 14, 20, 0.2f),
    labelMedium = studioStyle(fontFamily, FontWeight.Medium, 12, 16, 0.25f),
    labelSmall = studioStyle(fontFamily, FontWeight.Medium, 11, 16, 0.3f),
)

private fun studioStyle(
    fontFamily: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Float,
) = TextStyle(
    fontFamily = fontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)
