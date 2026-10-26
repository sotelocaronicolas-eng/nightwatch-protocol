package com.example.nightwatchprotocol.data.learning

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the own social profile takes from the progress: "Ya entrenaste hoy" and the two counters. */
class ProgressSummaryOwnProfileTest {
    private val zone = ZoneId.of("America/Bogota")
    private val now = Instant.parse("2026-10-25T15:00:00Z") // 10:00 in Bogotá

    private fun snapshot(vararg lessons: Pair<LearningTopic, String>, labs: Map<LearningTopic, String> = emptyMap()) = LearningSnapshot(
        viewedLessons = lessons.map { it.first }.toSet(),
        completedLabs = labs.keys,
        exam = ExamStatus.NotTaken,
        lessonDates = lessons.associate { it.first to Instant.parse(it.second) },
        labDates = labs.mapValues { Instant.parse(it.value) },
    )

    @Test
    fun aLessonSeenToday_isATrainedDay() {
        val summary = summarizeProgress(snapshot(LearningTopic.CONTRASENAS to "2026-10-25T13:00:00Z"), now, zone)

        assertTrue(summary.trainedToday)
    }

    @Test
    fun aLabDoneToday_isATrainedDay() {
        val summary = summarizeProgress(
            snapshot(labs = mapOf(LearningTopic.PHISHING to "2026-10-25T12:00:00Z")),
            now,
            zone,
        )

        assertTrue(summary.trainedToday)
    }

    @Test
    fun onlyYesterday_isNotATrainedDay() {
        val summary = summarizeProgress(snapshot(LearningTopic.CONTRASENAS to "2026-10-24T15:00:00Z"), now, zone)

        assertFalse(summary.trainedToday)
    }

    @Test
    fun theDayIsTheOneOfTheZone_notOfUtc() {
        // 03:00 UTC on the 25th is still the 24th in Bogotá (22:00).
        val summary = summarizeProgress(snapshot(LearningTopic.CONTRASENAS to "2026-10-25T03:00:00Z"), now, zone)

        assertFalse(summary.trainedToday)
    }

    @Test
    fun withoutProgress_nothingIsTrainedAndTheCountersAreZero() {
        val summary = summarizeProgress(LearningSnapshot.Empty, now, zone)

        assertFalse(summary.trainedToday)
        assertEquals(0, summary.viewedLessonCount)
        assertEquals(0, summary.completedLabCount)
    }

    @Test
    fun theCounters_areTheLessonsSeenAndTheLabsCompleted() {
        val summary = summarizeProgress(
            snapshot(
                LearningTopic.CONTRASENAS to "2026-10-20T13:00:00Z",
                LearningTopic.PHISHING to "2026-10-21T13:00:00Z",
                labs = mapOf(LearningTopic.CONTRASENAS to "2026-10-22T13:00:00Z"),
            ),
            now,
            zone,
        )

        assertEquals(2, summary.viewedLessonCount)
        assertEquals(1, summary.completedLabCount)
    }
}
