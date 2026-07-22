package ca.ilianokokoro.umihi.music.ui.screens.stats

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.models.ArtistListeningStat
import ca.ilianokokoro.umihi.music.models.TrackListeningStat
import java.time.LocalDate
import java.util.Calendar

/** Time window used to compute the top tracks / top artists rankings. */
enum class StatsPeriod(@StringRes val labelRes: Int) {
    WEEK(R.string.stats_period_week),
    MONTH(R.string.stats_period_month),
    YEAR(R.string.stats_period_year),
    ALL(R.string.stats_period_all);

    /** Start of the period in epoch millis (0 = no lower bound). */
    fun sinceMillis(): Long {
        if (this == ALL) return 0L

        return Calendar.getInstance().apply {
            when (this@StatsPeriod) {
                WEEK -> set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                MONTH -> set(Calendar.DAY_OF_MONTH, 1)
                YEAR -> set(Calendar.DAY_OF_YEAR, 1)
                ALL -> Unit
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

@Immutable
data class ListeningStatsState(
    val period: StatsPeriod = StatsPeriod.MONTH,
    val topTracks: List<TrackListeningStat> = emptyList(),
    val topArtists: List<ArtistListeningStat> = emptyList(),
    val dailyCounts: Map<LocalDate, Int> = emptyMap(),
    val wrapped: WrappedStats? = null,
)

/** Year-in-review summary shown in the highlighted "Wrapped" card. */
@Immutable
data class WrappedStats(
    val year: Int,
    val totalPlays: Int,
    /** Estimated total listening time; 0 when no duration information is available. */
    val listeningTimeMs: Long,
    val topTrack: TrackListeningStat?,
    val topArtist: ArtistListeningStat?,
    val mostActiveDay: LocalDate?,
    val mostActiveDayPlays: Int,
)
