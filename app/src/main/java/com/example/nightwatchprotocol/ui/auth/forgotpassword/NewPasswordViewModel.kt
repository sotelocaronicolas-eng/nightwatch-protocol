package com.example.nightwatchprotocol.ui.auth.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.account.AccountRepository
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import com.example.nightwatchprotocol.ui.auth.register.PasswordRules
import com.example.nightwatchprotocol.ui.profile.MESSAGE_GENERIC_ERROR
import com.example.nightwatchprotocol.ui.profile.MESSAGE_PASSWORDS_DO_NOT_MATCH
import com.example.nightwatchprotocol.ui.profile.MESSAGE_PASSWORD_RULES
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NewPasswordUiState(
    val newPassword: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val newError: String? = null,
    val confirmError: String? = null,
    val formError: String? = null,
) {
    /** The same rules as the sign-up and "Cambiar Contraseña" screens, evaluated live as the user types. */
    val rules: PasswordRules get() = PasswordRules.from(newPassword)

    val confirmationMismatch: Boolean get() = confirmPassword.isNotEmpty() && confirmPassword != newPassword

    val canSubmit: Boolean get() = !isLoading && rules.allSatisfied && confirmPassword == newPassword
}

sealed interface NewPasswordEvent {
    /** The password was changed and the recovery session closed: show "¡Contraseña actualizada!". */
    data object PasswordReset : NewPasswordEvent

    /** The user left without setting a password; the recovery session is closed and the app goes back to Login. */
    data object Cancelled : NewPasswordEvent
}

/**
 * The last step of the recovery by code. Verifying the code opened a session, so the user is signed in without having
 * typed a password: this screen must end by signing out, whether the password was set or the user backed out.
 */
class NewPasswordViewModel(
    private val accountRepository: AccountRepository,
    private val authRepository: AuthRepository,
    private val sessionPreferences: SessionPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewPasswordUiState())
    val uiState: StateFlow<NewPasswordUiState> = _uiState.asStateFlow()

    private val _events = Channel<NewPasswordEvent>(Channel.BUFFERED)
    val events: Flow<NewPasswordEvent> = _events.receiveAsFlow()

    fun onNewPasswordChanged(value: String) =
        _uiState.update { it.copy(newPassword = value, newError = null, confirmError = null, formError = null) }

    fun onConfirmPasswordChanged(value: String) =
        _uiState.update { it.copy(confirmPassword = value, confirmError = null, formError = null) }

    fun onSaveClicked() {
        val state = _uiState.value
        if (state.isLoading) return

        val newError = if (!state.rules.allSatisfied) MESSAGE_PASSWORD_RULES else null
        val confirmError =
            if (newError == null && state.newPassword != state.confirmPassword) MESSAGE_PASSWORDS_DO_NOT_MATCH else null
        if (newError != null || confirmError != null) {
            _uiState.update { it.copy(newError = newError, confirmError = confirmError, formError = null) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, newError = null, confirmError = null, formError = null) }
            try {
                accountRepository.updatePassword(state.newPassword)
                authRepository.signOut()
                sessionPreferences.setRecoveryInProgress(false)
                _uiState.update { it.copy(isLoading = false, newPassword = "", confirmPassword = "") }
                _events.send(NewPasswordEvent.PasswordReset)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthError) {
                _uiState.update { it.applyError(e) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, formError = MESSAGE_GENERIC_ERROR) }
            }
        }
    }

    /** "X" or the back button: no password was set, so the recovery session must not stay open. */
    fun onCancelClicked() {
        authRepository.signOut()
        viewModelScope.launch {
            sessionPreferences.setRecoveryInProgress(false)
            _events.send(NewPasswordEvent.Cancelled)
        }
    }

    private fun NewPasswordUiState.applyError(error: AuthError): NewPasswordUiState = when (error) {
        AuthError.WeakPassword -> copy(isLoading = false, newError = MESSAGE_PASSWORD_RULES)
        AuthError.NetworkError -> copy(isLoading = false, formError = "Sin conexión a internet")
        else -> copy(isLoading = false, formError = MESSAGE_GENERIC_ERROR)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                NewPasswordViewModel(
                    application.container.accountRepository,
                    application.container.authRepository,
                    application.container.sessionPreferences,
                )
            }
        }
    }
}
