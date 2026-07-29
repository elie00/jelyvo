package ca.ilianokokoro.umihi.music.ui.navigation

import androidx.compose.ui.input.key.Key
import androidx.navigation3.runtime.NavKey

/**
 * Desktop-style shortcuts shared by physical keyboards on tablets, foldables and DeX.
 *
 * Both Ctrl (Windows/ChromeOS) and Command (future macOS reuse) are accepted by the caller.
 */
internal fun navigationShortcutDestination(
    key: Key,
    hasCommandModifier: Boolean,
): NavKey? {
    if (!hasCommandModifier) return null

    return when (key) {
        Key.One -> HomeScreenKey
        Key.Two -> SearchScreenKey
        Key.Three -> LibraryScreenKey
        Key.Four, Key.Comma -> SettingsScreenKey
        else -> null
    }
}
