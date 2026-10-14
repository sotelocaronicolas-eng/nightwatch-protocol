package com.example.nightwatchprotocol.ui.profile

import com.example.nightwatchprotocol.data.auth.AuthError

private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** A light syntactic check; Firebase has the final say on whether an address is valid. */
fun isPlausibleEmail(email: String): Boolean = EMAIL_PATTERN.matches(email.trim())

const val MESSAGE_WRONG_CURRENT_PASSWORD = "La contraseña actual no es correcta"
const val MESSAGE_PASSWORD_RULES = "La contraseña no cumple con los requerimientos"
const val MESSAGE_PASSWORDS_DO_NOT_MATCH = "Las contraseñas no coinciden"
const val MESSAGE_ENTER_CURRENT_PASSWORD = "Ingresa tu contraseña actual"
const val MESSAGE_GENERIC_ERROR = "Se produjo un error inesperado. Intenta de nuevo."

/** Message for a failed reauthentication or password change. */
fun passwordOperationErrorMessage(error: Throwable): String = when (error) {
    AuthError.WrongPassword, AuthError.InvalidCredentials -> MESSAGE_WRONG_CURRENT_PASSWORD
    AuthError.WeakPassword -> MESSAGE_PASSWORD_RULES
    AuthError.NetworkError -> "Sin conexión a internet"
    else -> MESSAGE_GENERIC_ERROR
}
