package com.example.nightwatchprotocol.ui.auth.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import com.example.nightwatchprotocol.ui.profile.isPlausibleEmail
import com.example.nightwatchprotocol.ui.verifycode.MESSAGE_WAIT_BEFORE_NEW_CODE
import com.example.nightwatchprotocol.ui.verifycode.RecoveryCodeFlow
import com.example.nightwatchprotocol.ui.verifycode.VerifyCodeViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RecoveryStep {
    /** "Recuperación de contraseña": the email field. */
    Email,

    /** The same dialog with the six boxes for the code. */
    Code,
}

/** Why the email step failed; [message] is what the dialog shows under the field. */
enum class RecoveryEmailError(val message: String) {
    InvalidEmail("Correo electrónico inválido"),
    NotFound("Correo no encontrado"),
    GoogleAccountOnly("Esta cuenta se creó con Google. Usa «Continuar con Google»"),
    TooSoon(MESSAGE_WAIT_BEFORE_NEW_CODE),
    Offline("Sin conexión a internet"),
    Generic("Se produjo un error inesperado. Intenta de nuevo."),
}

data class PasswordRecoveryUiState(
    val step: RecoveryStep = RecoveryStep.Email,
    val email: String = "",
    val isSending: Boolean = false,
    val error: RecoveryEmailError? = null,
)

/**
 * The email step of the recovery dialog (Supabase). Sending the code moves the dialog to its code step, which is run by
 * a [VerifyCodeViewModel] over [RecoveryCodeFlow] (see [codeViewModelFactory]). One instance per opening of the
 * dialog, starting from the email that was typed on Login ([prefillEmail]).
 */
class PasswordRecoveryViewModel(
    private val authRepository: AuthRepository,
    private val sessionPreferences: SessionPreferences,
    prefillEmail: String = "",
) : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordRecoveryUiState(email = prefillEmail.trim()))
    val uiState: StateFlow<PasswordRecoveryUiState> = _uiState.asStateFlow()

    fun onEmailChanged(value: String) {
        _uiState.update { it.copy(email = value, error = null) }
    }

    fun onSendClicked() {
        val state = _uiState.value
        if (state.isSending) return
        val email = state.email.trim()
        if (!isPlausibleEmail(email)) {
            _uiState.update { it.copy(error = RecoveryEmailError.InvalidEmail) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            try {
                // Throws UserNotFound / GoogleAccountOnly before anything is sent.
                authRepository.sendPasswordResetEmail(email)
                _uiState.update { it.copy(isSending = false, email = email, step = RecoveryStep.Code) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthError) {
                _uiState.update { it.copy(isSending = false, error = errorFor(e)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, error = RecoveryEmailError.Generic) }
            }
        }
    }

    /** The view model of the code step, for the email the code was sent to. */
    fun codeViewModelFactory(): ViewModelProvider.Factory {
        val email = _uiState.value.email
        return viewModelFactory {
            initializer { VerifyCodeViewModel(RecoveryCodeFlow(authRepository, sessionPreferences, email), email) }
        }
    }

    private fun errorFor(error: AuthError): RecoveryEmailError = when (error) {
        AuthError.UserNotFound -> RecoveryEmailError.NotFound
        AuthError.GoogleAccountOnly -> RecoveryEmailError.GoogleAccountOnly
        AuthError.InvalidEmail -> RecoveryEmailError.InvalidEmail
        is AuthError.RateLimited -> RecoveryEmailError.TooSoon
        AuthError.NetworkError -> RecoveryEmailError.Offline
        else -> RecoveryEmailError.Generic
    }

    companion object {
        fun factory(prefillEmail: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                PasswordRecoveryViewModel(
                    application.container.authRepository,
                    application.container.sessionPreferences,
                    prefillEmail,
                )
            }
        }
    }
}
