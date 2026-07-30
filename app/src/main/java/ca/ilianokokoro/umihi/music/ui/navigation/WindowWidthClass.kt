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

/**
 * Permanent secondary panes need both enough width and enough usable height. This keeps a wide
 * phone in landscape on the two-pane player while tablets and desktop-style DeX windows gain the
 * third pane.
 */
internal fun supportsPermanentSecondaryPane(widthDp: Int, heightDp: Int): Boolean =
    classifyWindowWidth(widthDp) == WindowWidthClass.Expanded && heightDp >= 480

/**
 * The app-wide playback pane reserves more horizontal space than the full player's third column.
 * Keep it for desktop-sized windows so 840 dp tablets retain a comfortable content area.
 */
internal fun supportsPermanentPlaybackPane(widthDp: Int, heightDp: Int): Boolean =
    widthDp >= 1000 && heightDp >= 600

@Composable
fun currentWindowWidthClass(): WindowWidthClass =
    classifyWindowWidth(LocalConfiguration.current.screenWidthDp)
