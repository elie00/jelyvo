package ca.ilianokokoro.umihi.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ca.ilianokokoro.umihi.music.ui.theme.resources.Res
import ca.ilianokokoro.umihi.music.ui.theme.resources.nunito
import org.jetbrains.compose.resources.Font

/**
 * Nunito, vendored once as a variable font and declared at each weight the design uses.
 *
 * Every entry points at the same file: the weight axis produces the eight cuts that used to be
 * eight separate downloadable-font declarations. A function rather than a value because a
 * multiplatform resource is only reachable from a composition.
 */
@Composable
fun nunitoFontFamily(): FontFamily = FontFamily(
    Font(Res.font.nunito, FontWeight.ExtraLight, FontStyle.Normal),
    Font(Res.font.nunito, FontWeight.Light, FontStyle.Normal),
    Font(Res.font.nunito, FontWeight.Normal, FontStyle.Normal),
    Font(Res.font.nunito, FontWeight.Medium, FontStyle.Normal),
    Font(Res.font.nunito, FontWeight.SemiBold, FontStyle.Normal),
    Font(Res.font.nunito, FontWeight.Bold, FontStyle.Normal),
    Font(Res.font.nunito, FontWeight.ExtraBold, FontStyle.Normal),
    Font(Res.font.nunito, FontWeight.Black, FontStyle.Normal),
)

@Composable
fun appTypography(): Typography {
    val nunito = nunitoFontFamily()
    val defaultTypography = Typography()
    return Typography(
        displayLarge = defaultTypography.displayLarge.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1.5).sp,
        ),
        displayMedium = defaultTypography.displayMedium.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-1).sp,
        ),
        displaySmall = defaultTypography.displaySmall.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.75).sp,
        ),

        headlineLarge = defaultTypography.headlineLarge.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.75).sp,
        ),
        headlineMedium = defaultTypography.headlineMedium.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.5).sp,
        ),
        headlineSmall = defaultTypography.headlineSmall.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.25).sp,
        ),

        titleLarge = defaultTypography.titleLarge.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.Bold
        ),
        titleMedium = defaultTypography.titleMedium.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.SemiBold
        ),
        titleSmall = defaultTypography.titleSmall.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.SemiBold
        ),

        bodyLarge = defaultTypography.bodyLarge.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.Medium
        ),
        bodyMedium = defaultTypography.bodyMedium.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.Normal
        ),
        bodySmall = defaultTypography.bodySmall.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.Normal
        ),

        labelLarge = defaultTypography.labelLarge.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.SemiBold
        ),
        labelMedium = defaultTypography.labelMedium.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.SemiBold
        ),
        labelSmall = defaultTypography.labelSmall.copy(
            fontFamily = nunito,
            fontWeight = FontWeight.Medium
        )
    )
}
