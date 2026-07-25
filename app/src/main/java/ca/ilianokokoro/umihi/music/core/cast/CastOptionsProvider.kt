package ca.ilianokokoro.umihi.music.core.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions

/**
 * Read by the Cast SDK through the OPTIONS_PROVIDER_CLASS_NAME manifest meta-data, and only on the
 * first CastContext.getSharedInstance call — nothing here runs at cold start.
 *
 * Default Media Receiver: it is published by Google and plays arbitrary HTTPS media URLs, which is
 * all this app needs once the signed googlevideo URL has been resolved. Registering an app id in
 * the Cast Developer Console only buys the right to point at your own HTTPS-hosted receiver page,
 * and this fork hosts none.
 *
 * The three disabled options are mandatory rather than cosmetic. PlaybackService already publishes
 * a MediaLibrarySession and its own notification; left at their defaults the Cast SDK publishes a
 * SECOND MediaSession and a second media notification, and the two fight over the same playback.
 * The reconnection service resurrects sessions in the background, which would restart casting
 * behind the user's back after the app is gone.
 */
class CastOptionsProvider : OptionsProvider {

    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setCastMediaOptions(
                CastMediaOptions.Builder()
                    .setMediaSessionEnabled(false)
                    .setNotificationOptions(null)
                    .build()
            )
            .setEnableReconnectionService(false)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
