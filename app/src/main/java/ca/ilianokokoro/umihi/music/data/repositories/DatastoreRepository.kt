package ca.ilianokokoro.umihi.music.data.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ca.ilianokokoro.umihi.music.BuildConfig
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.helpers.KeystoreCrypto
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper.isNullOrInvalidId
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.AUTOPLAY
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.AUTO_UPDATE
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.CROSSFADE_SECONDS
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.COOKIES
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.DOWNLOAD_ON_METERED
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.DATA_SYNC_ID
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.KEEP_SCREEN_ON
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.SEND_PLAYBACK_DATA
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.SHOW_PODCAST_PLAYLIST
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.THEME_COLOR
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.UPDATE_CHANNEL
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.USE_AUDIO_OFFLOAD
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.USE_SPECIAL_LANGUAGE
import ca.ilianokokoro.umihi.music.core.ResponseCache
import ca.ilianokokoro.umihi.music.models.Cookies
import ca.ilianokokoro.umihi.music.models.UmihiSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.Datastore.NAME)

class DatastoreRepository(private val context: Context) {
    object PreferenceKeys {
        val COOKIES = stringPreferencesKey(Constants.Datastore.COOKIES_KEY)
        val DATA_SYNC_ID = stringPreferencesKey(Constants.Datastore.DATA_SYNC_ID)
        val UPDATE_CHANNEL = stringPreferencesKey(Constants.Datastore.UPDATE_CHANNEL_KEY)
        val SHOW_PODCAST_PLAYLIST = booleanPreferencesKey(Constants.Datastore.SHOW_PODCAST_PLAYLIST)
        val USE_SPECIAL_LANGUAGE = booleanPreferencesKey(Constants.Datastore.USE_SPECIAL_LANGUAGE)
        val USE_AUDIO_OFFLOAD = booleanPreferencesKey(Constants.Datastore.USE_AUDIO_OFFLOAD)
        val KEEP_SCREEN_ON = booleanPreferencesKey(Constants.Datastore.KEEP_SCREEN_ON)
        val AUTO_UPDATE = booleanPreferencesKey(Constants.Datastore.AUTO_UPDATE)
        val SEND_PLAYBACK_DATA = booleanPreferencesKey(Constants.Datastore.SEND_PLAYBACK_DATA)
        val DOWNLOAD_ON_METERED = booleanPreferencesKey(Constants.Datastore.DOWNLOAD_ON_METERED)
        val AUTOPLAY = booleanPreferencesKey(Constants.Datastore.AUTOPLAY)
        val CROSSFADE_SECONDS = intPreferencesKey(Constants.Datastore.CROSSFADE_SECONDS)
        val THEME_COLOR = stringPreferencesKey(Constants.Datastore.THEME_COLOR)
    }

    suspend fun <T> save(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit {
            it[key] = value
        }
        if (key == USE_SPECIAL_LANGUAGE) {
            mirrorUseSpecialLanguage(value as Boolean)
        }
    }

    /**
     * Process-wide shared settings: the DataStore read + cookie decryption run once per
     * preference change instead of once per [getSettings] call site.
     */
    val settings: Flow<UmihiSettings>
        get() = sharedSettings(context.applicationContext).filterNotNull()

    private fun settingsSource(): Flow<UmihiSettings> = context.dataStore.data.map {
        val updateChannel = it[UPDATE_CHANNEL]?.let { value -> UpdateChannel.valueOf(value) }
            ?: if (BuildConfig.IS_BETA) {
                UpdateChannel.Beta
            } else {
                UpdateChannel.Stable
            }
        val showPodcastPlaylist = it[SHOW_PODCAST_PLAYLIST] ?: true
        val useSpecialLanguage = it[USE_SPECIAL_LANGUAGE] ?: false
        val useAudioOffload = it[USE_AUDIO_OFFLOAD] ?: false
        val keepScreenOn = it[KEEP_SCREEN_ON] ?: false
        val updateChecking = it[AUTO_UPDATE] ?: true
        val sendPlaybackData = it[SEND_PLAYBACK_DATA] ?: false
        val downloadOnMetered = it[DOWNLOAD_ON_METERED] ?: false
        val autoplay = it[AUTOPLAY] ?: true
        val crossfadeSeconds = (it[CROSSFADE_SECONDS] ?: 0).coerceIn(0, 12)
        val themeColor = it[THEME_COLOR]?.let { value ->
            runCatching { ThemeColor.valueOf(value) }.getOrNull()
        } ?: ThemeColor.Dynamic
        val cookies = cookies.first()
        val dataSyncId = dataSyncId.first()

        mirrorUseSpecialLanguage(useSpecialLanguage)

        UmihiSettings(
            updateChannel = updateChannel,
            showPodcastPlaylist = showPodcastPlaylist,
            cookies = cookies,
            dataSyncId = dataSyncId,
            useSpecialLanguage = useSpecialLanguage,
            useAudioOffload = useAudioOffload,
            keepScreenOn = keepScreenOn,
            sendPlaybackData = sendPlaybackData,
            updateChecking = updateChecking,
            downloadOnMetered = downloadOnMetered,
            autoplay = autoplay,
            crossfadeSeconds = crossfadeSeconds,
            themeColor = themeColor
        )
    }

