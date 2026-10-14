package com.example.nightwatchprotocol.ui.profile

import com.example.nightwatchprotocol.data.auth.AuthError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileValidationTest {
    @Test
    fun plausibleEmails_areAccepted() {
        assertTrue(isPlausibleEmail("a@b.co"))
        assertTrue(isPlausibleEmail("  nicolas.sotelo+nw@example.com "))
    }

    @Test
    fun implausibleEmails_areRejected() {
        assertFalse(isPlausibleEmail(""))
        assertFalse(isPlausibleEmail("sin-arroba"))
        assertFalse(isPlausibleEmail("a@b"))
        assertFalse(isPlausibleEmail("a b@c.com"))
        assertFalse(isPlausibleEmail("@c.com"))
    }

    @Test
    fun passwordErrors_mapToTheirMessages() {
        assertEquals("La contraseña actual no es correcta", passwordOperationErrorMessage(AuthError.WrongPassword))
        assertEquals("La contraseña actual no es correcta", passwordOperationErrorMessage(AuthError.InvalidCredentials))
        assertEquals("La contraseña no cumple con los requerimientos", passwordOperationErrorMessage(AuthError.WeakPassword))
        assertEquals("Sin conexión a internet", passwordOperationErrorMessage(AuthError.NetworkError))
        assertEquals(MESSAGE_GENERIC_ERROR, passwordOperationErrorMessage(AuthError.Unknown))
        assertEquals(MESSAGE_GENERIC_ERROR, passwordOperationErrorMessage(RuntimeException("x")))
    }
}
