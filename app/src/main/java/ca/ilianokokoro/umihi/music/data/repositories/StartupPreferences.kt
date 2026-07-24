package ca.ilianokokoro.umihi.music.data.repositories

import android.content.Context

/**
 * Tiny synchronous mirror for values needed before the first activity frame.
 *
 * Keep this separate from [DatastoreRepository]: touching that class initializes coroutine
 * scopes, preference keys and flows that are useful after launch but expensive in
 * `attachBaseContext`.
 */
object StartupPreferences {
    private const val FILE_NAME = "umihi_startup"
    private const val USE_SPECIAL_LANGUAGE = "use_special_language"

    fun useSpecialLanguage(context: Context): Boolean {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getBoolean(USE_SPECIAL_LANGUAGE, false)
    }

    fun setUseSpecialLanguage(context: Context, enabled: Boolean) {
        val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        if (preferences.getBoolean(USE_SPECIAL_LANGUAGE, false) != enabled) {
            preferences.edit()
                .putBoolean(USE_SPECIAL_LANGUAGE, enabled)
                .apply()
        }
    }
}
