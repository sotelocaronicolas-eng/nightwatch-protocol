package com.example.nightwatchprotocol.data.learning

import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind

class FakeLearningContentRepository(
    var content: LearningContent = sampleLearningContent(),
) : LearningContentRepository {
    /** When set, [getContent] fails with it (the real one would only fail on a corrupt asset). */
    var failure: Throwable? = null
    var getContentCallCount = 0

    override suspend fun getContent(): LearningContent {
        getContentCallCount++
        failure?.let { throw it }
        return content
    }
}

/**
 * In-memory progress that behaves like the real one: repeating a lesson or a lab is a success, every exam call adds an
 * attempt, and `passed` is `correctCount >= 5`. Failures are configurable per kind (offline, permission denied, unknown),
 * separately for reads and writes.
 */
class FakeLearningProgressRepository : LearningProgressRepository {
    val viewedLessons = mutableSetOf<LearningTopic>()
    val completedLabs = mutableSetOf<LearningTopic>()
    val examAttempts = mutableListOf<Int>()

    /** When not null, [getSnapshot] fails with this kind. */
    var readFailure: FailureKind? = null

    /** When not null, every write fails with this kind and saves nothing. */
    var writeFailure: FailureKind? = null

    var getSnapshotCallCount = 0
    var writeCallCount = 0

    override suspend fun getSnapshot(): LearningSnapshot {
        getSnapshotCallCount++
        readFailure?.let { throw DataAccessException(it) }
        val last = examAttempts.lastOrNull()
        return LearningSnapshot(
            viewedLessons = viewedLessons.toSet(),
            completedLabs = completedLabs.toSet(),
            exam = ExamStatus(
                hasPassed = examAttempts.any { it >= EXAM_QUESTION_COUNT - EXAM_MAX_ERRORS },
                lastAttempt = last?.let { ExamAttempt(it, it >= EXAM_QUESTION_COUNT - EXAM_MAX_ERRORS, takenAt = null) },
            ),
        )
    }

    override suspend fun markLessonViewed(topic: LearningTopic) {
        writeCallCount++
        writeFailure?.let { throw DataAccessException(it) }
        viewedLessons += topic
    }

    override suspend fun markLabCompleted(lab: LearningTopic) {
        writeCallCount++
        writeFailure?.let { throw DataAccessException(it) }
        completedLabs += lab
    }

    override suspend fun recordExamAttempt(correctCount: Int) {
        writeCallCount++
        require(correctCount in 0..EXAM_QUESTION_COUNT)
        writeFailure?.let { throw DataAccessException(it) }
        examAttempts += correctCount
    }
}
