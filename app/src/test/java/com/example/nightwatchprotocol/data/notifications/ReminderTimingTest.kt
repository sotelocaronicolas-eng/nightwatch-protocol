package com.example.nightwatchprotocol.data.notifications

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTimingTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val hour = 60 * 60 * 1000L

    private fun at(year: Int, month: Int, day: Int, h: Int, m: Int = 0, s: Int = 0): Long =
        Calendar.getInstance(utc).apply { clear(); set(year, month, day, h, m, s) }.timeInMillis

    @Test
    fun beforeTheTargetTime_itIsLaterToday() {
        val now = at(2026, Calendar.OCTOBER, 10, 15)

        assertEquals(4 * hour, millisUntilNext(now, 19, 0, utc))
    }

    @Test
    fun afterTheTargetTime_itIsTomorrow() {
        val now = at(2026, Calendar.OCTOBER, 10, 20)

        assertEquals(23 * hour, millisUntilNext(now, 19, 0, utc))
    }

    @Test
    fun exactlyAtTheTargetTime_itIsTomorrowNotNow() {
        val now = at(2026, Calendar.OCTOBER, 10, 19)

        assertEquals(24 * hour, millisUntilNext(now, 19, 0, utc))
    }

    @Test
    fun theDelayIsAlwaysPositiveAndNeverMoreThanADay() {
        for (h in 0..23) {
            val delay = millisUntilNext(at(2026, Calendar.OCTOBER, 10, h, 30, 15), 19, 0, utc)
            assertTrue(delay > 0)
            assertTrue(delay <= 24 * hour)
        }
    }

    @Test
    fun crossingTheEndOfTheMonth_stillFindsTheNextDay() {
        val now = at(2026, Calendar.OCTOBER, 31, 22)

        assertEquals(21 * hour, millisUntilNext(now, 19, 0, utc))
    }

    @Test
    fun theTimeZoneDecidesTheWallClock() {
        val bogota = TimeZone.getTimeZone("America/Bogota") // UTC-5, no daylight saving
        val now = at(2026, Calendar.OCTOBER, 10, 23) // 18:00 in Bogota

        assertEquals(1 * hour, millisUntilNext(now, 19, 0, bogota))
    }

    @Test
    fun theRemindersUseTheDesignTimes() {
        assertEquals(19, ReminderKind.DAILY_PRACTICE.hour)
        assertEquals(21, ReminderKind.STREAK_AT_RISK.hour)
    }
}
