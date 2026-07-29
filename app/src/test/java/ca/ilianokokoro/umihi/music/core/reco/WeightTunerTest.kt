package ca.ilianokokoro.umihi.music.core.reco

import ca.ilianokokoro.umihi.music.core.playback.PlayEndReason
import ca.ilianokokoro.umihi.music.models.PlayEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class WeightTunerTest {

    private val now = 1_700_000_000_000L
    private val epsilon = 1e-9

    private fun event(
        id: String = "track",
        playedAt: Long = now,
        reason: PlayEndReason = PlayEndReason.COMPLETED,
        source: String? = DAILY_MIX_SOURCE_CONTEXT,
    ) = PlayEvent(
        youtubeId = id,
        playedAt = playedAt,
        durationListenedMs = if (reason == PlayEndReason.SKIPPED_EARLY) 1_000 else 180_000,
        trackDurationMs = 180_000,
        completionRatio = if (reason == PlayEndReason.SKIPPED_EARLY) 0.01f else 1f,
        endReason = reason.name,
        hourOfDay = 12,
        dayOfWeek = 1,
        audioOutput = null,
        sourceContext = source,
    )

    private fun profile(
        songAffinity: Map<String, Double> = mapOf("track" to 10.0),
    ) = TasteProfile(
        computedAtMs = now,
        affinity = AffinityScores(songAffinity = songAffinity),
    )

    @Test
    fun `health groups recent events and ignores process deaths`() {
        val health = skipRatesBySourceContext(
            events = listOf(
                event(source = "daily"),
                event(reason = PlayEndReason.SKIPPED_EARLY, source = "daily"),
                event(source = null),
                event(reason = PlayEndReason.APP_KILLED, source = "daily"),
                event(playedAt = now - TUNER_WINDOW_MS - 1, source = "daily"),
            ),
            sinceMs = now - TUNER_WINDOW_MS,
        )

        assertEquals(
            listOf(
                SourceContextHealth("daily", plays = 2, earlySkips = 1),
                SourceContextHealth(HEALTH_CONTEXT_OTHER, plays = 1, earlySkips = 0),
            ),
            health,
        )
    }

    @Test
    fun `too little feedback leaves every weight unchanged`() {
        val current = RecoWeights(songAffinity = 0.7, novelty = 0.2)
        val events = List(TUNER_MIN_EVENTS - 1) {
            event(reason = PlayEndReason.SKIPPED_EARLY)
        }

        assertEquals(current, tune(current, events))
    }

    @Test
    fun `rejected mix lowers novelty and its dominant term`() {
        val skipped = 9
        val events = List(TUNER_MIN_EVENTS) { index ->
            event(
                reason = if (index < skipped) {
                    PlayEndReason.SKIPPED_EARLY
                } else {
                    PlayEndReason.COMPLETED
                }
            )
        }

        val tuned = tune(RecoWeights.DAILY, events)

        assertEquals(RecoWeights.DAILY.novelty * 0.9, tuned.novelty, epsilon)
        assertEquals(RecoWeights.DAILY.songAffinity * 0.9, tuned.songAffinity, epsilon)
        assertEquals(RecoWeights.DAILY.fatigue, tuned.fatigue, epsilon)
        assertEquals(RecoWeights.DAILY.skipPenalty, tuned.skipPenalty, epsilon)
    }

    @Test
    fun `exact high threshold does not fire`() {
        val events = List(TUNER_MIN_EVENTS) { index ->
            event(
                reason = if (index < 8) {
                    PlayEndReason.SKIPPED_EARLY
                } else {
                    PlayEndReason.COMPLETED
                }
            )
        }

        assertEquals(RecoWeights.DAILY, tune(RecoWeights.DAILY, events))
    }

    @Test
    fun `healthy feedback relaxes tuned terms toward defaults only`() {
        val current = RecoWeights(
            songAffinity = 0.5,
            artistAffinity = 0.2,
            coListen = 0.3,
            contextMatch = 0.25,
            novelty = 0.15,
            fatigue = 0.9,
            skipPenalty = 1.2,
        )

        val tuned = tune(
            current = current,
            events = List(TUNER_MIN_EVENTS) { event(reason = PlayEndReason.COMPLETED) },
        )

        fun expected(value: Double, default: Double) =
            value + (default - value) * TUNER_RELAX_STEP

        assertEquals(expected(current.songAffinity, RecoWeights.DAILY.songAffinity), tuned.songAffinity, epsilon)
        assertEquals(expected(current.artistAffinity, RecoWeights.DAILY.artistAffinity), tuned.artistAffinity, epsilon)
        assertEquals(expected(current.coListen, RecoWeights.DAILY.coListen), tuned.coListen, epsilon)
        assertEquals(expected(current.contextMatch, RecoWeights.DAILY.contextMatch), tuned.contextMatch, epsilon)
        assertEquals(expected(current.novelty, RecoWeights.DAILY.novelty), tuned.novelty, epsilon)
        assertEquals(current.fatigue, tuned.fatigue, epsilon)
        assertEquals(current.skipPenalty, tuned.skipPenalty, epsilon)
    }

    @Test
    fun `events outside the window and app kills provide no signal`() {
        val events = buildList {
            repeat(10) {
                add(event(playedAt = now - TUNER_WINDOW_MS - 1, reason = PlayEndReason.SKIPPED_EARLY))
                add(event(playedAt = now + 1, reason = PlayEndReason.SKIPPED_EARLY))
                add(event(reason = PlayEndReason.APP_KILLED))
            }
        }

        assertEquals(RecoWeights.DAILY, tune(RecoWeights.DAILY, events))
    }

    @Test
    fun `clamp bounds tuned terms and preserves fixed penalties`() {
        val defaults = RecoWeights.DAILY
        val current = RecoWeights(
            songAffinity = -100.0,
            artistAffinity = 100.0,
            coListen = -100.0,
            contextMatch = 100.0,
            novelty = -100.0,
            fatigue = 9.0,
            skipPenalty = 8.0,
        )

        val clamped = clampWeights(current, defaults)

        assertEquals(defaults.songAffinity * TUNER_WEIGHT_MIN_FACTOR, clamped.songAffinity, epsilon)
        assertEquals(defaults.artistAffinity * TUNER_WEIGHT_MAX_FACTOR, clamped.artistAffinity, epsilon)
        assertEquals(defaults.coListen * TUNER_WEIGHT_MIN_FACTOR, clamped.coListen, epsilon)
        assertEquals(defaults.contextMatch * TUNER_WEIGHT_MAX_FACTOR, clamped.contextMatch, epsilon)
        assertEquals(defaults.novelty * TUNER_WEIGHT_MIN_FACTOR, clamped.novelty, epsilon)
        assertEquals(current.fatigue, clamped.fatigue, epsilon)
        assertEquals(current.skipPenalty, clamped.skipPenalty, epsilon)
    }

    @Test
    fun `dominant term resolves exact ties in declaration order`() {
        val zeroWeights = RecoWeights(
            songAffinity = 0.0,
            artistAffinity = 0.0,
            coListen = 0.0,
            contextMatch = 0.0,
            novelty = 0.0,
        )

        assertEquals(
            RecoTerm.SONG_AFFINITY,
            dominantTerm(
                event = event(),
                profile = profile(songAffinity = emptyMap()),
                artistByYoutubeId = emptyMap(),
                seedIds = emptySet(),
                weights = zeroWeights,
            ),
        )
    }

    private fun tune(current: RecoWeights, events: List<PlayEvent>) = tuneWeights(
        current = current,
        events = events,
        profile = profile(),
        artistByYoutubeId = emptyMap(),
        sourceContext = DAILY_MIX_SOURCE_CONTEXT,
        nowMs = now,
        defaults = RecoWeights.DAILY,
    )
}
