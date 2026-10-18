package com.example.nightwatchprotocol.ui.auth.register

data class PasswordRules(
    val hasMinLength: Boolean,
    val hasUpperCaseAndDigit: Boolean,
    val hasLowercase: Boolean,
    val hasSpecialChar: Boolean,
) {
    val allSatisfied: Boolean
        get() = hasMinLength && hasUpperCaseAndDigit && hasLowercase && hasSpecialChar

    companion object {
        // Mirrors the server's password policy (Supabase: "Lowercase, uppercase letters, digits and symbols") so a
        // password accepted here is never rejected there: uppercase = A-Z and digit = 0-9 (ASCII only, stricter than
        // Char.isUpperCase / isDigit), and "special" = only the symbols the server counts (a space, ñ, é, €, ¿ ...
        // do NOT count even though !isLetterOrDigit() says so).
        const val SPECIAL_CHARACTERS = "^\$*.[]{}()?\"!@#%&/\\,><':;|_~`=+-"

        fun from(password: String) = PasswordRules(
            hasMinLength = password.length >= 8,
            hasUpperCaseAndDigit = password.any { it in 'A'..'Z' } && password.any { it in '0'..'9' },
            // a-z only, like the uppercase rule.
            hasLowercase = password.any { it in 'a'..'z' },
            hasSpecialChar = password.any { it in SPECIAL_CHARACTERS },
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
    data object EmailRegisteredWithGoogle : RegisterFieldError
    data object InvalidEmail : RegisterFieldError
    data object PasswordMismatch : RegisterFieldError
    data object WeakPassword : RegisterFieldError
}

sealed interface RegisterEvent {
    data object NavigateToMainMenu : RegisterEvent
    data object NavigateBack : RegisterEvent

    /** The account exists but no session was opened: the email must be confirmed with the code sent to it. */
    data class NavigateToVerifyCode(val email: String) : RegisterEvent
    data class ShowErrorDialog(val kind: com.example.nightwatchprotocol.ui.auth.login.ErrorDialogKind) : RegisterEvent
}
