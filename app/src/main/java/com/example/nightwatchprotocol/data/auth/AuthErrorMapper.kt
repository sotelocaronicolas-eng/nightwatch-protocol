package com.example.nightwatchprotocol.data.auth

import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException

fun mapAuthErrorCode(errorCode: String?): AuthError = when (errorCode) {
    "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> AuthError.WrongPassword
    "ERROR_USER_NOT_FOUND" -> AuthError.UserNotFound
    "ERROR_INVALID_EMAIL" -> AuthError.InvalidEmail
    "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EmailAlreadyInUse
    "ERROR_WEAK_PASSWORD" -> AuthError.WeakPassword
    else -> AuthError.Unknown
}

/**
 * Covers every Firebase-originated failure, not just the two named subtypes:
 * e.g. FirebaseTooManyRequestsException (throttling after repeated bad attempts)
 * is neither a FirebaseNetworkException nor a FirebaseAuthException, and must
 * still map to a value the UI knows how to display instead of crashing.
 */
fun mapFirebaseThrowable(throwable: FirebaseException): AuthError = when (throwable) {
    is FirebaseNetworkException -> AuthError.NetworkError
    is FirebaseAuthException -> mapAuthErrorCode(throwable.errorCode)
    else -> AuthError.Unknown
}
