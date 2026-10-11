package com.example.nightwatchprotocol.ui.profile.changepassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.account.AccountRepository
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.ui.auth.register.PasswordRules
import com.example.nightwatchprotocol.ui.profile.MESSAGE_ENTER_CURRENT_PASSWORD
import com.example.nightwatchprotocol.ui.profile.MESSAGE_GENERIC_ERROR
import com.example.nightwatchprotocol.ui.profile.MESSAGE_PASSWORDS_DO_NOT_MATCH
import com.example.nightwatchprotocol.ui.profile.MESSAGE_PASSWORD_RULES
import com.example.nightwatchprotocol.ui.profile.MESSAGE_WRONG_CURRENT_PASSWORD
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChangePasswordUiState(
    val currentPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    /** False for Google-only accounts: the form is replaced by a notice. */
    val hasPassword: Boolean = true,
    val isLoading: Boolean = false,
    val currentError: String? = null,
    val newError: String? = null,
    val confirmError: String? = null,
    val formError: String? = null,
)

sealed interface ChangePasswordEvent {
    data object PasswordChanged : ChangePasswordEvent
}

class ChangePasswordViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChangePasswordUiState(hasPassword = accountRepository.currentProfile()?.hasPassword ?: true),
    )
    val uiState: StateFlow<ChangePasswordUiState> = _uiState.asStateFlow()

    private val _events = Channel<ChangePasswordEvent>(Channel.BUFFERED)
    val events: Flow<ChangePasswordEvent> = _events.receiveAsFlow()

    fun onCurrentPasswordChanged(value: String) =
        _uiState.update { it.copy(currentPassword = value, currentError = null, formError = null) }

    fun onNewPasswordChanged(value: String) =
        _uiState.update { it.copy(newPassword = value, newError = null, confirmError = null, formError = null) }

    fun onConfirmPasswordChanged(value: String) =
        _uiState.update { it.copy(confirmPassword = value, confirmError = null, formError = null) }

    fun onSaveClicked() {
        val state = _uiState.value
        if (state.isLoading || !state.hasPassword) return

        // Local checks first: a mismatch or a weak password never reaches Firebase.
        val currentError = if (state.currentPassword.isEmpty()) MESSAGE_ENTER_CURRENT_PASSWORD else null
        val newError = if (!PasswordRules.from(state.newPassword).allSatisfied) MESSAGE_PASSWORD_RULES else null
        val confirmError =
            if (newError == null && state.newPassword != state.confirmPassword) MESSAGE_PASSWORDS_DO_NOT_MATCH else null
        if (currentError != null || newError != null || confirmError != null) {
            _uiState.update {
                it.copy(currentError = currentError, newError = newError, confirmError = confirmError, formError = null)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, currentError = null, newError = null, confirmError = null, formError = null)
            }
            try {
                accountRepository.changePassword(state.currentPassword, state.newPassword)
                _uiState.update { it.copy(isLoading = false, currentPassword = "", newPassword = "", confirmPassword = "") }
                _events.send(ChangePasswordEvent.PasswordChanged)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthError) {
                _uiState.update { it.applyError(e) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, formError = MESSAGE_GENERIC_ERROR) }
            }
        }
    }

    private fun ChangePasswordUiState.applyError(error: AuthError): ChangePasswordUiState = when (error) {
        AuthError.WrongPassword, AuthError.InvalidCredentials ->
            copy(isLoading = false, currentError = MESSAGE_WRONG_CURRENT_PASSWORD)
        AuthError.WeakPassword -> copy(isLoading = false, newError = MESSAGE_PASSWORD_RULES)
        AuthError.NetworkError -> copy(isLoading = false, formError = "Sin conexión a internet")
        else -> copy(isLoading = false, formError = MESSAGE_GENERIC_ERROR)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                ChangePasswordViewModel(application.container.accountRepository)
            }
        }
    }
}
