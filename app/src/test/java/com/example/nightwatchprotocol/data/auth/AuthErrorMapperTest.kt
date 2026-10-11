package com.example.nightwatchprotocol.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AuthErrorMapperTest {
    @Test
    fun wrongPasswordCode_mapsToWrongPassword() {
        assertEquals(AuthError.WrongPassword, mapAuthErrorCode("ERROR_WRONG_PASSWORD"))
    }

    @Test
    fun invalidCredentialCode_mapsToInvalidCredentials_notToWrongPassword() {
        assertEquals(AuthError.InvalidCredentials, mapAuthErrorCode("ERROR_INVALID_CREDENTIAL"))
    }

    @Test
    fun wrongPasswordAndUserNotFound_areDistinctErrors() {
        assertNotEquals(mapAuthErrorCode("ERROR_WRONG_PASSWORD"), mapAuthErrorCode("ERROR_USER_NOT_FOUND"))
    }

    @Test
    fun passwordPolicyCode_mapsToWeakPassword() {
        assertEquals(AuthError.WeakPassword, mapAuthErrorCode("ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS"))
    }

    @Test
    fun requiresRecentLoginCode_mapsToRecentLoginRequired() {
        assertEquals(AuthError.RecentLoginRequired, mapAuthErrorCode("ERROR_REQUIRES_RECENT_LOGIN"))
    }

    @Test
    fun passwordPolicyBackendMessage_mapsToWeakPassword() {
        val message = "PASSWORD_DOES_NOT_MEET_REQUIREMENTS : Missing password requirements: [Password must contain an upper case character]"
        assertEquals(AuthError.WeakPassword, mapAuthErrorMessage(message))
    }

    @Test
    fun unrelatedOrNullMessage_mapsToUnknown() {
        assertEquals(AuthError.Unknown, mapAuthErrorMessage("too many requests"))
        assertEquals(AuthError.Unknown, mapAuthErrorMessage(null))
    }

    @Test
    fun userNotFoundCode_mapsToUserNotFound() {
        assertEquals(AuthError.UserNotFound, mapAuthErrorCode("ERROR_USER_NOT_FOUND"))
    }

    @Test
    fun invalidEmailCode_mapsToInvalidEmail() {
        assertEquals(AuthError.InvalidEmail, mapAuthErrorCode("ERROR_INVALID_EMAIL"))
    }

    @Test
    fun emailAlreadyInUseCode_mapsToEmailAlreadyInUse() {
        assertEquals(AuthError.EmailAlreadyInUse, mapAuthErrorCode("ERROR_EMAIL_ALREADY_IN_USE"))
    }

    @Test
    fun weakPasswordCode_mapsToWeakPassword() {
        assertEquals(AuthError.WeakPassword, mapAuthErrorCode("ERROR_WEAK_PASSWORD"))
    }

    @Test
    fun unrecognizedCode_mapsToUnknown() {
        assertEquals(AuthError.Unknown, mapAuthErrorCode("ERROR_SOMETHING_NEW"))
    }

    @Test
    fun nullCode_mapsToUnknown() {
        assertEquals(AuthError.Unknown, mapAuthErrorCode(null))
    }
}
