package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.learning.LearningTopic
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The mapping of the rows of migration 0007 to dates and exam history, without network. */
class LearningProgressDatesTest {

    @Test
    fun theServerTimestamp_withMicrosecondsAndUtc_becomesTheSameInstant() {
        assertEquals(Instant.parse("2026-10-18T15:30:00.123456Z"), parseServerInstant("2026-10-18T15:30:00.123456+00:00"))
    }

    @Test
    fun aTimestampWithAnotherOffset_isConvertedToUtc() {
        // 23:30 at UTC-5 is 04:30 of the next day in UTC: the streak converts it to the local date afterwards.
        assertEquals(Instant.parse("2026-10-19T04:30:00Z"), parseServerInstant("2026-10-18T23:30:00-05:00"))
    }

    @Test
    fun anUnreadableOrMissingTimestamp_isNull() {
        assertNull(parseServerInstant(null))
        assertNull(parseServerInstant(""))
        assertNull(parseServerInstant("ayer por la tarde"))
    }

    @Test
    fun lessonDates_mapEachTopicToItsCompletedAt() {
        val rows = listOf(
            LessonProgressRow("u", "CONTRASENAS", "2026-10-16T10:00:00+00:00"),
            LessonProgressRow("u", "PHISHING", "2026-10-17T10:00:00+00:00"),
        )

        assertEquals(
            mapOf(
                LearningTopic.CONTRASENAS to Instant.parse("2026-10-16T10:00:00Z"),
                LearningTopic.PHISHING to Instant.parse("2026-10-17T10:00:00Z"),
            ),
            rows.toLessonDates(),
        )
    }

    @Test
    fun lessonDates_dropUnknownTopicsAndUnreadableDates_butTheLessonStaysViewed() {
        val rows = listOf(
            LessonProgressRow("u", "TEMA_NUEVO", "2026-10-16T10:00:00+00:00"),
            LessonProgressRow("u", "MALWARE", "no es una fecha"),
            LessonProgressRow("u", "FIREWALL", null),
        )

        assertTrue(rows.toLessonDates().isEmpty())
        assertEquals(setOf(LearningTopic.MALWARE, LearningTopic.FIREWALL), rows.toViewedLessons())
    }

    @Test
    fun labDates_mapEachLabToItsCompletedAt_andDropWhatCannotBeRead() {
        val rows = listOf(
            LabProgressRow("u", "REDES_SEGURAS", "2026-10-18T01:00:00+00:00"),
            LabProgressRow("u", "OTRO", "2026-10-18T01:00:00+00:00"),
            LabProgressRow("u", "FIREWALL", null),
        )

        assertEquals(mapOf(LearningTopic.REDES_SEGURAS to Instant.parse("2026-10-18T01:00:00Z")), rows.toLabDates())
        assertEquals(setOf(LearningTopic.REDES_SEGURAS, LearningTopic.FIREWALL), rows.toCompletedLabs())
    }

    @Test
    fun anExamAttemptRow_keepsItsCountItsVerdictAndItsDate() {
        val attempt = ExamAttemptRow(id = 7, userId = "u", correctCount = 5, passed = true, takenAt = "2026-10-18T12:00:00+00:00").toDomain()

        assertEquals(5, attempt.correctCount)
        assertTrue(attempt.passed)
        assertEquals(Instant.parse("2026-10-18T12:00:00Z"), attempt.takenAt)
    }

    @Test
    fun theExamStatus_isPassedIfAnyAttemptPassed_andLastIsTheFirstRow() {
        val rows = listOf(
            ExamAttemptRow(3, "u", 2, false, "2026-10-18T12:00:00+00:00"),
            ExamAttemptRow(2, "u", 6, true, "2026-10-17T12:00:00+00:00"),
        )

        val status = rows.toExamStatus()

        assertTrue(status.hasPassed)
        assertEquals(2, status.lastAttempt?.correctCount)
        assertFalse(status.lastAttempt!!.passed)
    }

    @Test
    fun noAttempts_isNotPassedAndHasNoLastAttempt() {
        val status = emptyList<ExamAttemptRow>().toExamStatus()

        assertFalse(status.hasPassed)
        assertNull(status.lastAttempt)
    }
}
