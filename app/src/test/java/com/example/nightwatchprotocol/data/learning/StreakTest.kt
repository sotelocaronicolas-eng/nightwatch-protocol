package com.example.nightwatchprotocol.data.learning

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class StreakTest {
    // Bogotá is UTC-5 all year: 23:30 there is already 04:30 of the next day in UTC.
    private val bogota = ZoneId.of("America/Bogota")
    private val today = LocalDate.of(2026, 10, 18)

    private fun at(date: LocalDate, time: String = "12:00", zone: ZoneId = bogota): Instant =
        LocalDateTime.parse("${date}T$time").atZone(zone).toInstant()

    private fun day(offset: Long) = today.minusDays(offset)

    private fun streak(vararg moments: Instant, now: Instant = at(today, "15:00")) = computeStreak(moments.toList(), now, bogota)

    // ---- basic shapes ----

    @Test
    fun noActivity_isZeroAndZero() {
        assertEquals(Streak(0, 0), streak())
    }

    @Test
    fun oneDayToday_isOne() {
        assertEquals(Streak(1, 1), streak(at(today)))
    }

    @Test
    fun severalMomentsTheSameDay_areOneDay() {
        assertEquals(Streak(1, 1), streak(at(today, "08:00"), at(today, "13:00"), at(today, "22:59")))
    }

    @Test
    fun consecutiveDaysEndingToday_countAll() {
        assertEquals(Streak(3, 3), streak(at(day(2)), at(day(1)), at(today)))
    }

    @Test
    fun aGap_restartsTheCurrentStreak_andKeepsTheBestOne() {
        // Three days in a row, a hole, and today only.
        assertEquals(Streak(1, 3), streak(at(day(5)), at(day(4)), at(day(3)), at(today)))
    }

    @Test
    fun theBestStreak_canBeLongerThanTheCurrentOne() {
        val old = (10L..14L).map { at(day(it)) }
        assertEquals(Streak(2, 5), computeStreak(old + at(day(1)) + at(today), at(today, "15:00"), bogota))
    }

    @Test
    fun theOrderOfTheMoments_doesNotMatter() {
        assertEquals(Streak(3, 3), streak(at(today), at(day(2)), at(day(1))))
    }

    // ---- today without activity ----

    @Test
    fun todayWithoutActivityButYesterdayWith_theStreakIsStillAlive() {
        assertEquals(Streak(2, 2), streak(at(day(2)), at(day(1))))
    }

    @Test
    fun onlyYesterday_isOneAndAlive() {
        assertEquals(Streak(1, 1), streak(at(day(1))))
    }

    @Test
    fun lastDayBeforeYesterday_theCurrentStreakIsZero_butTheBestIsKept() {
        assertEquals(Streak(0, 3), streak(at(day(4)), at(day(3)), at(day(2))))
    }

    @Test
    fun aSingleOldDay_isZeroCurrent_oneBest() {
        assertEquals(Streak(0, 1), streak(at(day(30))))
    }

    // ---- time zone ----

    @Test
    fun aMomentAt2330Local_countsForTheLocalDay_notTheUtcOne() {
        val lateDay1 = at(day(1), "23:30")
        // In UTC it is already day(0) at 04:30.
        assertEquals(LocalDate.of(2026, 10, 18), lateDay1.atZone(ZoneOffset.UTC).toLocalDate())

        // Local day(1) at 23:30 and local today at 10:00 are two consecutive days.
        assertEquals(Streak(2, 2), streak(lateDay1, at(today, "10:00")))
        // Counted in UTC both would be the same day: this is why the conversion is always done first.
        assertEquals(Streak(1, 1), computeStreak(listOf(lateDay1, at(today, "10:00")), at(today, "15:00"), ZoneOffset.UTC))
    }

    @Test
    fun todayIsTheLocalDay_evenWhenUtcAlreadyChanged() {
        // 23:30 local on the 18th is the 19th in UTC. The streak that ended on the 18th is still "today".
        val now = at(today, "23:30")
        assertEquals(Streak(1, 1), computeStreak(listOf(at(today, "09:00")), now, bogota))
        // The same moments read in UTC would make that day "yesterday" and still alive: the local reading is the one used.
        assertEquals(Streak(1, 1), computeStreak(listOf(at(today, "09:00")), now, ZoneOffset.UTC))
    }

    @Test
    fun midnightLocal_startsANewDay() {
        assertEquals(Streak(2, 2), streak(at(day(1), "23:59"), at(today, "00:00")))
    }

    @Test
    fun theSameMomentsInTwoZones_canGiveDifferentDays() {
        val moments = listOf(at(day(1), "23:30"), at(day(1), "02:00"))
        // Local: one day. UTC: 04:30 of day(0) and 07:00 of day(1): two consecutive days ending today... still different counts.
        assertEquals(1, computeStreak(moments, at(today, "15:00"), bogota).best)
        assertEquals(2, computeStreak(moments, at(today, "15:00"), ZoneOffset.UTC).best)
    }

    // ---- robustness ----

    @Test
    fun aMomentAfterToday_isTheLastDay_andNeverBreaksTheCount() {
        // A wrong device clock: yesterday, today and "tomorrow" are three consecutive days.
        assertEquals(Streak(3, 3), streak(at(day(1)), at(today), at(today.plusDays(1))))
    }

    @Test
    fun thePracticeMoments_areTheLessonsLabsAndAttemptsOfTheSnapshot() {
        val snapshot = LearningSnapshot(
            viewedLessons = setOf(LearningTopic.CONTRASENAS),
            completedLabs = setOf(LearningTopic.CONTRASENAS),
            exam = ExamStatus.NotTaken,
            lessonDates = mapOf(LearningTopic.CONTRASENAS to at(day(2))),
            labDates = mapOf(LearningTopic.CONTRASENAS to at(day(1))),
            examAttempts = listOf(ExamAttempt(6, true, at(today)), ExamAttempt(2, false, takenAt = null)),
        )

        val moments = practiceMoments(snapshot)

        assertEquals(setOf(at(day(2)), at(day(1)), at(today)), moments.toSet())
        assertEquals(Streak(3, 3), computeStreak(moments, at(today, "15:00"), bogota))
    }
}
