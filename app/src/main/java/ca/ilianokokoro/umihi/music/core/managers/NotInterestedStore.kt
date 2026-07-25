package ca.ilianokokoro.umihi.music.core.managers

import android.content.Context
import android.widget.Toast
import ca.ilianokokoro.umihi.music.R
import ca.ilianokokoro.umihi.music.data.repositories.DatastoreRepository
import ca.ilianokokoro.umihi.music.models.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Process-local mirror of rejected ids. RadioRepository has no Context, so it reads this set
 * without blocking while PlaybackService owns hydration and lifecycle synchronization.
 */
object NotInterestedStore {
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var ids: Set<String> = emptySet()
    private var collectJob: Job? = null

    val hiddenIds: Set<String>
        get() = ids

    suspend fun hydrate(context: Context, syncScope: CoroutineScope) {
        val repository = DatastoreRepository(context.applicationContext)
        ids = repository.notInterested.first().toSet()
        if (collectJob?.isActive != true) {
            collectJob = syncScope.launch {
                repository.notInterested.collect { ids = it.toSet() }
            }
        }
    }

    /**
     * Applies the rejection in memory before persisting it so continuations already in flight see
     * the new rule. The playing item and manually queued entries are protected by PlayerManager.
     */
    fun hide(song: Song, context: Context) {
        val youtubeId = song.youtubeId
        if (youtubeId.isBlank()) return

        val appContext = context.applicationContext
        ids = ids + youtubeId
        PlayerManager.removeHiddenSuggestions(ids)
        Toast.makeText(
            appContext,
            appContext.getString(R.string.not_interested_toast),
            Toast.LENGTH_SHORT,
        ).show()
        writeScope.launch {
            DatastoreRepository(appContext).addNotInterested(youtubeId)
        }
    }
}
