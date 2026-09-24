package com.example.nightwatchprotocol.ui.auth.register

data class PasswordRules(
    val hasMinLength: Boolean,
    val hasUpperCaseAndDigit: Boolean,
    val hasSpecialChar: Boolean,
) {
    val allSatisfied: Boolean
        get() = hasMinLength && hasUpperCaseAndDigit && hasSpecialChar

    companion object {
        fun from(password: String) = PasswordRules(
            hasMinLength = password.length >= 8,
            hasUpperCaseAndDigit = password.any { it.isUpperCase() } && password.any { it.isDigit() },
            hasSpecialChar = password.any { !it.isLetterOrDigit() },
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
