package com.example.nightwatchprotocol.ui.common

import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.common.UiText

/**
 * The text of an [AuthError] as a resource, for the screens that show it as it is (Perfil, Verificar código).
 * `AuthError.message` keeps the same Spanish text for the login and registration screens until Part B moves them here.
 */
fun AuthError.toUiText(): UiText = UiText.of(
    when (this) {
        AuthError.WrongPassword -> R.string.error_auth_wrong_password
        AuthError.UserNotFound -> R.string.error_auth_user_not_found
        AuthError.InvalidCredentials -> R.string.error_auth_invalid_credentials
        AuthError.GoogleAccountOnly -> R.string.error_auth_google_account_only
        AuthError.InvalidEmail -> R.string.error_auth_invalid_email
        AuthError.EmailAlreadyInUse -> R.string.error_auth_email_already_in_use
        AuthError.WeakPassword -> R.string.error_auth_weak_password
        AuthError.RecentLoginRequired -> R.string.error_auth_recent_login_required
        AuthError.EmailConfirmationRequired -> R.string.error_auth_email_confirmation_required
        AuthError.EmailNotConfirmed -> R.string.error_auth_email_not_confirmed
        AuthError.InvalidCode -> R.string.error_auth_invalid_code
        AuthError.CodeExpired -> R.string.error_auth_code_expired
        AuthError.TooManyAttempts -> R.string.error_auth_too_many_attempts
        is AuthError.RateLimited -> R.string.error_auth_rate_limited
        AuthError.SameAsPrimaryEmail -> R.string.error_auth_same_as_primary_email
        AuthError.EmailSendFailed -> R.string.error_auth_email_send_failed
        AuthError.NetworkError -> R.string.common_no_connection
        AuthError.Unknown -> R.string.common_generic_error
    },
)
