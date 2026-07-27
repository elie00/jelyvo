package ca.ilianokokoro.umihi.music.data.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.NotInterestedFilter
import ca.ilianokokoro.umihi.music.core.helpers.KeystoreCrypto
import ca.ilianokokoro.umihi.music.core.helpers.UmihiHelper.isNullOrInvalidId
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.AUDIO_QUALITY
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.AUTOPLAY
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.AUTO_DOWNLOAD
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.AUTO_DOWNLOAD_LIKED
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.AUTO_DOWNLOAD_QUOTA_GB
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.AUTO_DOWNLOAD_WIFI_ONLY
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.CROSSFADE_SECONDS
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.COOKIES
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.DOWNLOAD_ON_METERED
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.DATA_SYNC_ID
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.DATA_SAVER_ON_METERED
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.AUTO_UPDATE
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.KEEP_SCREEN_ON
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.LAST_STREAM_QUALITY
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.NOTIFIED_RELEASE_IDS
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.NORMALIZE_VOLUME
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.NOTIFY_NEW_RELEASES
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.RESUME_ON_AUDIO_CONNECT
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.SLEEP_TIMER_DEFAULT_MINUTES
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.SEND_PLAYBACK_DATA
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.SHOW_PODCAST_PLAYLIST
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.PreferenceKeys.THEME_COLOR
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.Datastore.NAME)

