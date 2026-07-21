package ca.ilianokokoro.umihi.music.ui.navigation

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * State of the docked bottom bar: the mini player welded on top of the navigation bar, moving
 * as one unit. Hoisted in [NavigationRoot] and exposed to the tab screens through
 * [LocalBottomDockState].
 *
 * - Auto-hide: [nestedScrollConnection] retracts the dock when a child scrollable scrolls down
 *   and reveals it again on any upward scroll.
 * - Scroll-to-top: re-tapping the active tab calls [requestScrollToTop]; the visible list reacts
 *   through [DockScrollToTopEffect].
 */
@Stable
class BottomDockState {
    /** True while a downward scroll has retracted the dock off-screen. */
    var isDockHidden by mutableStateOf(false)
        private set

    private val scrollToTopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Asks the currently visible tab list to animate back to its top. */
    fun requestScrollToTop() {
        scrollToTopEvents.tryEmit(Unit)
    }

    /** Brings the dock back, e.g. when navigating to another screen. */
    fun showDock() {
        isDockHidden = false
    }

    internal suspend fun collectScrollToTop(onScrollToTop: suspend () -> Unit) {
        scrollToTopEvents.collect { onScrollToTop() }
    }

    /**
     * Applied via `Modifier.nestedScroll` where the screens are composed, so scroll deltas from
     * any child scrollable reach it without per-screen hoisting. Only deltas actually consumed
     * by a child count: a list too short to scroll consumes nothing and never hides the dock.
     */
    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (consumed.y < 0f) {
                isDockHidden = true
            } else if (consumed.y > 0f) {
                isDockHidden = false
            }
            return Offset.Zero
        }
    }
}

/** The dock state of the enclosing [NavigationRoot], or null outside of it (e.g. previews). */
val LocalBottomDockState = staticCompositionLocalOf<BottomDockState?> { null }

/** Animates [listState] back to its top whenever the active tab is re-tapped. */
@Composable
fun DockScrollToTopEffect(listState: LazyListState) {
    val dockState = LocalBottomDockState.current ?: return
    LaunchedEffect(dockState, listState) {
        dockState.collectScrollToTop { listState.animateScrollToItem(0) }
    }
}

/** Animates [gridState] back to its top whenever the active tab is re-tapped. */
@Composable
fun DockScrollToTopEffect(gridState: LazyGridState) {
    val dockState = LocalBottomDockState.current ?: return
    LaunchedEffect(dockState, gridState) {
        dockState.collectScrollToTop { gridState.animateScrollToItem(0) }
    }
}
