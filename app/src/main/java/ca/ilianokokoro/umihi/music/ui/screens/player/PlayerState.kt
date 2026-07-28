package ca.ilianokokoro.umihi.music.ui.screens.player

import ca.ilianokokoro.umihi.music.core.ApiResult
import ca.ilianokokoro.umihi.music.core.Constants
import ca.ilianokokoro.umihi.music.core.managers.AbLoop
import ca.ilianokokoro.umihi.music.models.Lyrics
import ca.ilianokokoro.umihi.music.models.Song
import androidx.compose.runtime.Immutable


@Immutable
data class PlayerState(
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val playbackProgress: PlaybackProgress = PlaybackProgress(),
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val isSeekBarHeld: Boolean = false,
    val isQueueModalShown: Boolean = false,
    val isSavingQueue: Boolean = false,
    val smartTransitions: Boolean = true,
    val isSleepTimerModalShown: Boolean = false,
    val sleepTimerRemainingSeconds: Long? = null,
    // Duration (minutes) preselected when the sleep timer sheet opens; user setting.
    val sleepTimerDefaultMinutes: Int = Constants.Ui.Player.SleepTimer.DEFAULT_MINUTES_FALLBACK,
    val isSpeedSelectorShown: Boolean = false,
    val isPlayerMenuShown: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val isLoggedIn: Boolean = false,
    val isLiked: Boolean = false,
    val isLiking: Boolean = false,
    val isResolvingArtist: Boolean = false,
    val isResolvingAlbum: Boolean = false,
    val lyrics: ApiResult<Lyrics> = ApiResult.Loading,
    val areLyricsShown: Boolean = false,
    val abLoop: AbLoop? = null,
    // True when the current item is a video or has a linked music-video variant: shows the
    // Song|Video pill toggle at the top of the player.
    val hasVideoVariant: Boolean = false,
    // True while a Song<->Video mode switch is resolving streams; debounces the pill toggle.
    val isSwitchingPlaybackMode: Boolean = false,
    // True while the video fills the whole screen (landscape rotation or the fullscreen button),
    // hiding all player chrome behind auto-hiding overlay controls. Video mode only.
    val isVideoFullscreen: Boolean = false,
)

@Immutable
data class PlaybackProgress(
    val position: Float = 0f,
    val duration: Float = 0f,
)