class DatastoreRepository(private val context: Context) {
    object PreferenceKeys {
        val COOKIES = stringPreferencesKey(Constants.Datastore.COOKIES_KEY)
        val DATA_SYNC_ID = stringPreferencesKey(Constants.Datastore.DATA_SYNC_ID)
        val SHOW_PODCAST_PLAYLIST = booleanPreferencesKey(Constants.Datastore.SHOW_PODCAST_PLAYLIST)
        val USE_SPECIAL_LANGUAGE = booleanPreferencesKey(Constants.Datastore.USE_SPECIAL_LANGUAGE)
        val USE_AUDIO_OFFLOAD = booleanPreferencesKey(Constants.Datastore.USE_AUDIO_OFFLOAD)
        val KEEP_SCREEN_ON = booleanPreferencesKey(Constants.Datastore.KEEP_SCREEN_ON)
        val AUTO_UPDATE = booleanPreferencesKey(Constants.Datastore.AUTO_UPDATE)
        val SEND_PLAYBACK_DATA = booleanPreferencesKey(Constants.Datastore.SEND_PLAYBACK_DATA)
        val DOWNLOAD_ON_METERED = booleanPreferencesKey(Constants.Datastore.DOWNLOAD_ON_METERED)
        val AUDIO_QUALITY = stringPreferencesKey(Constants.Datastore.AUDIO_QUALITY)
        val DATA_SAVER_ON_METERED =
            booleanPreferencesKey(Constants.Datastore.DATA_SAVER_ON_METERED)
        val LAST_STREAM_QUALITY = stringPreferencesKey(Constants.Datastore.LAST_STREAM_QUALITY)
        val AUTOPLAY = booleanPreferencesKey(Constants.Datastore.AUTOPLAY)
        val CROSSFADE_SECONDS = intPreferencesKey(Constants.Datastore.CROSSFADE_SECONDS)
        val NORMALIZE_VOLUME = booleanPreferencesKey(Constants.Datastore.NORMALIZE_VOLUME)
        val RESUME_ON_AUDIO_CONNECT =
            booleanPreferencesKey(Constants.Datastore.RESUME_ON_AUDIO_CONNECT)
        val SLEEP_TIMER_DEFAULT_MINUTES =
            intPreferencesKey(Constants.Datastore.SLEEP_TIMER_DEFAULT_MINUTES)
        val THEME_COLOR = stringPreferencesKey(Constants.Datastore.THEME_COLOR)
        val THEME_PRESET = stringPreferencesKey(Constants.Datastore.THEME_PRESET)
        val PROFILE_NAME = stringPreferencesKey(Constants.Datastore.PROFILE_NAME)
        val PROFILE_AVATAR = stringPreferencesKey(Constants.Datastore.PROFILE_AVATAR)
        val NOTIFY_NEW_RELEASES = booleanPreferencesKey(Constants.Datastore.NOTIFY_NEW_RELEASES)
        val NOTIFIED_RELEASE_IDS = stringSetPreferencesKey(Constants.Datastore.NOTIFIED_RELEASE_IDS)
        val AUTO_DOWNLOAD = booleanPreferencesKey(Constants.Datastore.AUTO_DOWNLOAD)
        val AUTO_DOWNLOAD_LIKED = booleanPreferencesKey(Constants.Datastore.AUTO_DOWNLOAD_LIKED)
        val AUTO_DOWNLOAD_WIFI_ONLY = booleanPreferencesKey(Constants.Datastore.AUTO_DOWNLOAD_WIFI_ONLY)
        val AUTO_DOWNLOAD_QUOTA_GB = intPreferencesKey(Constants.Datastore.AUTO_DOWNLOAD_QUOTA_GB)
        val RECENT_SEARCHES = stringPreferencesKey(Constants.Datastore.RECENT_SEARCHES)
        val NOT_INTERESTED = stringPreferencesKey(Constants.Datastore.NOT_INTERESTED)
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
        val showPodcastPlaylist = it[SHOW_PODCAST_PLAYLIST] ?: true
        val useSpecialLanguage = it[USE_SPECIAL_LANGUAGE] ?: false
        val useAudioOffload = it[USE_AUDIO_OFFLOAD] ?: false
        val keepScreenOn = it[KEEP_SCREEN_ON] ?: false
        val updateChecking = it[AUTO_UPDATE] ?: true
        val sendPlaybackData = it[SEND_PLAYBACK_DATA] ?: false
        val downloadOnMetered = it[DOWNLOAD_ON_METERED] ?: false
        val audioQuality = it[AUDIO_QUALITY]?.let { value ->
            runCatching { AudioQuality.valueOf(value) }.getOrNull()
        } ?: AudioQuality.High
        val dataSaverOnMetered = it[DATA_SAVER_ON_METERED] ?: false
        val autoplay = it[AUTOPLAY] ?: true
        val crossfadeSeconds = (it[CROSSFADE_SECONDS] ?: 0).coerceIn(0, 12)
        val normalizeVolume = it[NORMALIZE_VOLUME] ?: true
        val resumeOnAudioConnect = it[RESUME_ON_AUDIO_CONNECT] ?: false
        val sleepTimerDefaultMinutes = (it[SLEEP_TIMER_DEFAULT_MINUTES]
            ?: Constants.Ui.Player.SleepTimer.DEFAULT_MINUTES_FALLBACK).coerceIn(
            Constants.Ui.Player.SleepTimer.STEP_VALUE,
            Constants.Ui.Player.SleepTimer.STEP_VALUE * Constants.Ui.Player.SleepTimer.STEP_AMOUNT
        )
        val themeColor = it[THEME_COLOR]?.let { value ->
            runCatching { ThemeColor.valueOf(value) }.getOrNull()
        } ?: ThemeColor.Umihi
        val themePreset = it[PreferenceKeys.THEME_PRESET]?.let { value ->
            runCatching { ThemePreset.valueOf(value) }.getOrNull()
        } ?: ThemePreset.Jelyvo
        val profileName = it[PreferenceKeys.PROFILE_NAME].orEmpty()
        val profileAvatarPath = it[PreferenceKeys.PROFILE_AVATAR].orEmpty()
        val notifyNewReleases = it[NOTIFY_NEW_RELEASES] ?: false
        val autoDownload = it[AUTO_DOWNLOAD] ?: false
        val autoDownloadLiked = it[AUTO_DOWNLOAD_LIKED] ?: true
        val autoDownloadWifiOnly = it[AUTO_DOWNLOAD_WIFI_ONLY] ?: true
        val autoDownloadQuotaGb = (it[AUTO_DOWNLOAD_QUOTA_GB]
            ?: Constants.Downloads.AUTO_DOWNLOAD_QUOTA_GB_DEFAULT).coerceIn(
            Constants.Downloads.AUTO_DOWNLOAD_QUOTA_GB_MIN,
            Constants.Downloads.AUTO_DOWNLOAD_QUOTA_GB_MAX
        )
        val cookies = cookies.first()
        // onBehalfOfUser identifies the signed-in channel; without a cookie there is no session to
        // act on behalf of, so a failed decrypt reads as fully logged out.
        val dataSyncId = if (cookies.isEmpty()) null else dataSyncId.first()

        mirrorUseSpecialLanguage(useSpecialLanguage)

