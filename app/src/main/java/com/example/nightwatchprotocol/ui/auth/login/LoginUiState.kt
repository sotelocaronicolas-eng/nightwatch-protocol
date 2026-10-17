package com.example.nightwatchprotocol.ui.auth.login

import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.AuthRepository

data class LoginUiState(
    val usernameOrEmail: String = "",
    val password: String = "",
    val rememberSession: Boolean = false,
    val isLoading: Boolean = false,
    val fieldError: LoginFieldError? = null,
    val showUsernameRequired: Boolean = false,
    val showPasswordRequired: Boolean = false,
)

sealed interface LoginFieldError {
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

/**
 * The dialog for a Google sign-in that failed after the ID token arrived. Only the backend that checks nonces
 * (Supabase) tells "no connection" apart; Firebase keeps its single generic message.
 */
fun googleErrorKind(authRepository: AuthRepository, error: AuthError): ErrorDialogKind =
    if (authRepository.usesGoogleNonce && error == AuthError.NetworkError) {
        ErrorDialogKind.NETWORK
    } else {
        ErrorDialogKind.UNKNOWN
    }

sealed interface LoginEvent {
    data object NavigateToMainMenu : LoginEvent
    data object NavigateToRegister : LoginEvent
    data object NavigateToForgotPassword : LoginEvent
    data class ShowErrorDialog(val kind: ErrorDialogKind) : LoginEvent
}
