package ca.ilianokokoro.umihi.music.core.cast

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.cast.framework.CastContext
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the optional Google Cast runtime.
 *
 * Initialization is intentionally deferred until after the first rendered app frame. Devices
 * without Google Play services keep a null context and otherwise run the exact same playback path.
 */
object CastManager {
    private val _castContext = MutableStateFlow<CastContext?>(null)
    val castContext = _castContext.asStateFlow()

    @Volatile
    private var initializationStarted = false

    fun initializeIfSupported(context: Context, executor: Executor) {
        if (initializationStarted || _castContext.value != null) return

        val applicationContext = context.applicationContext
        if (
            GoogleApiAvailabilityLight.getInstance()
                .isGooglePlayServicesAvailable(applicationContext) != ConnectionResult.SUCCESS
        ) {
            return
        }

        synchronized(this) {
            if (initializationStarted || _castContext.value != null) return
            initializationStarted = true
        }

        try {
            CastContext.getSharedInstance(applicationContext, executor)
                .addOnSuccessListener { _castContext.value = it }
                .addOnFailureListener { initializationStarted = false }
        } catch (_: Exception) {
            // Cast is optional. A broken or absent Google runtime must never block music playback.
            initializationStarted = false
        }
    }
}
