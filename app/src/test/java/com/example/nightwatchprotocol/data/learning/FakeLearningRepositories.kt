package com.example.nightwatchprotocol.data.learning

import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import java.time.Instant

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

    /**
     * The server's clock, to date what is written through the repository (`completed_at`, `taken_at`). Entries added to the
     * sets above directly have no date, and with no clock set (the default) nothing is dated. Tests that need a history for the
     * streak set a clock and move it.
     */
    var clock: (() -> Instant)? = null
    val lessonTimes = mutableMapOf<LearningTopic, Instant>()
    val labTimes = mutableMapOf<LearningTopic, Instant>()
    private val attemptTimesByIndex = mutableMapOf<Int, Instant>()

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
        val attempts = examAttempts.mapIndexed { index, count ->
            ExamAttempt(count, count >= EXAM_QUESTION_COUNT - EXAM_MAX_ERRORS, takenAt = attemptTimesByIndex[index])
        }
        return LearningSnapshot(
            viewedLessons = viewedLessons.toSet(),
            completedLabs = completedLabs.toSet(),
            exam = ExamStatus(
                hasPassed = examAttempts.any { it >= EXAM_QUESTION_COUNT - EXAM_MAX_ERRORS },
                lastAttempt = last?.let { ExamAttempt(it, it >= EXAM_QUESTION_COUNT - EXAM_MAX_ERRORS, takenAt = attemptTimesByIndex[examAttempts.lastIndex]) },
            ),
            lessonDates = lessonTimes.toMap(),
            labDates = labTimes.toMap(),
            // Newest first, as the real repository returns them.
            examAttempts = attempts.asReversed(),
        )
    }

    override suspend fun markLessonViewed(topic: LearningTopic) {
        writeCallCount++
        writeFailure?.let { throw DataAccessException(it) }
        viewedLessons += topic
        clock?.invoke()?.let { lessonTimes.putIfAbsent(topic, it) }
    }

    override suspend fun markLabCompleted(lab: LearningTopic) {
        writeCallCount++
        writeFailure?.let { throw DataAccessException(it) }
        completedLabs += lab
        clock?.invoke()?.let { labTimes.putIfAbsent(lab, it) }
    }

    override suspend fun recordExamAttempt(correctCount: Int) {
        writeCallCount++
        require(correctCount in 0..EXAM_QUESTION_COUNT)
        writeFailure?.let { throw DataAccessException(it) }
        examAttempts += correctCount
        clock?.invoke()?.let { attemptTimesByIndex[examAttempts.lastIndex] = it }
    }
}