    suspend fun getSettings(): UmihiSettings {
        return settings.first()
    }

    /**
     * Keeps a plain SharedPreferences mirror of the special-language flag so
     * [getUseSpecialLanguageSync] can read it synchronously in `attachBaseContext` without
     * blocking the first frame on a DataStore read. DataStore stays the source of truth.
     */
    private fun mirrorUseSpecialLanguage(value: Boolean) {
        val prefs = context.getSharedPreferences(STARTUP_PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(STARTUP_USE_SPECIAL_LANGUAGE, false) != value) {
            prefs.edit().putBoolean(STARTUP_USE_SPECIAL_LANGUAGE, value).apply()
        }
    }

    /**
     * Reads only the theme color. Used on the startup path (setContent) to avoid the cost of the
     * full settings read (cookie decryption, dataSyncId collection) when applying the app theme.
     */
    val themeColor: Flow<ThemeColor> = context.dataStore.data.map {
        it[THEME_COLOR]?.let { value ->
            runCatching { ThemeColor.valueOf(value) }.getOrNull()
        } ?: ThemeColor.Dynamic
    }

    val cookies = context.dataStore.data.map {
        val stored = it[COOKIES] ?: String()
        Cookies(if (stored.isEmpty()) stored else KeystoreCrypto.decrypt(stored))
    }

    val dataSyncId: Flow<String?> = flow {
        context.dataStore.data.collect { prefs ->
            val id = prefs[DATA_SYNC_ID]
            if (id.isNullOrInvalidId()) {
                if (id != null) {
                    context.dataStore.edit { it.remove(DATA_SYNC_ID) }
                }
                emit(null)
            } else {
                emit(id)
            }
        }
    }

    suspend fun saveCookies(cookies: Cookies) {
        val raw = cookies.toRawCookie()
        context.dataStore.edit {
            it[COOKIES] = if (raw.isEmpty()) raw else KeystoreCrypto.encrypt(raw)
        }
        // Cached responses belong to the previous account state (login/logout).
        ResponseCache.clear()
    }

    suspend fun logOut() {
        saveCookies(Cookies())
        saveDataSyncId("")
    }

    suspend fun saveDataSyncId(newId: String) {
        if (newId.isNullOrInvalidId()) {
            context.dataStore.edit { it.remove(DATA_SYNC_ID) }
            return
        }
        context.dataStore.edit {
            it[DATA_SYNC_ID] = newId
        }
    }

    companion object {
        private const val STARTUP_PREFS_NAME = "umihi_startup"
        private const val STARTUP_USE_SPECIAL_LANGUAGE = "use_special_language"

        private val sharedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        private var sharedSettingsFlow: StateFlow<UmihiSettings?>? = null

        private fun sharedSettings(applicationContext: Context): StateFlow<UmihiSettings?> {
            return sharedSettingsFlow ?: synchronized(this) {
                sharedSettingsFlow ?: DatastoreRepository(applicationContext)
                    .settingsSource()
                    .stateIn(sharedScope, SharingStarted.Eagerly, null)
                    .also { sharedSettingsFlow = it }
            }
        }

        /**
         * Synchronous read of the special-language flag for `attachBaseContext`, backed by the
         * SharedPreferences mirror instead of DataStore so the first frame is never blocked on
         * a DataStore file open/parse.
         */
        fun getUseSpecialLanguageSync(context: Context): Boolean {
            return context.getSharedPreferences(STARTUP_PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(STARTUP_USE_SPECIAL_LANGUAGE, false)
        }
    }

    enum class UpdateChannel {
        Stable,
        Beta
    }

    enum class ThemeColor {
        Dynamic,
        OceanBlue,
        ForestGreen,
        Violet,
        SunsetOrange,
        RoseRed,
        Mono
    }

}