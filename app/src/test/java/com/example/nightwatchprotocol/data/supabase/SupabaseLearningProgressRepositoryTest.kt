package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.ExamAttempt
import com.example.nightwatchprotocol.data.learning.LearningTopic
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SupabaseLearningProgressRepositoryTest {
    // The serializer Postgrest uses: encodeDefaults is OFF.
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun kindOf(block: suspend () -> Unit): FailureKind? = try {
        block()
        null
    } catch (e: DataAccessException) {
        e.kind
    }

    // ---- what is sent: only the columns that `authenticated` may insert (migration 0007) ----

    @Test
    fun lessonInsert_sendsOnlyUserIdAndTopic() {
        val encoded = json.encodeToJsonElement(LessonProgressInsert.serializer(), LessonProgressInsert("u1", "PHISHING")).jsonObject

        assertEquals(setOf("user_id", "topic"), encoded.keys)
        assertEquals("PHISHING", encoded.getValue("topic").jsonPrimitive.content)
    }

    @Test
    fun labInsert_sendsOnlyUserIdAndLab() {
        val encoded = json.encodeToJsonElement(LabProgressInsert.serializer(), LabProgressInsert("u1", "REDES_SEGURAS")).jsonObject

        assertEquals(setOf("user_id", "lab"), encoded.keys)
        assertEquals("REDES_SEGURAS", encoded.getValue("lab").jsonPrimitive.content)
    }

    @Test
    fun examInsert_sendsOnlyUserIdAndCorrectCount_neverPassedNorTime() {
        val encoded = json.encodeToJsonElement(ExamAttemptInsert.serializer(), ExamAttemptInsert("u1", 5)).jsonObject

        assertEquals(setOf("user_id", "correct_count"), encoded.keys)
        assertEquals("5", encoded.getValue("correct_count").jsonPrimitive.content)
    }

    @Test
    fun theTopicSentIsTheEnumName_forEveryTopic() {
        LearningTopic.entries.forEach { topic ->
            assertEquals(topic, LearningTopic.fromName(LessonProgressInsert("u", topic.name).topic))
        }
    }

    // ---- what is read ----

    @Test
    fun lessonRows_becomeTheSetOfViewedLessons_dropUnknownTopics() {
        val rows = json.decodeFromString<List<LessonProgressRow>>(
            """[{"user_id":"u","topic":"CONTRASENAS","completed_at":"2026-10-18T15:30:00.123456+00:00"},
                {"user_id":"u","topic":"PHISHING"},
                {"user_id":"u","topic":"TEMA_NUEVO"},
                {"user_id":"u","topic":"PHISHING","extra":1}]""",
        )

        assertEquals(setOf(LearningTopic.CONTRASENAS, LearningTopic.PHISHING), rows.toViewedLessons())
    }

    @Test
    fun labRows_becomeTheSetOfCompletedLabs_dropUnknownLabs() {
        val rows = json.decodeFromString<List<LabProgressRow>>(
            """[{"user_id":"u","lab":"FIREWALL","completed_at":"2026-10-18T15:30:00+00:00"},{"user_id":"u","lab":"REDES"}]""",
        )

        assertEquals(setOf(LearningTopic.FIREWALL), rows.toCompletedLabs())
    }

    @Test
    fun anEmptyList_isAnEmptySet() {
        assertEquals(emptySet<LearningTopic>(), emptyList<LessonProgressRow>().toViewedLessons())
        assertEquals(emptySet<LearningTopic>(), emptyList<LabProgressRow>().toCompletedLabs())
    }

    @Test
    fun examRow_decodesTheServerAnswer_andReadsTheTimestamp() {
        val row = json.decodeFromString<ExamAttemptRow>(
            """{"id":12,"user_id":"u","correct_count":5,"passed":true,"taken_at":"2026-10-18T15:30:00.123456+00:00","extra":true}""",
        )

        assertEquals(ExamAttempt(5, passed = true, takenAt = Instant.parse("2026-10-18T15:30:00.123456Z")), row.toDomain())
    }

    @Test
    fun examRow_withAnUnreadableTimestamp_hasNoTime_butKeepsTheResult() {
        val row = ExamAttemptRow(correctCount = 3, passed = false, takenAt = "ayer")

        assertEquals(ExamAttempt(3, passed = false, takenAt = null), row.toDomain())
        assertNull(ExamAttemptRow(correctCount = 3, passed = false).toDomain().takenAt)
    }

    // ---- errors: no session, out of range ----

    @Test
    fun withoutASession_everyCall_failsAsPermissionDenied_beforeAnyRequest() = runTest {
        val repository = SupabaseLearningProgressRepository(unusedPostgrest(), currentUserId = { null })

        assertEquals(FailureKind.PermissionDenied, kindOf { repository.getSnapshot() })
        assertEquals(FailureKind.PermissionDenied, kindOf { repository.markLessonViewed(LearningTopic.PHISHING) })
        assertEquals(FailureKind.PermissionDenied, kindOf { repository.markLabCompleted(LearningTopic.PHISHING) })
        assertEquals(FailureKind.PermissionDenied, kindOf { repository.recordExamAttempt(5) })
    }

    @Test
    fun anExamCountOutOfRange_isRejected_beforeAnyRequest() = runTest {
        val repository = SupabaseLearningProgressRepository(unusedPostgrest(), currentUserId = { "u1" })

        listOf(-1, 7, 100).forEach { count ->
            try {
                repository.recordExamAttempt(count)
                fail("debía lanzar con $count")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
    }
}
