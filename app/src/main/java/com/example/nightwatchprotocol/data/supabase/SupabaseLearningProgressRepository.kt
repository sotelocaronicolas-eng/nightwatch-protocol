package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.learning.EXAM_QUESTION_COUNT
import com.example.nightwatchprotocol.data.learning.ExamAttempt
import com.example.nightwatchprotocol.data.learning.ExamStatus
import com.example.nightwatchprotocol.data.learning.LearningProgressRepository
import com.example.nightwatchprotocol.data.learning.LearningSnapshot
import com.example.nightwatchprotocol.data.learning.LearningTopic
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.query.Order
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Rows of migration 0007. The INSERT rows (`...Insert`) are separate classes with ONLY the columns `authenticated` may
 * insert: `completed_at`, `taken_at`, `id` and `passed` belong to the server (a column-level GRANT would reject them),
 * and the read rows carry every column the server returns. No property has a default value on purpose: supabase-kt does
 * not send properties that equal their default (see PlacementRow).
 */

@Serializable
data class LessonProgressInsert(@SerialName("user_id") val userId: String, val topic: String)

@Serializable
data class LabProgressInsert(@SerialName("user_id") val userId: String, val lab: String)

@Serializable
data class ExamAttemptInsert(
    @SerialName("user_id") val userId: String,
    @SerialName("correct_count") val correctCount: Int,
)

@Serializable
data class LessonProgressRow(
    @SerialName("user_id") val userId: String? = null,
    val topic: String,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class LabProgressRow(
    @SerialName("user_id") val userId: String? = null,
    val lab: String,
    @SerialName("completed_at") val completedAt: String? = null,
)

@Serializable
data class ExamAttemptRow(
    val id: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("correct_count") val correctCount: Int,
    val passed: Boolean,
    @SerialName("taken_at") val takenAt: String? = null,
)

/** Unknown topic names (a newer database) are dropped, like unknown categories in the quiz. */
fun List<LessonProgressRow>.toViewedLessons(): Set<LearningTopic> =
    mapNotNull { LearningTopic.fromName(it.topic) }.toSet()

fun List<LabProgressRow>.toCompletedLabs(): Set<LearningTopic> =
    mapNotNull { LearningTopic.fromName(it.lab) }.toSet()

/** The server answers a timestamptz such as `2026-10-18T15:30:00.123456+00:00`; null if it cannot be read. */
fun parseServerInstant(text: String?): Instant? =
    text?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

fun ExamAttemptRow.toDomain(): ExamAttempt = ExamAttempt(
    correctCount = correctCount,
    passed = passed,
    takenAt = parseServerInstant(takenAt),
)

/** `completed_at` of each saved lesson, as an instant. A row with an unknown topic or an unreadable date is dropped. */
fun List<LessonProgressRow>.toLessonDates(): Map<LearningTopic, Instant> =
    mapNotNull { row ->
        val topic = LearningTopic.fromName(row.topic) ?: return@mapNotNull null
        parseServerInstant(row.completedAt)?.let { topic to it }
    }.toMap()

fun List<LabProgressRow>.toLabDates(): Map<LearningTopic, Instant> =
    mapNotNull { row ->
        val topic = LearningTopic.fromName(row.lab) ?: return@mapNotNull null
        parseServerInstant(row.completedAt)?.let { topic to it }
    }.toMap()

/** The whole exam history of the person from its rows (newest first, as asked of the server). */
fun List<ExamAttemptRow>.toExamStatus(): ExamStatus =
    ExamStatus(hasPassed = any { it.passed }, lastAttempt = firstOrNull()?.toDomain())

/**
 * Progress in Supabase (tables of migration 0007).
 *
 * The user is always the session's ([requireSessionUserId]); no function receives a user id. A request without a session
 * fails as [com.example.nightwatchprotocol.data.common.FailureKind.PermissionDenied] before reaching the network, and every
 * failure of a call is a `DataAccessException` ([supabaseCall]). The server also filters by RLS: even a wrong id would
 * only see the caller's own rows.
 *
 * IDEMPOTENT WRITES (lesson seen, lab completed). `authenticated` has INSERT (and SELECT) but no UPDATE on these tables, so
 * an UPSERT that updates would be rejected. The write is an upsert that IGNORES duplicates: supabase-kt sends
 * `Prefer: resolution=ignore-duplicates` and `on_conflict=user_id,topic` (or `user_id,lab`), which PostgREST runs as
 * `INSERT ... ON CONFLICT (user_id, topic) DO NOTHING`. That needs only the INSERT privilege, and an existing row is not an
 * error (no 409, no 23505), so repeating a lesson or a lab "succeeds". It is one request and atomic, with no "read then
 * write" race. `columns=user_id,topic` is also sent, so the server never receives `completed_at` (default `now()`).
 * Not tested against a real server from here (CLAUDE.md, F-03): it is checked by hand in the phone (Etapa 3).
 *
 * The exam is a plain INSERT: each attempt is a new row, so there is no conflict to ignore.
 */
class SupabaseLearningProgressRepository(
    private val postgrest: Postgrest,
    private val currentUserId: () -> String?,
) : LearningProgressRepository {

    override suspend fun getSnapshot(): LearningSnapshot {
        val userId = requireSessionUserId(currentUserId)
        return coroutineScope {
            val lessons = async {
                supabaseCall {
                    postgrest.from(LESSONS).select { filter { eq("user_id", userId) } }.decodeList<LessonProgressRow>()
                }
            }
            val labs = async {
                supabaseCall {
                    postgrest.from(LABS).select { filter { eq("user_id", userId) } }.decodeList<LabProgressRow>()
                }
            }
            // Every attempt, newest first: it gives the last one, whether one passed, and the dates of the streak.
            val attempts = async {
                supabaseCall {
                    postgrest.from(EXAMS).select {
                        filter { eq("user_id", userId) }
                        order("taken_at", Order.DESCENDING)
                    }.decodeList<ExamAttemptRow>()
                }
            }
            val lessonRows = lessons.await()
            val labRows = labs.await()
            val attemptRows = attempts.await()
            LearningSnapshot(
                viewedLessons = lessonRows.toViewedLessons(),
                completedLabs = labRows.toCompletedLabs(),
                exam = attemptRows.toExamStatus(),
                lessonDates = lessonRows.toLessonDates(),
                labDates = labRows.toLabDates(),
                examAttempts = attemptRows.map { it.toDomain() },
            )
        }
    }

    override suspend fun markLessonViewed(topic: LearningTopic) {
        val row = LessonProgressInsert(requireSessionUserId(currentUserId), topic.name)
        supabaseCall {
            postgrest.from(LESSONS).upsert(row) {
                onConflict = "user_id,topic"
                ignoreDuplicates = true
            }
        }
    }

    override suspend fun markLabCompleted(lab: LearningTopic) {
        val row = LabProgressInsert(requireSessionUserId(currentUserId), lab.name)
        supabaseCall {
            postgrest.from(LABS).upsert(row) {
                onConflict = "user_id,lab"
                ignoreDuplicates = true
            }
        }
    }

    override suspend fun recordExamAttempt(correctCount: Int) {
        require(correctCount in 0..EXAM_QUESTION_COUNT) { "correctCount fuera de rango: $correctCount" }
        val row = ExamAttemptInsert(requireSessionUserId(currentUserId), correctCount)
        supabaseCall { postgrest.from(EXAMS).insert(row) }
    }

    private companion object {
        const val LESSONS = "lesson_progress"
        const val LABS = "lab_progress"
        const val EXAMS = "exam_attempts"
    }
}
