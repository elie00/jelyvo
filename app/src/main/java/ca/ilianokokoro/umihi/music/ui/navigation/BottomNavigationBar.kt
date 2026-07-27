package ca.ilianokokoro.umihi.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import ca.ilianokokoro.umihi.music.R

@Composable
fun BottomNavigationBar(
    modifier: Modifier = Modifier,
    currentTab: NavKey?,
    onTabSelected: (NavKey) -> Unit
) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        NavigationBarItem(
            selected = currentTab is HomeScreenKey,
            onClick = { onTabSelected(HomeScreenKey) },
            icon = {
                Icon(
                    Icons.Rounded.Home,
                    contentDescription = stringResource(R.string.home)
                )
            },
            alwaysShowLabel = false,
            colors = itemColors,
        )
        NavigationBarItem(
            selected = currentTab is SearchScreenKey,
            onClick = { onTabSelected(SearchScreenKey) },
            icon = {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search)
                )
            },
            alwaysShowLabel = false,
            colors = itemColors,
        )
        NavigationBarItem(
            selected = currentTab is LibraryScreenKey,
            onClick = { onTabSelected(LibraryScreenKey) },
            icon = {
                Icon(
                    Icons.Rounded.LibraryMusic,
                    contentDescription = stringResource(R.string.library)
                )
            },
            alwaysShowLabel = false,
            colors = itemColors,
        )
    }
}
