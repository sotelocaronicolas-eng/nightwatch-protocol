package com.example.nightwatchprotocol.data.device

import com.example.nightwatchprotocol.data.firestore.FailureKind
import com.example.nightwatchprotocol.data.firestore.FirestoreAccessException
import com.example.nightwatchprotocol.data.supabase.unusedPostgrest
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SupabaseDeviceRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val now = Instant.parse("2026-10-17T12:30:00Z")

    @Test
    fun toRecord_mapsColumns_andParsesTheServerTimestamp() {
        val row = DeviceRow("u", "dev-1", "Pixel 8", "2026-10-17T12:00:00.123456+00:00", revoked = true)

        assertEquals(
            DeviceRecord("dev-1", "Pixel 8", Instant.parse("2026-10-17T12:00:00.123456Z").toEpochMilli(), true),
            row.toRecord(),
        )
    }

    @Test
    fun parseTimestamp_acceptsZAndOffsets_andToleratesGarbage() {
        val expected = Instant.parse("2026-10-17T12:00:00Z").toEpochMilli()
        assertEquals(expected, parseTimestampMillis("2026-10-17T12:00:00Z"))
        assertEquals(expected, parseTimestampMillis("2026-10-17T14:00:00+02:00"))
        assertNull(parseTimestampMillis("ayer"))
        assertNull(parseTimestampMillis(null))
    }

    @Test
    fun row_decodesTheServerJson_withDefaultsForMissingColumns() {
        val row = json.decodeFromString(
            DeviceRow.serializer(),
            """{"user_id":"u","device_id":"d","name":"Tablet","last_seen":"2026-10-17T12:00:00+00:00","revoked":false,"x":1}""",
        )
        assertEquals("d", row.toRecord().id)
        assertFalse(row.toRecord().revoked)
    }

    @Test
    fun saveDevicePayload_seenAgain_omitsRevoked_soAMarkIsNeverCleared() {
        val payload = saveDevicePayload("u", "d", "Pixel", revoked = null, now = now)

        assertEquals(setOf("user_id", "device_id", "name", "last_seen"), payload.keys)
        assertEquals(JsonPrimitive("2026-10-17T12:30:00Z"), payload.getValue("last_seen"))
    }

    @Test
    fun saveDevicePayload_newDevice_writesRevokedFalse() {
        val payload = saveDevicePayload("u", "d", "Pixel", revoked = false, now = now)

        assertEquals(JsonPrimitive(false), payload.getValue("revoked"))
    }

    @Test
    fun sanitizeDeviceName_respectsTheColumnLimits() {
        assertEquals("Pixel", sanitizeDeviceName("  Pixel  "))
        assertEquals(80, sanitizeDeviceName("x".repeat(200)).length)
        assertEquals("Dispositivo", sanitizeDeviceName("   "))
    }

    @Test
    fun withoutASession_everyCallFailsAsPermissionDenied_beforeAnyRequest() = runTest {
        val repository = SupabaseDeviceRepository(unusedPostgrest(), currentUserId = { null })

        listOf<suspend () -> Unit>(
            { repository.getDevice("x", "d") },
            { repository.listDevices("x") },
            { repository.saveDevice("x", "d", "n", null) },
            { repository.setRevoked("x", "d", true) },
            { repository.deleteDevice("x", "d") },
        ).forEach { call ->
            try {
                call()
                fail("debía lanzar")
            } catch (e: FirestoreAccessException) {
                assertEquals(FailureKind.PermissionDenied, e.kind)
            }
        }
    }
}
