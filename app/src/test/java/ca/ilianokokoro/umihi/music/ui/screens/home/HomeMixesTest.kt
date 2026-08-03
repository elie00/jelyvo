package ca.ilianokokoro.umihi.music.ui.screens.home

import ca.ilianokokoro.umihi.music.core.reco.DayBucket
import ca.ilianokokoro.umihi.music.models.TrackListeningStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMixesTest {

    private val now = 1_000_000_000_000L
    private val recentEnough = now - 1_000L
    private val longSilent = now - HOME_MIX_REDISCOVER_GAP_MS - 1_000L

    private fun stat(
        id: String,
        playCount: Int = 3,
        lastPlayedAt: Long = recentEnough,
    ) = TrackListeningStat(
        youtubeId = id,
        title = "Title $id",
        artist = "Artist $id",
        thumbnailHref = "",
        duration = "3:00",
        isExplicit = false,
        playCount = playCount,
        lastPlayedAt = lastPlayedAt,
    )

    @Test
    fun `replay mix mirrors the recent top tracks in order`() {
        val tops = (1..6).map { stat("t$it") }

        val mixes = buildForYouMixes(tops, emptyList(), now)

        assertEquals(1, mixes.size)
        assertEquals(HomeMixKind.REPLAY, mixes[0].kind)
        assertEquals(tops.map { it.youtubeId }, mixes[0].songs.map { it.youtubeId })
    }

    @Test
    fun `a replay mix under the minimum size is not offered`() {
        val tops = (1 until HOME_MIX_MIN_SONGS).map { stat("t$it") }

        assertTrue(buildForYouMixes(tops, emptyList(), now).isEmpty())
    }

    @Test
    fun `replay mix is capped at the maximum queue length`() {
        val tops = (1..40).map { stat("t$it") }

        val mixes = buildForYouMixes(tops, emptyList(), now)

        assertEquals(HOME_MIX_MAX_SONGS, mixes[0].songs.size)
    }

    @Test
    fun `rediscoveries keep only repeat tracks that went silent`() {
        val allTime =
            (1..5).map { stat("old$it", playCount = 4, lastPlayedAt = longSilent) } +
                stat("fresh", playCount = 10, lastPlayedAt = recentEnough) +
                stat("oneOff", playCount = 1, lastPlayedAt = longSilent)

        val mixes = buildForYouMixes(emptyList(), allTime, now)

        assertEquals(1, mixes.size)
        assertEquals(HomeMixKind.REDISCOVER, mixes[0].kind)
        assertEquals(
            (1..5).map { "old$it" },
            mixes[0].songs.map { it.youtubeId },
        )
    }

    @Test
    fun `both mixes can coexist with replay first`() {
        val recent = (1..5).map { stat("r$it") }
        val allTime = (1..5).map { stat("old$it", lastPlayedAt = longSilent) }

        val mixes = buildForYouMixes(recent, allTime, now)

        assertEquals(listOf(HomeMixKind.REPLAY, HomeMixKind.REDISCOVER), mixes.map { it.kind })
    }

    @Test
    fun `empty history yields no mixes`() {
        assertTrue(buildForYouMixes(emptyList(), emptyList(), now).isEmpty())
    }

    @Test
    fun `essentials diversify all time favorites by artist`() {
        val allTime = (1..8).map { stat("a$it").copy(artist = "Same artist") } +
            (1..12).map { stat("b$it").copy(artist = "Artist $it") }

        val essentials = buildForYouMixes(emptyList(), allTime, now)
            .single { it.kind == HomeMixKind.ESSENTIALS }

        assertEquals(2, essentials.songs.count { it.artist == "Same artist" })
        assertTrue(essentials.songs.size >= HOME_MIX_ESSENTIALS_MIN_SONGS)
    }

    @Test
    fun `recent discoveries keep one off plays ordered by freshness`() {
        val discoveries = (1..6).map { index ->
            stat(
                id = "new$index",
                playCount = 1,
                lastPlayedAt = now - index * 1_000L,
            )
        } + stat(
            id = "too-old",
            playCount = 1,
            lastPlayedAt = now - HOME_MIX_DISCOVERY_WINDOW_MS - 1,
        )

        val mix = buildForYouMixes(emptyList(), discoveries, now)
            .single { it.kind == HomeMixKind.RECENT_DISCOVERIES }

        assertEquals((1..6).map { "new$it" }, mix.songs.map { it.youtubeId })
    }

    @Test
    fun `the daily mix takes the first slot of the row`() {
        val history = buildForYouMixes((1..5).map { stat("r$it") }, emptyList(), now)
        val daily = (1..5).map { stat("d$it").toSong() }

        val mixes = withDailyMix(daily, history)

        assertEquals(listOf(HomeMixKind.DAILY, HomeMixKind.REPLAY), mixes.map { it.kind })
        assertEquals(daily.map { it.youtubeId }, mixes[0].songs.map { it.youtubeId })
    }

    @Test
    fun `a too small daily mix is not offered`() {
        val history = buildForYouMixes((1..5).map { stat("r$it") }, emptyList(), now)
        val daily = (1..HOME_MIX_MIN_SONGS - 1).map { stat("d$it").toSong() }

        assertEquals(history, withDailyMix(daily, history))
    }

    @Test
    fun `the context mix slots in right behind the daily mix`() {
        val history = buildForYouMixes((1..5).map { stat("r$it") }, emptyList(), now)
        val daily = (1..5).map { stat("d$it").toSong() }
        val evening = (1..5).map { stat("e$it").toSong() }

        val mixes = withContextMix(evening, DayBucket.EVENING, withDailyMix(daily, history))

        assertEquals(
            listOf(HomeMixKind.DAILY, HomeMixKind.CONTEXT_EVENING, HomeMixKind.REPLAY),
            mixes.map { it.kind },
        )
    }

    @Test
    fun `without a daily mix the context mix takes the first slot`() {
        val history = buildForYouMixes((1..5).map { stat("r$it") }, emptyList(), now)
        val morning = (1..5).map { stat("m$it").toSong() }

        val mixes = withContextMix(morning, DayBucket.MORNING, history)

        assertEquals(
            listOf(HomeMixKind.CONTEXT_MORNING, HomeMixKind.REPLAY),
            mixes.map { it.kind },
        )
    }

    @Test
    fun `outside the morning and evening slots the row is untouched`() {
        val history = buildForYouMixes((1..5).map { stat("r$it") }, emptyList(), now)
        val songs = (1..5).map { stat("s$it").toSong() }

        assertEquals(history, withContextMix(songs, null, history))
        assertEquals(history, withContextMix(songs, DayBucket.DAY, history))
        assertEquals(history, withContextMix(songs, DayBucket.NIGHT, history))
    }

    @Test
    fun `a too small context mix is not offered`() {
        val history = buildForYouMixes((1..5).map { stat("r$it") }, emptyList(), now)
        val songs = (1..HOME_MIX_MIN_SONGS - 1).map { stat("s$it").toSong() }

        assertEquals(history, withContextMix(songs, DayBucket.EVENING, history))
    }
}
