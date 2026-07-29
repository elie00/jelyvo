package ca.ilianokokoro.umihi.music.ui.screens.recodebug

import androidx.compose.runtime.Immutable
import ca.ilianokokoro.umihi.music.core.reco.DayBucket
import ca.ilianokokoro.umihi.music.core.reco.SourceContextHealth
import ca.ilianokokoro.umihi.music.core.reco.StoredRecoWeights

/** One affinity row of the debug lists: a label and its raw score. */
@Immutable
data class AffinityRow(
    val label: String,
    val sublabel: String = "",
    val score: Double,
)

/** Read-only snapshot the recommendation debug screen renders. Dev tooling, no pagination. */
@Immutable
data class RecoDebugState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Play events feeding the profile (its retention window). */
    val eventCount: Int = 0,
    val profileComputedAtMs: Long = 0L,
    val topArtists: List<AffinityRow> = emptyList(),
    val topTracks: List<AffinityRow> = emptyList(),
    val hourlyTopArtists: Map<DayBucket, List<String>> = emptyMap(),
    val weights: StoredRecoWeights? = null,
    val health7d: List<SourceContextHealth> = emptyList(),
    val health30d: List<SourceContextHealth> = emptyList(),
)
