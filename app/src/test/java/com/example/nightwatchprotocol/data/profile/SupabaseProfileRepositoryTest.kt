package com.example.nightwatchprotocol.data.profile

import com.example.nightwatchprotocol.data.firestore.FailureKind
import com.example.nightwatchprotocol.data.firestore.FirestoreAccessException
import com.example.nightwatchprotocol.data.supabase.unusedPostgrest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class SupabaseProfileRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun row_toRemoteProfile_copiesEveryColumn_andLeavesBase64Empty() {
        val row = ProfileRow("u", "rec@example.com", "u/avatar.jpg", "female", "2001-02-03", "2026-10-17T12:00:00+00:00")

        assertEquals(
            RemoteProfile(
                recoveryEmail = "rec@example.com",
                avatarBase64 = null,
                gender = "female",
                birthDate = "2001-02-03",
                avatarPath = "u/avatar.jpg",
            ),
            row.toRemoteProfile(),
        )
    }

    @Test
    fun row_withNothingFilled_isAnEmptyProfile() {
        assertEquals(RemoteProfile(), ProfileRow(userId = "u").toRemoteProfile())
    }

    @Test
    fun row_decodesTheServerJson_includingTheDateAsText() {
        val row = json.decodeFromString(
            ProfileRow.serializer(),
            """{"user_id":"u","recovery_email":null,"avatar_path":null,"gender":"other","birth_date":"1999-12-31","updated_at":"2026-10-17T12:00:00.5+00:00","x":1}""",
        )

        assertEquals("1999-12-31", row.toRemoteProfile().birthDate)
        assertEquals("other", row.toRemoteProfile().gender)
    }

    @Test
    fun birthDate_roundTripsThroughTheDomainParser() {
        val remote = ProfileRow(userId = "u", birthDate = "2001-02-03").toRemoteProfile()
        assertEquals("2001-02-03", BirthDate.parseIso(remote.birthDate)?.iso())
    }

    @Test
    fun extrasPayload_sendsOnlyKeyGenderAndBirthDate_neverUpdatedAt() {
        val payload = extrasPayload("u", "male", "2001-02-03")

        assertEquals(setOf("user_id", "gender", "birth_date"), payload.keys)
        assertEquals(JsonPrimitive("2001-02-03"), payload.getValue("birth_date"))
        assertFalse("updated_at" in payload)
    }

    @Test
    fun extrasPayload_aNullIsAnExplicitJsonNull_soTheColumnIsCleared() {
        val payload = extrasPayload("u", null, null)

        assertEquals(JsonNull, payload.getValue("gender"))
        assertEquals(JsonNull, payload.getValue("birth_date"))
    }

    @Test
    fun recoveryEmailPayload_touchesOnlyThatColumn() {
        val payload = recoveryEmailPayload("u", "rec@example.com")

        assertEquals(setOf("user_id", "recovery_email"), payload.keys)
    }

    @Test
    fun avatarPathPayload_canClearThePath() {
        assertEquals(JsonNull, avatarPathPayload("u", null).getValue("avatar_path"))
        assertEquals(JsonPrimitive("u/avatar.jpg"), avatarPathPayload("u", "u/avatar.jpg").getValue("avatar_path"))
    }

    @Test
    fun withoutASession_everyCallFailsAsPermissionDenied_beforeAnyRequest() = runTest {
        val repository = SupabaseProfileRepository(unusedPostgrest(), currentUserId = { null })

        listOf<suspend () -> Unit>(
            { repository.getProfile("x") },
            { repository.saveExtras("x", "male", null) },
            { repository.saveRecoveryEmail("x", "a@b.c") },
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
