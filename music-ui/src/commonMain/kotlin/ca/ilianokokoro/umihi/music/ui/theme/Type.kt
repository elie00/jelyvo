package ca.ilianokokoro.umihi.music.ui.theme

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
