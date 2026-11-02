package com.example.nightwatchprotocol.data.auth

/**
 * Why an authentication step failed. The [message] is a technical identifier for the logs and the tests, never text for the
 * person: the screens translate each case to a resource with `AuthError.toUiText()` (`ui/common/AuthErrorText.kt`).
 */
sealed class AuthError(message: String) : Exception(message) {
    data object WrongPassword : AuthError("wrong_password")
    data object UserNotFound : AuthError("user_not_found")
    data object InvalidCredentials : AuthError("invalid_credentials")
    data object GoogleAccountOnly : AuthError("google_account_only")
    data object InvalidEmail : AuthError("invalid_email")
    data object EmailAlreadyInUse : AuthError("email_already_in_use")
    data object WeakPassword : AuthError("weak_password")
    data object RecentLoginRequired : AuthError("recent_login_required")
    /** The account was created but the backend wants the email confirmed first, so there is no session yet. */
    data object EmailConfirmationRequired : AuthError("email_confirmation_required")
    /** Signing in with the right password, but the email was never confirmed with its code. */
    data object EmailNotConfirmed : AuthError("email_not_confirmed")
    /** The 6-digit code was refused. The server answers the same for a wrong and for an expired code. */
    data object InvalidCode : AuthError("invalid_code")
    /** The code was older than its validity. The server cannot tell this from [InvalidCode]; the code screen can. */
    data object CodeExpired : AuthError("code_expired")
    /** The server stopped answering verification requests (HTTP 429 over_request_rate_limit). */
    data object TooManyAttempts : AuthError("too_many_attempts")
    /**
     * Another email was asked for too soon (over_email_send_rate_limit). [retryAfterSeconds] is what the server said
     * ("you can only request this after 45 seconds"), or null if it did not say.
     */
    data class RateLimited(val retryAfterSeconds: Int?) : AuthError("rate_limited")
    /** The recovery email is the account's own email: it must be a different address. */
    data object SameAsPrimaryEmail : AuthError("same_as_primary_email")
    /** The email with the code could not be sent (the mail provider refused it). */
    data object EmailSendFailed : AuthError("email_send_failed")
    data object NetworkError : AuthError("network_error")
    data object Unknown : AuthError("unknown")
}
