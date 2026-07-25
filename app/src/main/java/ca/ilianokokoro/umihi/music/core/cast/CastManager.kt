package ca.ilianokokoro.umihi.music.core.cast

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.SessionManagerListener
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    private val _sessionPlayer = MutableStateFlow<Player?>(null)
    val sessionPlayer = _sessionPlayer.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName = _deviceName.asStateFlow()

    private val _isCasting = MutableStateFlow(false)
    val isCasting = _isCasting.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val castScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var initializationStarted = false
    private var applicationContext: Context? = null

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) {
            activateSession(session)
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            activateSession(session)
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            deactivateSession()
        }

        override fun onSessionEnding(session: CastSession) {
            deactivateSession()
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            deactivateSession()
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            deactivateSession()
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            deactivateSession()
        }

        override fun onSessionStarting(session: CastSession) = Unit

        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
    }

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
            this.applicationContext = applicationContext
        }

        try {
            CastContext.getSharedInstance(applicationContext, executor)
                .addOnSuccessListener { context ->
                    mainHandler.post {
                        _castContext.value = context
                        context.sessionManager.addSessionManagerListener(
                            sessionListener,
                            CastSession::class.java,
                        )
                        context.sessionManager.currentCastSession?.let(::activateSession)
                    }
                }
                .addOnFailureListener { initializationStarted = false }
        } catch (_: Exception) {
            // Cast is optional. A broken or absent Google runtime must never block music playback.
            initializationStarted = false
        }
    }

    fun endCurrentSession() {
        mainHandler.post {
            _castContext.value?.sessionManager?.endCurrentSession(true)
        }
    }

    @OptIn(UnstableApi::class)
    private fun activateSession(session: CastSession) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { activateSession(session) }
            return
        }
        if (_sessionPlayer.value != null) {
            _deviceName.value = session.castDevice?.friendlyName
            _isCasting.value = true
            return
        }

        val context = applicationContext ?: return
        val castContext = _castContext.value ?: return
        _sessionPlayer.value = CastResolvingPlayer(
            context = context,
            castPlayer = CastPlayer(castContext),
            scope = castScope,
        )
        _deviceName.value = session.castDevice?.friendlyName
        _isCasting.value = true
    }

    private fun deactivateSession() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(::deactivateSession)
            return
        }
        _isCasting.value = false
        _deviceName.value = null
        // PlaybackService still needs the old player to copy its queue back locally. It owns and
        // releases that player after observing this null transition.
        _sessionPlayer.value = null
    }
}
