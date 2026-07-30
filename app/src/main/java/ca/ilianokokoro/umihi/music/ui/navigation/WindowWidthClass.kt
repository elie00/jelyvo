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

/**
 * The library companion sits alongside the navigation rail and the playback pane, so it is
 * reserved for genuinely wide desktop windows. At the threshold, the main content still keeps
 * roughly a compact phone's width when every pane is visible.
 */
internal fun supportsPermanentLibraryPane(widthDp: Int, heightDp: Int): Boolean =
    widthDp >= 1360 && heightDp >= 700

/**
 * Browsing screens use this against their own measured content area, after global navigation and
 * playback panes have taken their space. This avoids a cramped nested split around 1000 dp where
 * the outer window is wide but the remaining album/artist surface is not.
 */
internal fun supportsBrowsingContextPane(
    availableWidthDp: Int,
    availableHeightDp: Int,
): Boolean = availableWidthDp >= 760 && availableHeightDp >= 560

@Composable
fun currentWindowWidthClass(): WindowWidthClass =
    classifyWindowWidth(LocalConfiguration.current.screenWidthDp)
