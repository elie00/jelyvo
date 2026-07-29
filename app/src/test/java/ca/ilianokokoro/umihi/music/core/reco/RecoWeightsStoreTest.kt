package ca.ilianokokoro.umihi.music.core.reco

import ca.ilianokokoro.umihi.music.core.playback.PlayEndReason
import ca.ilianokokoro.umihi.music.models.PlayEvent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.ZoneOffset

class RecoWeightsStoreTest {

    private val now = 1_700_000_000_000L
    private val json = Json

    @Test
    fun `current loads a valid snapshot once and publishes it`() = runBlocking {
        val persisted = StoredRecoWeights(
            daily = RecoWeights.DAILY.copy(novelty = 0.17),
            lastTunedEpochDay = 42,
            updatedAtMs = 123,
        )
        val storage = MemorySnapshotStore(json.encodeToString(persisted))
        val store = RecoWeightsStore(FakeInputs(), storage)

        assertEquals(persisted, store.current())
        assertEquals(persisted, store.current())
        assertEquals(persisted, store.weights.value)
        assertEquals(1, storage.readCount)
    }

    @Test
    fun `corrupt snapshot is deleted and defaults remain available`() = runBlocking {
        val storage = MemorySnapshotStore("{ definitely not valid json")
        val store = RecoWeightsStore(FakeInputs(), storage)

        assertEquals(StoredRecoWeights(), store.current())
        assertEquals(1, storage.deleteCount)
        assertEquals(null, storage.value)
    }

    @Test
    fun `tuning runs and persists at most once per local day`() = runBlocking {
        val events = List(TUNER_MIN_EVENTS) {
            event(reason = PlayEndReason.SKIPPED_EARLY)
        }
        val inputs = FakeInputs(events = events)
        val storage = MemorySnapshotStore()
        val store = RecoWeightsStore(inputs, storage)
        val profile = TasteProfile(
            computedAtMs = now,
            affinity = AffinityScores(songAffinity = mapOf("track" to 10.0)),
        )

        store.maybeTune(profile, now, ZoneOffset.UTC)
        val first = store.current()
        store.maybeTune(profile, now + 1_000, ZoneOffset.UTC)

        assertEquals(1, inputs.eventsCalls)
        assertEquals(1, inputs.artistCalls)
        assertEquals(1, storage.writeCount)
        assertEquals(dailyMixEpochDay(now, ZoneOffset.UTC), first.lastTunedEpochDay)
        assertEquals(now, first.updatedAtMs)
        assertEquals(RecoWeights.DAILY.novelty * 0.9, first.daily.novelty, 1e-9)
        assertNotNull(storage.value)
    }

    @Test
    fun `a new local day permits the next tuning pass`() = runBlocking {
        val inputs = FakeInputs(events = List(TUNER_MIN_EVENTS) { event() })
        val storage = MemorySnapshotStore()
        val store = RecoWeightsStore(inputs, storage)
        val profile = TasteProfile(computedAtMs = now)

        store.maybeTune(profile, now, ZoneOffset.UTC)
        store.maybeTune(profile, now + 24 * 60 * 60 * 1_000L, ZoneOffset.UTC)

        assertEquals(2, inputs.eventsCalls)
        assertEquals(2, storage.writeCount)
    }

    @Test
    fun `reset persists defaults and blocks another pass that day`() = runBlocking {
        val inputs = FakeInputs(events = List(TUNER_MIN_EVENTS) { event() })
        val storage = MemorySnapshotStore()
        val store = RecoWeightsStore(inputs, storage)

        store.reset(now, ZoneOffset.UTC)
        store.maybeTune(TasteProfile(computedAtMs = now), now + 1_000, ZoneOffset.UTC)

        val reset = store.current()
        assertEquals(RecoWeights.DAILY, reset.daily)
        assertEquals(RecoWeights.CONTEXT, reset.context)
        assertEquals(dailyMixEpochDay(now, ZoneOffset.UTC), reset.lastTunedEpochDay)
        assertEquals(0, inputs.eventsCalls)
        assertEquals(1, storage.writeCount)
    }

    private fun event(
        reason: PlayEndReason = PlayEndReason.COMPLETED,
    ) = PlayEvent(
        youtubeId = "track",
        playedAt = now,
        durationListenedMs = 180_000,
        trackDurationMs = 180_000,
        completionRatio = 1f,
        endReason = reason.name,
        hourOfDay = 12,
        dayOfWeek = 1,
        audioOutput = null,
        sourceContext = DAILY_MIX_SOURCE_CONTEXT,
    )

    private class FakeInputs(
        private val events: List<PlayEvent> = emptyList(),
        private val artists: Map<String, String> = emptyMap(),
    ) : RecoWeightsInputs {
        var eventsCalls = 0
        var artistCalls = 0

        override suspend fun eventsSince(epochMs: Long): List<PlayEvent> {
            eventsCalls += 1
            return events
        }

        override suspend fun artistByYoutubeId(): Map<String, String> {
            artistCalls += 1
            return artists
        }
    }

    private class MemorySnapshotStore(
        var value: String? = null,
    ) : RecoWeightsSnapshotStore {
        var readCount = 0
        var writeCount = 0
        var deleteCount = 0

        override fun read(): String? {
            readCount += 1
            return value
        }

        override fun write(value: String) {
            writeCount += 1
            this.value = value
        }

        override fun delete() {
            deleteCount += 1
            value = null
        }
    }
}
