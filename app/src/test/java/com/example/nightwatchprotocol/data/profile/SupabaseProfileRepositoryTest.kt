package com.example.nightwatchprotocol.data.profile

import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.avatar.FakeAvatarStorage
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.supabase.RecoveryEmailCodeApi
import com.example.nightwatchprotocol.data.supabase.unusedPostgrest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun noWritePayloadOfTheApp_sendsTheRecoveryEmailColumn() {
        // Migration 0006 revoked INSERT/UPDATE on profile.recovery_email: sending it would fail with "permission denied".
        listOf(extrasPayload("u", "male", "2001-02-03"), extrasPayload("u", null, null), avatarPathPayload("u", "u/avatar.jpg"))
            .forEach { assertFalse("recovery_email" in it) }
    }

    /** Records what the repository asked the server, and fails when the test says so. */
    private class FakeRecoveryCodes : RecoveryEmailCodeApi {
        var failure: AuthError? = null
        val sent = mutableListOf<String>()
        val verified = mutableListOf<String>()

        override suspend fun sendCode(email: String) {
            sent += email
            failure?.let { throw it }
        }

        override suspend fun verifyCode(code: String) {
            verified += code
            failure?.let { throw it }
        }
    }

    private fun newRepository(codes: RecoveryEmailCodeApi, userId: String? = "u") =
        SupabaseProfileRepository(unusedPostgrest(), FakeAvatarStorage(), currentUserId = { userId }, codes)

    @Test
    fun requestAndVerify_goToTheServerApi() = runTest {
        val codes = FakeRecoveryCodes()
        val repository = newRepository(codes)

        repository.requestRecoveryEmailCode("respaldo@example.com")
        repository.verifyRecoveryEmailCode("123456")

        assertEquals(listOf("respaldo@example.com"), codes.sent)
        assertEquals(listOf("123456"), codes.verified)
    }

    @Test
    fun requestAndVerify_passTheServerErrorsOn() = runTest {
        val codes = FakeRecoveryCodes().apply { failure = AuthError.RateLimited(30) }
        val repository = newRepository(codes)

        listOf<suspend () -> Unit>(
            { repository.requestRecoveryEmailCode("respaldo@example.com") },
            { repository.verifyRecoveryEmailCode("123456") },
        ).forEach { call ->
            try {
                call()
                fail("debía lanzar")
            } catch (e: AuthError) {
                assertEquals(AuthError.RateLimited(30), e)
            }
        }
    }

    @Test
    fun requestAndVerify_withoutASession_failBeforeAnyRequest() = runTest {
        val codes = FakeRecoveryCodes()
        val repository = newRepository(codes, userId = null)

        listOf<suspend () -> Unit>(
            { repository.requestRecoveryEmailCode("respaldo@example.com") },
            { repository.verifyRecoveryEmailCode("123456") },
        ).forEach { call ->
            try {
                call()
                fail("debía lanzar")
            } catch (e: AuthError) {
                assertEquals(AuthError.Unknown, e)
            }
        }
        assertTrue(codes.sent.isEmpty() && codes.verified.isEmpty())
    }

    @Test
    fun avatarPathPayload_canClearThePath() {
        assertEquals(JsonNull, avatarPathPayload("u", null).getValue("avatar_path"))
        assertEquals(JsonPrimitive("u/avatar.jpg"), avatarPathPayload("u", "u/avatar.jpg").getValue("avatar_path"))
    }

    @Test
    fun withoutASession_everyCallFailsAsPermissionDenied_beforeAnyRequest() = runTest {
        val repository = newRepository(FakeRecoveryCodes(), userId = null)

        listOf<suspend () -> Unit>(
            { repository.getProfile("x") },
            { repository.saveExtras("x", "male", null) },
        ).forEach { call ->
            try {
                call()
                fail("debía lanzar")
            } catch (e: DataAccessException) {
                assertEquals(FailureKind.PermissionDenied, e.kind)
            }
        }
    }
}
