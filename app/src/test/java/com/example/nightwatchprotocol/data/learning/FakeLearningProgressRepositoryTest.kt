package com.example.nightwatchprotocol.data.learning

import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The fake is what the view models of the next stages are tested with, so its contract is tested too. */
class FakeLearningProgressRepositoryTest {
    private val repository = FakeLearningProgressRepository()

    private suspend fun failureOf(block: suspend () -> Unit): FailureKind? = try {
        block()
        null
    } catch (e: DataAccessException) {
        e.kind
    }

    @Test
    fun aNewRepository_hasNothing() = runTest {
        assertEquals(LearningSnapshot.Empty, repository.getSnapshot())
    }

    @Test
    fun savedLessonsAndLabs_comeBackInTheSnapshot() = runTest {
        repository.markLessonViewed(LearningTopic.PHISHING)
        repository.markLabCompleted(LearningTopic.CONTRASENAS)

        val snapshot = repository.getSnapshot()

        assertEquals(setOf(LearningTopic.PHISHING), snapshot.viewedLessons)
        assertEquals(setOf(LearningTopic.CONTRASENAS), snapshot.completedLabs)
    }

    @Test
    fun repeatingALessonOrALab_isASuccess_andSavesItOnce() = runTest {
        repository.markLessonViewed(LearningTopic.MALWARE)
        repository.markLessonViewed(LearningTopic.MALWARE)
        repository.markLabCompleted(LearningTopic.MALWARE)
        repository.markLabCompleted(LearningTopic.MALWARE)

        val snapshot = repository.getSnapshot()

        assertEquals(setOf(LearningTopic.MALWARE), snapshot.viewedLessons)
        assertEquals(setOf(LearningTopic.MALWARE), snapshot.completedLabs)
        assertEquals(4, repository.writeCallCount)
    }

    @Test
    fun everyExamAttempt_isAdded_andTheStatusFollowsTheRule() = runTest {
        repository.recordExamAttempt(4)
        assertFalse(repository.getSnapshot().exam.hasPassed)
        assertEquals(ExamAttempt(4, passed = false, takenAt = null), repository.getSnapshot().exam.lastAttempt)

        repository.recordExamAttempt(5)
        assertTrue(repository.getSnapshot().exam.hasPassed)

        repository.recordExamAttempt(2)
        val exam = repository.getSnapshot().exam
        assertTrue("aprobó alguna vez", exam.hasPassed)
        assertEquals("el último intento es el más reciente", ExamAttempt(2, passed = false, takenAt = null), exam.lastAttempt)
        assertEquals(listOf(4, 5, 2), repository.examAttempts)
    }

    @Test
    fun anExamWithoutAttempts_hasNoLastAttempt() = runTest {
        assertNull(repository.getSnapshot().exam.lastAttempt)
        assertEquals(ExamStatus.NotTaken, repository.getSnapshot().exam)
    }

    @Test
    fun readFailures_areReportedByKind() = runTest {
        FailureKind.entries.forEach { kind ->
            repository.readFailure = kind

            assertEquals(kind, failureOf { repository.getSnapshot() })
        }
    }

    @Test
    fun writeFailures_areReportedByKind_andSaveNothing() = runTest {
        FailureKind.entries.forEach { kind ->
            repository.writeFailure = kind

            assertEquals(kind, failureOf { repository.markLessonViewed(LearningTopic.PHISHING) })
            assertEquals(kind, failureOf { repository.markLabCompleted(LearningTopic.PHISHING) })
            assertEquals(kind, failureOf { repository.recordExamAttempt(6) })
        }

        repository.writeFailure = null
        assertEquals(LearningSnapshot.Empty, repository.getSnapshot())
    }

    @Test
    fun aWriteFailure_doesNotBlockReads_andAReadFailure_doesNotBlockWrites() = runTest {
        repository.writeFailure = FailureKind.Offline
        repository.getSnapshot()

        repository.writeFailure = null
        repository.readFailure = FailureKind.Offline
        repository.markLessonViewed(LearningTopic.PHISHING)

        assertEquals(setOf(LearningTopic.PHISHING), repository.viewedLessons)
    }

    @Test
    fun anExamAttemptOutOfRange_isRejected() = runTest {
        try {
            repository.recordExamAttempt(7)
            fail("debía lanzar")
        } catch (e: IllegalArgumentException) {
            // expected, like the real repository
        }
        assertTrue(repository.examAttempts.isEmpty())
    }

    @Test
    fun theFakeContent_failsOnDemand_andCountsTheReads() = runTest {
        val content = FakeLearningContentRepository()
        content.getContent()
        content.failure = IllegalStateException("asset dañado")

        try {
            content.getContent()
            fail("debía lanzar")
        } catch (e: IllegalStateException) {
            // expected
        }
        assertEquals(2, content.getContentCallCount)
    }

    @Test
    fun theSampleContent_isValid() {
        val content = sampleLearningContent()

        assertEquals(6, content.lessons.size)
        assertEquals(6, content.labs.size)
        assertEquals(6, content.exam.size)
        content.labs.forEach { assertEquals(3, it.objectives.size) }
    }
}
