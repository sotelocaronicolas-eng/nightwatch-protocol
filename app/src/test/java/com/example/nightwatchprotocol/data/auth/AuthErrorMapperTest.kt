package com.example.nightwatchprotocol.data.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class AuthErrorMapperTest {
    @Test
    fun wrongPasswordCode_mapsToWrongPassword() {
        assertEquals(AuthError.WrongPassword, mapAuthErrorCode("ERROR_WRONG_PASSWORD"))
    }

    @Test
    fun invalidCredentialCode_mapsToWrongPassword() {
        assertEquals(AuthError.WrongPassword, mapAuthErrorCode("ERROR_INVALID_CREDENTIAL"))
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
