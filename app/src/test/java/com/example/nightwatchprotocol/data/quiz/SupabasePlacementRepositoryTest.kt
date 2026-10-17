package com.example.nightwatchprotocol.data.quiz

import com.example.nightwatchprotocol.data.firestore.FailureKind
import com.example.nightwatchprotocol.data.firestore.FirestoreAccessException
import com.example.nightwatchprotocol.data.supabase.unusedPostgrest
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SupabasePlacementRepositoryTest {
    // The serializer Postgrest uses: encodeDefaults is OFF, which is why the row has no default values.
    private val json = Json { ignoreUnknownKeys = true }
    private val now = Instant.parse("2026-10-17T12:30:00Z")

    @Test
    fun toRow_usesEnumNamesAndTheClock() {
        val result = PlacementResult(PlacementLevel.MEDIO, setOf(QuizCategory.CONTRASENAS, QuizCategory.REDES_MALWARE))

        val row = result.toRow("user-1", now)

        assertEquals(PlacementRow("user-1", "MEDIO", listOf("CONTRASENAS", "REDES_MALWARE"), "2026-10-17T12:30:00Z"), row)
    }

    @Test
    fun toDomain_roundTripsEveryLevelAndCategory() {
        PlacementLevel.entries.forEach { level ->
            val result = PlacementResult(level, QuizCategory.entries.toSet())
            assertEquals(result, result.toRow("u", now).toDomain())
        }
    }

    @Test
    fun toDomain_unknownLevel_isNull() {
        assertNull(PlacementRow("u", "EXPERTO", emptyList()).toDomain())
    }

    @Test
    fun toDomain_dropsUnknownCategories() {
        val row = PlacementRow("u", "BAJO", listOf("CONTRASENAS", "OTRA_COSA"))
        assertEquals(PlacementResult(PlacementLevel.BAJO, setOf(QuizCategory.CONTRASENAS)), row.toDomain())
    }

    @Test
    fun encoded_usesSnakeCaseColumns_andSendsAnEmptyListOfCategories() {
        val encoded = json.encodeToJsonElement(PlacementRow.serializer(), PlacementResult(PlacementLevel.ALTO, emptySet()).toRow("u", now)).jsonObject

        assertEquals(setOf("user_id", "level", "weak_categories", "completed_at"), encoded.keys)
        assertEquals(0, encoded.getValue("weak_categories").jsonArray.size)
        assertEquals("ALTO", encoded.getValue("level").jsonPrimitive.content)
    }

    @Test
    fun decoded_ignoresUnknownColumns_andToleratesAServerTimestamp() {
        val row = json.decodeFromString(
            PlacementRow.serializer(),
            """{"user_id":"u","level":"BAJO","weak_categories":["CONCEPTOS_BASICOS"],"completed_at":"2026-10-17T12:30:00.123456+00:00","extra":1}""",
        )

        assertEquals(PlacementResult(PlacementLevel.BAJO, setOf(QuizCategory.CONCEPTOS_BASICOS)), row.toDomain())
    }

    @Test
    fun withoutASession_failsAsPermissionDenied_beforeAnyRequest() = runTest {
        val repository = SupabasePlacementRepository(unusedPostgrest(), currentUserId = { null })

        try {
            repository.getResult("whatever")
            fail("debía lanzar")
        } catch (e: FirestoreAccessException) {
            assertEquals(FailureKind.PermissionDenied, e.kind)
        }
        try {
            repository.saveResult("whatever", PlacementResult(PlacementLevel.BAJO, emptySet()))
            fail("debía lanzar")
        } catch (e: FirestoreAccessException) {
            assertEquals(FailureKind.PermissionDenied, e.kind)
        }
    }
}
