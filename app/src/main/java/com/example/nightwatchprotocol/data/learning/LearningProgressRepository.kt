package com.example.nightwatchprotocol.data.learning

import java.time.Instant

/** One try of the final exam, as stored in `exam_attempts`. [takenAt] is the server's clock (null if it could not be read). */
data class ExamAttempt(val correctCount: Int, val passed: Boolean, val takenAt: Instant?)

/** [hasPassed]: at least one attempt passed, ever. [lastAttempt]: the most recent one, or null if there is none. */
data class ExamStatus(val hasPassed: Boolean, val lastAttempt: ExamAttempt?) {
    companion object {
        val NotTaken = ExamStatus(hasPassed = false, lastAttempt = null)
    }
}

/**
 * Everything saved about the signed-in person's progress. [lessonDates] and [labDates] are the server's `completed_at` of
 * each saved row and [examAttempts] is every attempt (newest first) with `taken_at` and `correct_count`: they feed the
 * streak and the badges. An entry without a readable date is simply missing from the maps / has a null `takenAt`.
 */
data class LearningSnapshot(
    val viewedLessons: Set<LearningTopic>,
    val completedLabs: Set<LearningTopic>,
    val exam: ExamStatus,
    val lessonDates: Map<LearningTopic, Instant> = emptyMap(),
    val labDates: Map<LearningTopic, Instant> = emptyMap(),
    val examAttempts: List<ExamAttempt> = emptyList(),
) {
    companion object {
        val Empty = LearningSnapshot(emptySet(), emptySet(), ExamStatus.NotTaken)
    }
}

/**
 * The progress of the signed-in person. The row owner is always the session's: no function takes a user id.
 * Failures are [com.example.nightwatchprotocol.data.common.DataAccessException].
 *
 * Writing something already saved (a lesson seen again, a lab repeated) is a success, not an error: there is no update.
 */
interface LearningProgressRepository {
    suspend fun getSnapshot(): LearningSnapshot

    suspend fun markLessonViewed(topic: LearningTopic)

    /** Only call this when the three objectives were done (decision P6): an incomplete lab is never saved. */
    suspend fun markLabCompleted(lab: LearningTopic)

    /** Stores only [correctCount] (0 to [EXAM_QUESTION_COUNT]); the server decides `passed`. Every call adds an attempt. */
    suspend fun recordExamAttempt(correctCount: Int)
}
