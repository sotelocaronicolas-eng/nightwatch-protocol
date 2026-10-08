package com.example.nightwatchprotocol.ui.auth.login

data class LoginUiState(
    val usernameOrEmail: String = "",
    val password: String = "",
    val rememberSession: Boolean = false,
    val isLoading: Boolean = false,
    val fieldError: LoginFieldError? = null,
)

sealed interface LoginFieldError {
    data object BlankFields : LoginFieldError
    data object WrongPassword : LoginFieldError
    data object UserNotFound : LoginFieldError
    data object InvalidCredentials : LoginFieldError
    data object GoogleAccountOnly : LoginFieldError
    data object InvalidEmail : LoginFieldError
}

enum class ErrorDialogKind {
    NETWORK,
    UNKNOWN,
}

sealed interface LoginEvent {
    data object NavigateToMainMenu : LoginEvent
    data object NavigateToRegister : LoginEvent
    data object NavigateToForgotPassword : LoginEvent
    data class ShowErrorDialog(val kind: ErrorDialogKind) : LoginEvent
}