        UmihiSettings(
            showPodcastPlaylist = showPodcastPlaylist,
            cookies = cookies,
            dataSyncId = dataSyncId,
            useSpecialLanguage = useSpecialLanguage,
            useAudioOffload = useAudioOffload,
            keepScreenOn = keepScreenOn,
            updateChecking = updateChecking,
            sendPlaybackData = sendPlaybackData,
            downloadOnMetered = downloadOnMetered,
            audioQuality = audioQuality,
            dataSaverOnMetered = dataSaverOnMetered,
            autoplay = autoplay,
            crossfadeSeconds = crossfadeSeconds,
            normalizeVolume = normalizeVolume,
            resumeOnAudioConnect = resumeOnAudioConnect,
            sleepTimerDefaultMinutes = sleepTimerDefaultMinutes,
            themeColor = themeColor,
            themePreset = themePreset,
            profileName = profileName,
            profileAvatarPath = profileAvatarPath,
            notifyNewReleases = notifyNewReleases,
            autoDownload = autoDownload,
            autoDownloadLiked = autoDownloadLiked,
            autoDownloadWifiOnly = autoDownloadWifiOnly,
            autoDownloadQuotaGb = autoDownloadQuotaGb
        )
    }

    suspend fun getSettings(): UmihiSettings {
        return settings.first()
    }

    /**
     * Keeps a plain SharedPreferences mirror of the special-language flag so
     * [StartupPreferences] can read it synchronously in `attachBaseContext` without
     * blocking the first frame on a DataStore read. DataStore stays the source of truth.
     */
    private fun mirrorUseSpecialLanguage(value: Boolean) {
        StartupPreferences.setUseSpecialLanguage(context, value)
    }

    /**
     * Reads only the theme color. Used on the startup path (setContent) to avoid the cost of the
     * full settings read (cookie decryption, dataSyncId collection) when applying the app theme.
     */
    val themeColor: Flow<ThemeColor> = context.dataStore.data.map {
        it[THEME_COLOR]?.let { value ->
            runCatching { ThemeColor.valueOf(value) }.getOrNull()
        } ?: ThemeColor.Umihi
    }

    /** Same startup-path treatment as [themeColor]: the preset gates the whole theme. */
    val themePreset: Flow<ThemePreset> = context.dataStore.data.map {
        it[PreferenceKeys.THEME_PRESET]?.let { value ->
            runCatching { ThemePreset.valueOf(value) }.getOrNull()
        } ?: ThemePreset.Jelyvo
    }

    /** Lightweight flows used on the latency-sensitive stream-resolution path. */
    val audioQuality: Flow<AudioQuality> = context.dataStore.data.map {
        it[AUDIO_QUALITY]?.let { value ->
            runCatching { AudioQuality.valueOf(value) }.getOrNull()
        } ?: AudioQuality.High
    }

    val dataSaverOnMetered: Flow<Boolean> = context.dataStore.data.map {
        it[DATA_SAVER_ON_METERED] ?: false
    }

    suspend fun getLastStreamQuality(): AudioQuality =
        context.dataStore.data.first()[LAST_STREAM_QUALITY]?.let { value ->
            runCatching { AudioQuality.valueOf(value) }.getOrNull()
        } ?: AudioQuality.High

    suspend fun setLastStreamQuality(quality: AudioQuality) {
        context.dataStore.edit {
            it[LAST_STREAM_QUALITY] = quality.name
        }
    }

    /**
     * Reads only the new-releases notification toggle. Used on the startup path (Application) to
     * decide whether to (re)schedule the periodic check without paying for the full settings read.
     */
    val notifyNewReleases: Flow<Boolean> = context.dataStore.data.map {
        it[NOTIFY_NEW_RELEASES] ?: false
    }

    /**
     * Reads only the offline auto-download toggle and its Wi-Fi-only constraint. Used on the
     * startup path (Application) to (re)schedule [OfflineSyncWorker] without the full settings read.
     */
    val autoDownload: Flow<Boolean> = context.dataStore.data.map {
        it[AUTO_DOWNLOAD] ?: false
    }

    val autoDownloadWifiOnly: Flow<Boolean> = context.dataStore.data.map {
        it[AUTO_DOWNLOAD_WIFI_ONLY] ?: true
    }

