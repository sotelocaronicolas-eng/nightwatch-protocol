package com.example.nightwatchprotocol.ui.auth.register

data class PasswordRules(
    val hasMinLength: Boolean,
    val hasUpperCaseAndDigit: Boolean,
    val hasSpecialChar: Boolean,
) {
    val allSatisfied: Boolean
        get() = hasMinLength && hasUpperCaseAndDigit && hasSpecialChar

    companion object {
        // Mirrors Firebase's password policy so a password accepted here is never rejected
        // there: uppercase = A-Z and digit = 0-9 (ASCII only, stricter than Char.isUpperCase /
        // isDigit), and "special" = only the characters Firebase documents as non-alphanumeric
        // (a space, ñ, é, €, ¿ ... do NOT count for Firebase even though !isLetterOrDigit() says so).
        const val FIREBASE_SPECIAL_CHARACTERS = "^\$*.[]{}()?\"!@#%&/\\,><':;|_~`=+-"

        fun from(password: String) = PasswordRules(
            hasMinLength = password.length >= 8,
            hasUpperCaseAndDigit = password.any { it in 'A'..'Z' } && password.any { it in '0'..'9' },
            hasSpecialChar = password.any { it in FIREBASE_SPECIAL_CHARACTERS },
        )
    }
}

data class RegisterUiState(
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val fieldError: RegisterFieldError? = null,
) {
    val passwordRules: PasswordRules
        get() = PasswordRules.from(password)

    val showPasswordMismatch: Boolean
        get() = confirmPassword.isNotEmpty() && password != confirmPassword

    val isRegisterEnabled: Boolean
        get() = fullName.isNotBlank() &&
            email.isNotBlank() &&
            passwordRules.allSatisfied &&
            password == confirmPassword &&
            !isLoading
}

sealed interface RegisterFieldError {
    data object EmailAlreadyInUse : RegisterFieldError
    data object InvalidEmail : RegisterFieldError
    data object PasswordMismatch : RegisterFieldError
    data object WeakPassword : RegisterFieldError
}

sealed interface RegisterEvent {
    data object NavigateToMainMenu : RegisterEvent
    data object NavigateBack : RegisterEvent
    data class ShowErrorDialog(val kind: com.example.nightwatchprotocol.ui.auth.login.ErrorDialogKind) : RegisterEvent
}
