package ca.ilianokokoro.umihi.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ca.ilianokokoro.umihi.music.R

val nunitoFontFamily = FontFamily(
    Font(R.font.nunito_extralight, FontWeight.ExtraLight, FontStyle.Normal),
    Font(R.font.nunito_light, FontWeight.Light, FontStyle.Normal),
    Font(R.font.nunito, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.nunito_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.nunito_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold, FontStyle.Normal),
    Font(R.font.nunito_black, FontWeight.Black, FontStyle.Normal),
)
private val defaultTypography = Typography()
val AppTypography = Typography(
    displayLarge = defaultTypography.displayLarge.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1.5).sp,
    ),
    displayMedium = defaultTypography.displayMedium.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1).sp,
    ),
    displaySmall = defaultTypography.displaySmall.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.75).sp,
    ),

    headlineLarge = defaultTypography.headlineLarge.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.75).sp,
    ),
    headlineMedium = defaultTypography.headlineMedium.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = defaultTypography.headlineSmall.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.25).sp,
    ),

    titleLarge = defaultTypography.titleLarge.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.Bold
    ),
    titleMedium = defaultTypography.titleMedium.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = defaultTypography.titleSmall.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.SemiBold
    ),

    bodyLarge = defaultTypography.bodyLarge.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.Medium
    ),
    bodyMedium = defaultTypography.bodyMedium.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = defaultTypography.bodySmall.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.Normal
    ),

    labelLarge = defaultTypography.labelLarge.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.SemiBold
    ),
    labelMedium = defaultTypography.labelMedium.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.SemiBold
    ),
    labelSmall = defaultTypography.labelSmall.copy(
        fontFamily = nunitoFontFamily,
        fontWeight = FontWeight.Medium
    )
)
