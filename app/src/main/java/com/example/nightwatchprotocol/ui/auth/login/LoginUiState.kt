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

    /** Google sign-in on a phone that has no Google account. */
    NO_GOOGLE_ACCOUNT,
    UNKNOWN,
}

const val MESSAGE_NO_GOOGLE_ACCOUNT = "Agrega una cuenta de Google en tu teléfono para continuar"
const val MESSAGE_ERROR_DIALOG_NETWORK = "Sin conexión a internet. Verifica tu conexión e intenta de nuevo."

/** The text of the error dialog; [unknown] is the screen's own generic message. */
fun ErrorDialogKind.message(unknown: String): String = when (this) {
    ErrorDialogKind.NETWORK -> MESSAGE_ERROR_DIALOG_NETWORK
    ErrorDialogKind.NO_GOOGLE_ACCOUNT -> MESSAGE_NO_GOOGLE_ACCOUNT
    ErrorDialogKind.UNKNOWN -> unknown
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

    /** The recovery runs in a dialog over Login (backends that recover with a code), starting from [prefillEmail]. */
    data class OpenRecoveryDialog(val prefillEmail: String) : LoginEvent

    /** The password was right but the email was never confirmed: a new code was sent, go and type it. */
    data class NavigateToVerifyCode(val email: String) : LoginEvent
    data class ShowErrorDialog(val kind: ErrorDialogKind) : LoginEvent
}
