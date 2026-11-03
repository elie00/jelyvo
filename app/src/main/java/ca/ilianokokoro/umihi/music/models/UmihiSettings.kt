package ca.ilianokokoro.umihi.music.models

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.youtube.YoutubeSession
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.AudioQuality
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository.VideoQuality
import ca.ilianokokoro.umihi.music.ui.theme.ThemeColor
import ca.ilianokokoro.umihi.music.ui.theme.ThemePreset

@Immutable
data class UmihiSettings(
    val updateChecking: Boolean = true,
    val cookies: Cookies,
    override val dataSyncId: String?,
    val showPodcastPlaylist: Boolean = true,
    val useSpecialLanguage: Boolean = false,
    val useAudioOffload: Boolean = false,
    val keepScreenOn: Boolean = false,
    val sendPlaybackData: Boolean = false,
    val downloadOnMetered: Boolean = false,
    val audioQuality: AudioQuality = AudioQuality.High,
    val videoQuality: VideoQuality = VideoQuality.P1080,
    val dataSaverOnMetered: Boolean = false,
    val autoplay: Boolean = true,
    val crossfadeSeconds: Int = 0,
    val smartTransitions: Boolean = true,
    val automix: Boolean = false,
    val skipSilence: Boolean = false,
    val preferFastestRefresh: Boolean = false,
    val normalizeVolume: Boolean = true,
    val resumeOnAudioConnect: Boolean = false,
    val sleepTimerDefaultMinutes: Int = Constants.Ui.Player.SleepTimer.DEFAULT_MINUTES_FALLBACK,
    val themeColor: ThemeColor = ThemeColor.Umihi,
    val themePreset: ThemePreset = ThemePreset.Studio,
    // Profil local d'affichage (l'identite Google n'est pas modifiable) : nom choisi et chemin
    // du fichier avatar copie dans le stockage interne. Vides tant que rien n'est personnalise.
    val profileName: String = "",
    val profileAvatarPath: String = "",
    val notifyNewReleases: Boolean = false,
    val autoDownload: Boolean = false,
    val autoDownloadLiked: Boolean = true,
    val autoDownloadWifiOnly: Boolean = true,
    val autoDownloadQuotaGb: Int = Constants.Downloads.AUTO_DOWNLOAD_QUOTA_GB_DEFAULT,
) : YoutubeSession {
    override val rawCookieHeader: String get() = cookies.toRawCookie()

    override val cookieValues: Map<String, String> get() = cookies.data

    val canTrack: Boolean get() = sendPlaybackData && !cookies.isEmpty()
}
