package ca.ilianokokoro.umihi.music.ui.navigation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import ca.ilianokokoro.umihi.music.ui.components.keyboardFocusOutline

/**
 * Primary navigation for medium and expanded windows.
 *
 * Keeping the same tab model as [BottomNavigationBar] guarantees that resizing a DeX window
 * changes only the navigation chrome, never the selected destination or its back stack.
 */
@Composable
fun PrimaryNavigationRail(
    currentTab: NavKey?,
    onTabSelected: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // The veil of the buttons beside Play for the current tab, where Material would tint a pill.
    val itemColors = NavigationRailItemDefaults.colors(
        indicatorColor = scheme.onSurface.copy(alpha = 0.12f),
        selectedIconColor = scheme.onSurface,
        selectedTextColor = scheme.onSurface,
        unselectedIconColor = scheme.onSurfaceVariant,
        unselectedTextColor = scheme.onSurfaceVariant,
    )
    NavigationRail(modifier = modifier, containerColor = Color.Transparent) {
        Spacer(Modifier.height(12.dp))

        NAV_TABS.forEach { (key, icon, labelRes) ->
            NavigationRailItem(
                modifier = Modifier.keyboardFocusOutline(RoundedCornerShape(24.dp)),
                selected = currentTab == key,
                onClick = { onTabSelected(key) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(labelRes)) },
                alwaysShowLabel = true,
                colors = itemColors,
            )
        }
    }
}
