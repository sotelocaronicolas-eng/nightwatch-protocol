package com.example.nightwatchprotocol.ui.profile.changeemail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.account.AccountRepository
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.ui.navigation.ChangeEmail
import com.example.nightwatchprotocol.ui.profile.MESSAGE_GENERIC_ERROR
import com.example.nightwatchprotocol.ui.verifycode.MESSAGE_WAIT_BEFORE_NEW_CODE
import com.example.nightwatchprotocol.ui.profile.isPlausibleEmail
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MESSAGE_EMAIL_INVALID = "Correo electrónico inválido"
const val MESSAGE_EMAIL_IN_USE = "Ese correo ya está en uso en otra cuenta"
const val MESSAGE_EMAIL_SAME = "Ese ya es el correo de tu cuenta"

data class ChangeEmailUiState(
    val email: String = "",
    /** False for Google-only accounts: the form is replaced by a notice. */
    val hasPassword: Boolean = true,
    val isSending: Boolean = false,
    val error: String? = null,
)

sealed interface ChangeEmailEvent {
    /** A 6-digit code was sent to [newEmail]; the next screen is where it is typed. */
    data class CodeSent(val newEmail: String) : ChangeEmailEvent
    data object NavigateToConfirmIdentity : ChangeEmailEvent
}

class ChangeEmailViewModel(
    private val accountRepository: AccountRepository,
    initialEmail: String,
) : ViewModel() {

    private val account = accountRepository.currentProfile()

    private val _uiState = MutableStateFlow(
        ChangeEmailUiState(email = initialEmail, hasPassword = account?.hasPassword ?: true),
    )
    val uiState: StateFlow<ChangeEmailUiState> = _uiState.asStateFlow()

    private val _events = Channel<ChangeEmailEvent>(Channel.BUFFERED)
    val events: Flow<ChangeEmailEvent> = _events.receiveAsFlow()

    private var identityConfirmed = false

    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, error = null) }

    fun onSendCodeClicked() {
        val state = _uiState.value
        if (state.isSending || !state.hasPassword) return
        val email = state.email.trim()
        val error = when {
            !isPlausibleEmail(email) -> MESSAGE_EMAIL_INVALID
            email.equals(account?.email, ignoreCase = true) -> MESSAGE_EMAIL_SAME
            else -> null
        }
        if (error != null) {
            _uiState.update { it.copy(error = error) }
            return
        }
        // The server never answers "recent login required" for an email change, so the password is asked for here,
        // once, before anything is sent.
        if (!identityConfirmed) {
            viewModelScope.launch { _events.send(ChangeEmailEvent.NavigateToConfirmIdentity) }
            return
        }
        send(email)
    }

    /** The user proved their password on "Confirma tu identidad": send the code. */
    fun onIdentityConfirmed() {
        val state = _uiState.value
        if (state.isSending || !state.hasPassword) return
        identityConfirmed = true
        send(state.email.trim())
    }

    private fun send(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            try {
                accountRepository.requestEmailChange(email)
                _uiState.update { it.copy(isSending = false) }
                _events.send(ChangeEmailEvent.CodeSent(email))
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthError.RecentLoginRequired) {
                _uiState.update { it.copy(isSending = false) }
                _events.send(ChangeEmailEvent.NavigateToConfirmIdentity)
            } catch (e: AuthError) {
                _uiState.update { it.copy(isSending = false, error = messageFor(e)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSending = false, error = MESSAGE_GENERIC_ERROR) }
            }
        }
    }

    private fun messageFor(error: AuthError): String = when (error) {
        AuthError.InvalidEmail -> MESSAGE_EMAIL_INVALID
        AuthError.EmailAlreadyInUse -> MESSAGE_EMAIL_IN_USE
        is AuthError.RateLimited -> MESSAGE_WAIT_BEFORE_NEW_CODE
        AuthError.NetworkError -> "Sin conexión a internet"
        else -> MESSAGE_GENERIC_ERROR
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val route = createSavedStateHandle().toRoute<ChangeEmail>()
                ChangeEmailViewModel(application.container.accountRepository, route.newEmail)
            }
        }
    }
}
