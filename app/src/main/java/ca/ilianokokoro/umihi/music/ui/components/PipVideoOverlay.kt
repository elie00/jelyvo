package ca.ilianokokoro.umihi.music.ui.components

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.ilianokokoro.umihi.music.core.managers.PlayerManager

/**
 * Video-only layer shown on top of the whole app while the activity is in picture-in-picture
 * mode: the PiP window then contains just the letterboxed video instead of a scaled-down app UI.
 * Attaching this SurfaceView steals the surface from the player screen's; on dispose the clear is
 * a no-op if another surface was set in the meantime, so hand-over in both directions is safe.
 */
@Composable
fun PipVideoOverlay(modifier: Modifier = Modifier) {
    val controller = PlayerManager.controllerState.collectAsStateWithLifecycle().value
    val isCasting by PlayerManager.isCasting.collectAsStateWithLifecycle()
    var surfaceView by remember { mutableStateOf<SurfaceView?>(null) }

    if (isCasting) return

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { context -> SurfaceView(context).also { surfaceView = it } },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )
    }

    DisposableEffect(controller, surfaceView) {
        val view = surfaceView
        if (controller != null && view != null) {
            controller.setVideoSurfaceView(view)
        }
        onDispose {
            if (view != null) {
                controller?.clearVideoSurfaceView(view)
            }
        }
    }
}