    /** The set of release browseIds already surfaced by [NewReleasesWorker], persisted so a
     * release is never notified twice across background runs. */
    suspend fun getNotifiedReleaseIds(): Set<String> {
        return context.dataStore.data.first()[NOTIFIED_RELEASE_IDS] ?: emptySet()
    }

    suspend fun setNotifiedReleaseIds(ids: Set<String>) {
        context.dataStore.edit {
            it[NOTIFIED_RELEASE_IDS] = ids
        }
    }

    /**
     * Submitted search queries, most recent first, deduped (case-insensitive) and capped at
     * [Constants.Datastore.RECENT_SEARCHES_MAX]. Stored as a JSON list so ordering survives.
     */
    val recentSearches: Flow<List<String>> = context.dataStore.data.map {
        decodeRecentSearches(it[PreferenceKeys.RECENT_SEARCHES])
    }

    suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return
        }
        context.dataStore.edit { prefs ->
            val current = decodeRecentSearches(prefs[PreferenceKeys.RECENT_SEARCHES])
            val updated = (listOf(trimmed) + current.filterNot { entry ->
                entry.equals(trimmed, ignoreCase = true)
            }).take(Constants.Datastore.RECENT_SEARCHES_MAX)
            prefs[PreferenceKeys.RECENT_SEARCHES] = Json.encodeToString(updated)
        }
    }

    suspend fun removeRecentSearch(query: String) {
        context.dataStore.edit { prefs ->
            val updated = decodeRecentSearches(prefs[PreferenceKeys.RECENT_SEARCHES])
                .filterNot { it == query }
            if (updated.isEmpty()) {
                prefs.remove(PreferenceKeys.RECENT_SEARCHES)
            } else {
                prefs[PreferenceKeys.RECENT_SEARCHES] = Json.encodeToString(updated)
            }
        }
    }

    suspend fun clearRecentSearches() {
        context.dataStore.edit {
            it.remove(PreferenceKeys.RECENT_SEARCHES)
        }
    }

    private fun decodeRecentSearches(stored: String?): List<String> {
        if (stored.isNullOrEmpty()) {
            return emptyList()
        }
        return runCatching { Json.decodeFromString<List<String>>(stored) }
            .getOrDefault(emptyList())
    }

    /** YouTube ids rejected from autoplay and radio, most recent first. */
    val notInterested: Flow<List<String>> = context.dataStore.data.map {
        NotInterestedFilter.decode(it[PreferenceKeys.NOT_INTERESTED])
    }

    suspend fun addNotInterested(youtubeId: String) {
        context.dataStore.edit { prefs ->
            val updated = NotInterestedFilter.add(
                current = NotInterestedFilter.decode(prefs[PreferenceKeys.NOT_INTERESTED]),
                youtubeId = youtubeId,
                max = Constants.Datastore.NOT_INTERESTED_MAX,
            )
            prefs[PreferenceKeys.NOT_INTERESTED] = NotInterestedFilter.encode(updated)
        }
    }

    suspend fun clearNotInterested() {
        context.dataStore.edit {
            it.remove(PreferenceKeys.NOT_INTERESTED)
        }
    }

    val cookies = context.dataStore.data.map {
        val stored = it[COOKIES] ?: String()
        // A null decrypt means the key is gone (restored data, wiped Keystore): read it as logged
        // out rather than handing a corrupt cookie to the API layer.
        Cookies(if (stored.isEmpty()) stored else KeystoreCrypto.decrypt(stored).orEmpty())
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
        val stored = if (raw.isEmpty()) raw else KeystoreCrypto.encrypt(raw)
        context.dataStore.edit {
            // No ciphertext means encryption failed: drop the session rather than persist the
            // cookie in clear.
            if (stored == null) {
                it.remove(COOKIES)
            } else {
                it[COOKIES] = stored
            }
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

    }

    enum class ThemeColor {
        Umihi,
        Dynamic,
        OceanBlue,
        ForestGreen,
        Violet,
        SunsetOrange,
        RoseRed,
        Mono
    }

    /**
     * Full theme identities (colors + typography + shapes), against [ThemeColor] which only swaps
     * the accent. [Jelyvo] keeps the brand look and the accent picker; the others override it all.
     */
    enum class ThemePreset {
        Jelyvo,
        Minuit,
        Vinyle,
        Neon,
        Oled,
    }

    enum class AudioQuality {
        Low,
        Normal,
        High,
    }

}
