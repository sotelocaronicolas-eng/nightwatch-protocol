package com.example.nightwatchprotocol.data.learning

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Module 4 (rule R10): the personal streak cuts the day at midnight of Colombia, like the friends' and the pairs' streaks, whatever
 * the zone of the phone is. The calculation itself is the same pure function of module 3; what changed is the zone it is given.
 */
class ColombiaStreakTest {

    @Test
    fun theZoneOfEveryStreak_isColombia() {
        assertEquals(ZoneId.of("America/Bogota"), ColombiaZone)
        assertEquals(ColombiaZone, colombiaClock().zone)
    }

    @Test
    fun aLabDoneAt2330InColombia_countsForThatDay_evenIfItIsAlreadyTomorrowInUtc() {
        // 04:30Z on Oct 23 is 23:30 of Oct 22 in Colombia. Two days later (14:00Z on Oct 24 = 09:00 in Colombia) the last
        // practice day is Oct 22: two days ago, so the current streak is over. Counted in UTC it would be Oct 23 (yesterday).
        val practice = listOf(Instant.parse("2026-10-23T04:30:00Z"))
        val now = Instant.parse("2026-10-24T14:00:00Z")

        assertEquals(Streak(current = 0, best = 1), computeStreak(practice, now, ColombiaZone))
        assertEquals(Streak(current = 1, best = 1), computeStreak(practice, now, ZoneOffset.UTC))
    }

    @Test
    fun aPhoneInAnotherZone_getsTheSameStreakAsOneInColombia() {
        val practice = listOf(
            Instant.parse("2026-10-21T15:00:00Z"),
            Instant.parse("2026-10-22T15:00:00Z"),
        )
        val now = Instant.parse("2026-10-23T03:30:00Z") // 22:30 of Oct 22 in Colombia: still "today" there

        val inColombia = computeStreak(practice, now, ColombiaZone)

        assertEquals(Streak(current = 2, best = 2), inColombia)
        // The provider and the Home card pass the Colombian zone, not the phone's: the same instants give the same answer.
        assertEquals(inColombia, computeStreak(practice, now, colombiaClock().zone))
    }
}
