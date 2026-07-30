package ca.ilianokokoro.umihi.music.core.reco

import ca.ilianokokoro.umihi.music.core.domain.ArtistRef
import ca.ilianokokoro.umihi.music.core.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoScorerTest {

    private val now = 1_700_000_000_000L
    private val epsilon = 1e-9

    private fun candidate(
        id: String,
        artist: String = "Artist $id",
        lastPlayedAtMs: Long? = null,
    ) = Track(
        id = id,
        title = "Title $id",
        artist = ArtistRef(name = artist),
    ).let { track ->
        DailyMixCandidate(
            track = track,
            value = track,
            lastPlayedAtMs = lastPlayedAtMs,
        )
    }

    private fun profile(
        songAffinity: Map<String, Double> = emptyMap(),
        artistAffinity: Map<String, Double> = emptyMap(),
        coListen: CoListenGraph = CoListenGraph(),
        hourlyTopArtists: Map<DayBucket, List<String>> = emptyMap(),
    ) = TasteProfile(
        computedAtMs = now,
        affinity = AffinityScores(artistAffinity = artistAffinity, songAffinity = songAffinity),
        coListen = coListen,
        hourlyTopArtists = hourlyTopArtists,
    )

    private val context = RecoContext(nowMs = now)

    // --- veto -------------------------------------------------------------------------------

    @Test
    fun `a not interested track has no score at all`() {
        assertNull(
            recoScore(
                candidate("a"),
                profile(songAffinity = mapOf("a" to 10.0)),
                RecoContext(nowMs = now, notInterestedIds = setOf("a")),
            )
        )
    }

    @Test
    fun `a blank id has no score at all`() {
        assertNull(recoScore(candidate(""), profile(), context))
    }

    // --- normalization ----------------------------------------------------------------------

    @Test
    fun `squash01 maps positives into the unit interval and clamps negatives`() {
        assertEquals(0.0, squash01(-3.0), epsilon)
        assertEquals(0.0, squash01(0.0), epsilon)
        assertEquals(0.5, squash01(1.0), epsilon)
        assertTrue(squash01(1_000_000.0) < 1.0)
    }

    @Test
    fun `every term stays bounded so the score cannot exceed the positive weights`() {
        val weights = RecoWeights()
        val maxPositive = weights.songAffinity + weights.artistAffinity + weights.coListen +
            weights.contextMatch + weights.novelty
        val score = recoScore(
            candidate("a", artist = "Star"),
            profile(
                songAffinity = mapOf("a" to 1_000.0),
                artistAffinity = mapOf(artistKey("Star") to 1_000.0),
                coListen = CoListenGraph(edges = mapOf("seed" to mapOf("a" to 1_000.0))),
                hourlyTopArtists = mapOf(DayBucket.MORNING to listOf("Star")),
            ),
            RecoContext(nowMs = now, dayBucket = DayBucket.MORNING, seedIds = setOf("seed")),
            weights,
        )!!

        assertTrue(score <= maxPositive)
    }

    // --- individual terms -------------------------------------------------------------------

    @Test
    fun `the score is monotonic in song affinity`() {
        val tasteProfile = profile(songAffinity = mapOf("hi" to 5.0, "lo" to 1.0))
        // Same artist so the artist term cannot interfere.
        val hi = recoScore(candidate("hi", artist = "X"), tasteProfile, context)!!
        val lo = recoScore(candidate("lo", artist = "X"), tasteProfile, context)!!

        assertTrue(hi > lo)
    }

    @Test
    fun `negative song affinity turns into a skip penalty`() {
        val score = recoScore(
            candidate("skipped"),
            profile(songAffinity = mapOf("skipped" to -1.0)),
            context,
        )!!

        // Only novelty pushes up; squash01(1.0) = 0.5 of skip penalty pulls down.
        assertEquals(RecoWeights.DAILY.novelty * 1.0 - RecoWeights.DAILY.skipPenalty * 0.5, score, epsilon)
    }

    @Test
    fun `artist affinity reaches tracks never played themselves`() {
        val tasteProfile = profile(artistAffinity = mapOf(artistKey("Loved Artist") to 4.0))
        val ofLoved = recoScore(candidate("new1", artist = "Loved Artist"), tasteProfile, context)!!
        val ofUnknown = recoScore(candidate("new2", artist = "Unknown"), tasteProfile, context)!!

        assertTrue(ofLoved > ofUnknown)
    }

    @Test
    fun `co listen weight only counts from the seed tracks`() {
        val graph = CoListenGraph(
            edges = mapOf(
                "seed" to mapOf("near" to 3.0),
                "other" to mapOf("far" to 3.0),
            )
        )
        val seeded = RecoContext(nowMs = now, seedIds = setOf("seed"))
        val near = recoScore(candidate("near", artist = "X"), profile(coListen = graph), seeded)!!
        val far = recoScore(candidate("far", artist = "X"), profile(coListen = graph), seeded)!!

        assertTrue(near > far)
    }

    @Test
    fun `context match follows the artist rank in the current bucket`() {
        val tasteProfile = profile(
            hourlyTopArtists = mapOf(DayBucket.EVENING to listOf("First", "Second")),
        )
        val evening = RecoContext(nowMs = now, dayBucket = DayBucket.EVENING)
        val first = recoScore(candidate("a", artist = "first"), tasteProfile, evening)!!
        val second = recoScore(candidate("b", artist = "Second"), tasteProfile, evening)!!
        val outside = recoScore(candidate("c", artist = "Nobody"), tasteProfile, evening)!!

        assertTrue(first > second)
        assertTrue(second > outside)
        // Without a bucket the term disappears entirely.
        val noBucket = recoScore(candidate("a", artist = "first"), tasteProfile, context)!!
        assertEquals(outside, noBucket, epsilon)
    }

    @Test
    fun `novelty rewards never played tracks`() {
        val played = candidate("a", artist = "X", lastPlayedAtMs = now - DAILY_MIX_FATIGUE_MS * 2)
        val never = candidate("b", artist = "X")

        val playedScore = recoScore(played, profile(), context)!!
        val neverScore = recoScore(never, profile(), context)!!

        assertEquals(RecoWeights.DAILY.novelty, neverScore - playedScore, epsilon)
    }

    @Test
    fun `fatigue is strongest right after a play and gone after 48h`() {
        val justPlayed = recoScore(candidate("a", artist = "X", lastPlayedAtMs = now), profile(), context)!!
        val halfway = recoScore(
            candidate("b", artist = "X", lastPlayedAtMs = now - DAILY_MIX_FATIGUE_MS / 2),
            profile(),
            context,
        )!!
        val longAgo = recoScore(
            candidate("c", artist = "X", lastPlayedAtMs = now - DAILY_MIX_FATIGUE_MS),
            profile(),
            context,
        )!!

        assertTrue(justPlayed < halfway)
        assertTrue(halfway < longAgo)
        assertEquals(-RecoWeights.DAILY.fatigue, justPlayed, epsilon)
        assertEquals(0.0, longAgo, epsilon)
    }

    // --- weights ----------------------------------------------------------------------------

    @Test
    fun `context weights let the hour bucket overturn a pure affinity ranking`() {
        val tasteProfile = profile(
            songAffinity = mapOf("fav" to 9.0),
            hourlyTopArtists = mapOf(DayBucket.EVENING to listOf("Evening Star")),
        )
        val evening = RecoContext(nowMs = now, dayBucket = DayBucket.EVENING)
        val fav = candidate("fav", artist = "Anyone")
        val contextual = candidate("eveningTrack", artist = "Evening Star")

        // Daily weights: raw affinity wins.
        assertTrue(
            recoScore(fav, tasteProfile, evening, RecoWeights.DAILY)!! >
                recoScore(contextual, tasteProfile, evening, RecoWeights.DAILY)!!
        )
        // Context weights: the bucket artist wins.
        assertTrue(
            recoScore(contextual, tasteProfile, evening, RecoWeights.CONTEXT)!! >
                recoScore(fav, tasteProfile, evening, RecoWeights.CONTEXT)!!
        )
    }

    // --- seeds ------------------------------------------------------------------------------

    @Test
    fun `top affinity seeds keep only positive scores in order`() {
        val seeds = topAffinitySeeds(
            profile(songAffinity = mapOf("a" to 1.0, "b" to 5.0, "c" to -2.0, "d" to 3.0)),
            count = 2,
        )

        assertEquals(listOf("b", "d"), seeds.toList())
    }
}
