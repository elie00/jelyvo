package ca.ilianokokoro.umihi.music.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/**
 * Width tiers used by Jelyvo layouts.
 *
 * They deliberately depend on the current window width, not the device type or orientation, so
 * resizing a DeX window or unfolding a device updates the navigation without restarting the app.
 */
enum class WindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

internal fun classifyWindowWidth(widthDp: Int): WindowWidthClass = when {
    widthDp < 600 -> WindowWidthClass.Compact
    widthDp < 840 -> WindowWidthClass.Medium
    else -> WindowWidthClass.Expanded
}

@Composable
fun currentWindowWidthClass(): WindowWidthClass =
    classifyWindowWidth(LocalConfiguration.current.screenWidthDp)
