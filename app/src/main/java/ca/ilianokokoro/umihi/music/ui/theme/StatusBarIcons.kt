package ca.ilianokokoro.umihi.music.ui.theme

import android.app.Activity
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/** Where black and white text have the same contrast: above it black reads better, below it white. */
private const val EQUAL_CONTRAST_LUMINANCE = 0.179f

/**
 * Whether the status bar's glyphs should be dark. Over a page photograph the photograph decides, as
 * long as it is still under the bar and has been read; otherwise the page does.
 */
internal fun statusBarIconsDark(
    photoTopLuminance: Float?,
    pageLuminance: Float,
    photoUnderBar: Boolean,
): Boolean {
    val under = if (photoUnderBar && photoTopLuminance != null) photoTopLuminance else pageLuminance
    return under > EQUAL_CONTRAST_LUMINANCE
}

/** Who set the icons last, so a page leaving does not undo the page that just replaced it. */
private var iconsOwner: Any? = null

/**
 * Draws the status bar's glyphs dark or light while this is composed, then back to [restoreDark].
 * Only the status bar: the navigation bar sits over the page's foot, not over its photograph.
 */
@Composable
internal fun StatusBarIcons(dark: Boolean, restoreDark: Boolean) {
    val view = LocalView.current
    val window = remember(view) { findWindow(view) } ?: return
    val owner = remember { Any() }
    val restore by rememberUpdatedState(restoreDark)
    SideEffect {
        iconsOwner = owner
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = dark
    }
    DisposableEffect(window, view) {
        onDispose {
            if (iconsOwner === owner) {
                iconsOwner = null
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = restore
            }
        }
    }
}

private fun findWindow(view: View): Window? {
    var parent = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    var context = view.context
    while (context is ContextWrapper) {
        if (context is Activity) return context.window
        context = context.baseContext
    }
    return null
}
