package ca.ilianokokoro.umihi.music.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/**
 * Frosted glass over whatever [state] captures. Below Android 12 there is no blur, so the content
 * would show sharply through the tint and fight with the bar's own text: the fallback is opaque.
 *
 * [state] must capture content that is not an ancestor of this bar, so the bar and the content it
 * blurs have to be siblings.
 *
 * @param visibility fades the glass, for bars that only appear as the page scrolls.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.frosted(state: HazeState, visibility: Float = 1f): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    return hazeEffect(
        state = state,
        style = HazeMaterials.regular(surface).copy(
            fallbackTint = HazeTint(surface),
        ),
    ) {
        alpha = visibility
    }
}

/** Lets a [frosted] top bar show through: the Material bar would otherwise paint over the glass. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun frostedTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
)
