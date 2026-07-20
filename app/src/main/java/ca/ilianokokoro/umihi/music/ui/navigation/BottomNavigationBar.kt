package ca.ilianokokoro.umihi.music.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavKey
import ca.ilianokokoro.umihi.music.R

@Composable
fun BottomNavigationBar(
    modifier: Modifier = Modifier,
    currentTab: NavKey?,
    onTabSelected: (NavKey) -> Unit
) {
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = currentTab is HomeScreenKey,
            onClick = { onTabSelected(HomeScreenKey) },
            icon = {
                Icon(
                    Icons.Default.Home,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(R.string.home)) }
        )
        NavigationBarItem(
            selected = currentTab is SearchScreenKey,
            onClick = { onTabSelected(SearchScreenKey) },
            icon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(R.string.search)) }
        )
        NavigationBarItem(
            selected = currentTab is LibraryScreenKey,
            onClick = { onTabSelected(LibraryScreenKey) },
            icon = {
                Icon(
                    Icons.Default.LibraryMusic,
                    contentDescription = null
                )
            },
            label = { Text(stringResource(R.string.library)) }
        )
    }
}
