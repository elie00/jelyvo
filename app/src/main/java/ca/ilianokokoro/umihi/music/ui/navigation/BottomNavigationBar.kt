package ca.ilianokokoro.umihi.music.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import ca.ilianokokoro.umihi.music.R

/** Les trois onglets racine, dans l'ordre visuel — l'ordre sert aussi au swipe. */
private val NAV_TABS: List<Triple<NavKey, ImageVector, Int>> = listOf(
    Triple(HomeScreenKey, Icons.Rounded.Home, R.string.home),
    Triple(SearchScreenKey, Icons.Rounded.Search, R.string.search),
    Triple(LibraryScreenKey, Icons.Rounded.LibraryMusic, R.string.library),
)

/** Deplacement horizontal minimal (dp) pour qu'un glissement sur la barre change d'onglet. */
private const val SWIPE_THRESHOLD_DP = 48

/**
 * Barre de navigation compacte (56 dp au lieu des 80 dp de la NavigationBar M3) : les libelles
 * ont ete retires, la hauteur standard ne payait plus que du vide. Un glissement horizontal
 * sur la barre passe a l'onglet voisin — le mini-player garde son propre swipe (piste
 * precedente/suivante), les deux zones ne se recouvrent pas.
 */
@Composable
fun BottomNavigationBar(
    modifier: Modifier = Modifier,
    currentTab: NavKey?,
    onTabSelected: (NavKey) -> Unit
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(NavigationBarDefaults.windowInsets)
                .height(56.dp)
                .pointerInput(currentTab) {
                    var dragTotal = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount },
                        onDragEnd = {
                            val threshold = SWIPE_THRESHOLD_DP.dp.toPx()
                            val currentIndex = NAV_TABS.indexOfFirst { it.first == currentTab }
                            if (currentIndex == -1) return@detectHorizontalDragGestures
                            // Glisser vers la gauche revele l'onglet de droite, et inversement.
                            val targetIndex = when {
                                dragTotal <= -threshold -> currentIndex + 1
                                dragTotal >= threshold -> currentIndex - 1
                                else -> currentIndex
                            }
                            if (targetIndex != currentIndex && targetIndex in NAV_TABS.indices) {
                                onTabSelected(NAV_TABS[targetIndex].first)
                            }
                        },
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NAV_TABS.forEach { (key, icon, labelRes) ->
                val selected = currentTab == key
                val indicatorColor by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        Color.Transparent
                    },
                    label = "navIndicatorColor"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onTabSelected(key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(indicatorColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = stringResource(labelRes),
                            tint = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}
