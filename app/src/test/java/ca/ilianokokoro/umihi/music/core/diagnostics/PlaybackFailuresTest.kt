package ca.ilianokokoro.umihi.music.core.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureRulesTest {
    private val now = 1_000_000L

    private fun refused(ageMs: Long = 0) = PlaybackFailure(
        timeMs = now - ageMs,
        source = FailureSource.AndroidVr,
        code = "LOGIN_REQUIRED",
        detail = "Sign in to confirm you are not a bot",
    )

    private fun classify(
        hasNetwork: Boolean = true,
        isNetworkError: Boolean = false,
        errorCode: String = "ERROR_CODE_UNSPECIFIED",
        recent: List<PlaybackFailure> = emptyList(),
    ) = FailureRules.classify(hasNetwork, isNetworkError, errorCode, recent, now)

    @Test
    fun `no connection explains everything else`() {
        assertEquals(FailureKind.Network, classify(hasNetwork = false, recent = listOf(refused())))
        assertEquals(FailureKind.Network, classify(isNetworkError = true))
    }

    @Test
    fun `a refused stream counts as refused`() {
        assertEquals(FailureKind.RefusedBySource, classify(errorCode = "ERROR_CODE_IO_BAD_HTTP_STATUS"))
    }

    @Test
    fun `a client's recent refusal explains the failure after it`() {
        assertEquals(FailureKind.RefusedBySource, classify(recent = listOf(refused())))
    }

    @Test
    fun `an old refusal explains nothing`() {
        val old = refused(ageMs = FailureRules.RECENT_MS + 1)
        assertEquals(FailureKind.Other, classify(recent = listOf(old)))
    }

    @Test
    fun `a past player failure is not a reason`() {
        val past = PlaybackFailure(now, FailureSource.Player, "ERROR_CODE_IO_BAD_HTTP_STATUS")
        assertEquals(FailureKind.Other, classify(recent = listOf(past)))
    }

    @Test
    fun `an unknown failure stays unknown`() {
        assertEquals(FailureKind.Other, classify())
    }

    @Test
    fun `stream addresses never reach the log`() {
        val clean = FailureRules.sanitize(
            "Response code: 403 https://rr3.googlevideo.com/videoplayback?expire=1&ip=1.2.3.4&sig=abc"
        )
        assertEquals("Response code: 403 <url>", clean)
        assertFalse(clean.contains("1.2.3.4"))
    }

    @Test
    fun `leftover query strings are dropped`() {
        assertFalse(FailureRules.sanitize("failed for path?ip=1.2.3.4&sig=abc").contains("1.2.3.4"))
    }

    @Test
    fun `track ids never reach the log`() {
        assertEquals("<id> : Fatal fail", FailureRules.sanitize("dQw4w9WgXcQ : Fatal fail"))
        assertFalse(FailureRules.sanitize("watch v=dQw4w9WgXcQ").contains("dQw4w9WgXcQ"))
    }

    @Test
    fun `details stay short and on one line`() {
        val clean = FailureRules.sanitize("a\n\n  b " + "x".repeat(500))
        assertTrue(clean.startsWith("a b "))
        assertTrue(clean.length <= 160)
    }

    @Test
    fun `nothing to sanitize is nothing`() {
        assertEquals("", FailureRules.sanitize(null))
    }

    @Test
    fun `the summary counts each client and shows the latest`() {
        val summary = FailureRules.summary(
            listOf(
                PlaybackFailure(0, FailureSource.AndroidVr, "LOGIN_REQUIRED", "Sign in"),
                PlaybackFailure(1000, FailureSource.AndroidVr, "UNPLAYABLE"),
                PlaybackFailure(2000, FailureSource.Player, "ERROR_CODE_TIMEOUT"),
            )
        )
        assertTrue(summary, summary.contains("Playback failures: 3 recorded"))
        assertTrue(summary, summary.contains("AndroidVr: 2, last 1970-01-01T00:00:01Z UNPLAYABLE"))
        assertTrue(summary, summary.contains("Player: 1"))
        assertFalse(summary, summary.contains("NewPipe"))
        assertTrue(summary, summary.contains("LOGIN_REQUIRED - Sign in"))
    }
}

class PlaybackFailuresTest {
    @After
    fun tearDown() {
        PlaybackFailures.clear()
    }

    @Test
    fun `records what it is told, sanitized`() {
        PlaybackFailures.record(FailureSource.NewPipe, "IOException", "at https://x.example/a?b=c", nowMs = 5)
        val failure = PlaybackFailures.recent().single()
        assertEquals(5L, failure.timeMs)
        assertEquals("at <url>", failure.detail)
    }

    @Test
    fun `keeps only the latest`() {
        repeat(PlaybackFailures.MAX_ENTRIES + 10) {
            PlaybackFailures.record(FailureSource.Player, "code$it")
        }
        val recent = PlaybackFailures.recent()
        assertEquals(PlaybackFailures.MAX_ENTRIES, recent.size)
        assertEquals("code${PlaybackFailures.MAX_ENTRIES + 9}", recent.last().code)
    }
}
