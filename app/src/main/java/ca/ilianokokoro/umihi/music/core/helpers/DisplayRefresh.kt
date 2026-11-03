package ca.ilianokokoro.umihi.music.core.helpers

import android.app.Activity
import android.view.Display

/** One way the screen can run, free of the platform class so the choice can be tested. */
data class DisplayModeChoice(val id: Int, val width: Int, val height: Int, val refreshRate: Float)

object DisplayRefresh {
    /** 0 is how a window says it has no preference, and lets the system decide. */
    const val NO_PREFERENCE = 0

    /** The fastest mode at the current resolution: a faster mode at another one would blur the screen. */
    fun fastestMode(modes: List<DisplayModeChoice>, current: DisplayModeChoice): Int =
        modes
            .filter { it.width == current.width && it.height == current.height }
            .maxByOrNull(DisplayModeChoice::refreshRate)
            ?.id
            ?: NO_PREFERENCE

    /** Asks for the fastest refresh rate, or hands the choice back to the system. */
    @Suppress("DEPRECATION") // Activity.getDisplay() is API 30, and the app runs from API 24.
    fun apply(activity: Activity, preferFastest: Boolean) {
        val display = activity.windowManager.defaultDisplay
        val wanted = if (preferFastest) {
            fastestMode(display.supportedModes.map(::choiceOf), choiceOf(display.mode))
        } else {
            NO_PREFERENCE
        }
        val window = activity.window
        if (window.attributes.preferredDisplayModeId != wanted) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = wanted }
        }
    }

    private fun choiceOf(mode: Display.Mode) = DisplayModeChoice(
        id = mode.modeId,
        width = mode.physicalWidth,
        height = mode.physicalHeight,
        refreshRate = mode.refreshRate,
    )
}
