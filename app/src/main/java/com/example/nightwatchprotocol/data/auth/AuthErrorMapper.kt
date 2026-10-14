package com.example.nightwatchprotocol.data.auth

import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException

/**
 * With email-enumeration protection disabled, the backend answers EMAIL_NOT_FOUND
 * (-> ERROR_USER_NOT_FOUND) and INVALID_PASSWORD (-> ERROR_WRONG_PASSWORD), so the two
 * can be told apart. ERROR_INVALID_CREDENTIAL is what the backend returns when it
 * deliberately does not say which half was wrong (protection enabled), so it is NOT
 * mapped to WrongPassword: that would claim the account exists.
 */
fun mapAuthErrorCode(errorCode: String?): AuthError = when (errorCode) {
    "ERROR_WRONG_PASSWORD" -> AuthError.WrongPassword
    "ERROR_INVALID_CREDENTIAL" -> AuthError.InvalidCredentials
    "ERROR_USER_NOT_FOUND" -> AuthError.UserNotFound
    "ERROR_INVALID_EMAIL" -> AuthError.InvalidEmail
    "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EmailAlreadyInUse
    "ERROR_WEAK_PASSWORD", "ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS" -> AuthError.WeakPassword
    "ERROR_REQUIRES_RECENT_LOGIN" -> AuthError.RecentLoginRequired
    else -> AuthError.Unknown
}

private const val PASSWORD_POLICY_BACKEND_CODE = "PASSWORD_DOES_NOT_MEET_REQUIREMENTS"

/**
 * firebase-auth 24.2.0 has no ERROR_ code for the password-policy rejection, so the
 * backend's own code can only be recognised in the exception message.
 */
fun mapAuthErrorMessage(message: String?): AuthError =
    if (message?.contains(PASSWORD_POLICY_BACKEND_CODE, ignoreCase = true) == true) {
        AuthError.WeakPassword
    } else {
        AuthError.Unknown
    }

/**
 * Covers every Firebase-originated failure, not just the two named subtypes:
 * e.g. FirebaseTooManyRequestsException (throttling after repeated bad attempts)
 * is neither a FirebaseNetworkException nor a FirebaseAuthException, and must
 * still map to a value the UI knows how to display instead of crashing.
 */
fun mapFirebaseThrowable(throwable: FirebaseException): AuthError = when (throwable) {
    is FirebaseNetworkException -> AuthError.NetworkError
    is FirebaseAuthException -> mapAuthErrorCode(throwable.errorCode).let { fromCode ->
        if (fromCode == AuthError.Unknown) mapAuthErrorMessage(throwable.message) else fromCode
    }
    else -> mapAuthErrorMessage(throwable.message)
}
