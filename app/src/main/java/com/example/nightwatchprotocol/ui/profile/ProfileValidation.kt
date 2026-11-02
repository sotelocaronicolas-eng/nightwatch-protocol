package com.example.nightwatchprotocol.ui.profile

import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.common.UiText

private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** A light syntactic check; the server has the final say on whether an address is valid. */
fun isPlausibleEmail(email: String): Boolean = EMAIL_PATTERN.matches(email.trim())

val MESSAGE_WRONG_CURRENT_PASSWORD: UiText = UiText.of(R.string.profile_error_wrong_current_password)
val MESSAGE_ENTER_CURRENT_PASSWORD: UiText = UiText.of(R.string.profile_error_enter_current_password)

/** Texts shared by Perfil and the password-recovery flow (auth). */
val TEXT_PASSWORD_RULES: UiText = UiText.of(R.string.profile_error_password_rules)
val TEXT_PASSWORDS_DO_NOT_MATCH: UiText = UiText.of(R.string.profile_error_passwords_do_not_match)
val TEXT_GENERIC_ERROR: UiText = UiText.of(R.string.common_generic_error)
val TEXT_NO_CONNECTION: UiText = UiText.of(R.string.common_no_connection)

/** Message for a failed reauthentication or password change. */
fun passwordOperationErrorMessage(error: Throwable): UiText = when (error) {
    AuthError.WrongPassword, AuthError.InvalidCredentials -> MESSAGE_WRONG_CURRENT_PASSWORD
    AuthError.WeakPassword -> TEXT_PASSWORD_RULES
    AuthError.NetworkError -> TEXT_NO_CONNECTION
    else -> TEXT_GENERIC_ERROR
}
