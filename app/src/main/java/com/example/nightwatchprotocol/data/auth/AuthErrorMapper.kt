package com.example.nightwatchprotocol.data.auth

fun mapAuthErrorCode(errorCode: String?): AuthError = when (errorCode) {
    "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL" -> AuthError.WrongPassword
    "ERROR_USER_NOT_FOUND" -> AuthError.UserNotFound
    "ERROR_INVALID_EMAIL" -> AuthError.InvalidEmail
    "ERROR_EMAIL_ALREADY_IN_USE" -> AuthError.EmailAlreadyInUse
    "ERROR_WEAK_PASSWORD" -> AuthError.WeakPassword
    else -> AuthError.Unknown
}
