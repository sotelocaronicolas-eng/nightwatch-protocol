package com.example.nightwatchprotocol.data.social

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The countdown is the server's number of seconds, counted down with a monotonic clock: never the phone's date. */
class ServerCountdownTest {
    private val start = 7_000_000_000L

    private fun nanosAfter(millis: Long) = start + millis * 1_000_000L

    @Test
    fun atTheMomentOfReceipt_theWholeTimeIsLeft() {
        val countdown = ServerCountdown(60.0, start)

        assertEquals(60_000L, countdown.remainingMillis(start))
        assertEquals(60, countdown.remainingSeconds(start))
    }

    @Test
    fun itCountsDownWithTheMonotonicClock() {
        val countdown = ServerCountdown(60.0, start)

        assertEquals(45_000L, countdown.remainingMillis(nanosAfter(15_000)))
        assertEquals(45, countdown.remainingSeconds(nanosAfter(15_000)))
        assertEquals(1_000L, countdown.remainingMillis(nanosAfter(59_000)))
    }

    @Test
    fun secondsAreRoundedUp_soTheCountdownShowsOneUntilItIsReallyOver() {
        val countdown = ServerCountdown(10.0, start)

        assertEquals(10, countdown.remainingSeconds(nanosAfter(1)))
        assertEquals(1, countdown.remainingSeconds(nanosAfter(9_999)))
        assertEquals(0, countdown.remainingSeconds(nanosAfter(10_000)))
    }

    @Test
    fun itNeverGoesBelowZero_andNeverDecidesTheTimeRanOut() {
        val countdown = ServerCountdown(5.0, start)

        assertEquals(0L, countdown.remainingMillis(nanosAfter(5_000)))
        assertEquals(0L, countdown.remainingMillis(nanosAfter(3_600_000)))
        assertEquals(0, countdown.remainingSeconds(nanosAfter(3_600_000)))
        // A countdown at zero changes nothing in the server: the turn passes when the server says so (next sync).
    }

    @Test
    fun decimalsOfTheServer_areKept() {
        val countdown = ServerCountdown(57.4, start)

        assertEquals(57_400L, countdown.remainingMillis(start))
        assertEquals(58, countdown.remainingSeconds(start))
    }

    @Test
    fun aServerWithTwoMinutes_forTheUlti() {
        val countdown = ServerCountdown(120.0, start)

        assertEquals(120_000L, countdown.remainingMillis(start))
        assertEquals(60_000L, countdown.remainingMillis(nanosAfter(60_000)))
    }

    @Test
    fun theSystemClock_isMonotonic() {
        val first = SystemMonotonicClock.nanoTime()
        val second = SystemMonotonicClock.nanoTime()

        assertTrue(second >= first)
    }
}
