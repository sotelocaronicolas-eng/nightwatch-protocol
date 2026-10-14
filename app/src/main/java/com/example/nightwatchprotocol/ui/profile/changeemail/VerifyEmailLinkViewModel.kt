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
import com.example.nightwatchprotocol.ui.navigation.VerifyEmailLink
import com.example.nightwatchprotocol.ui.profile.MESSAGE_GENERIC_ERROR
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MESSAGE_LINK_NOT_VERIFIED = "El enlace venció o no se ha verificado"
const val MESSAGE_LINK_RESENT = "Te enviamos el enlace de nuevo."

/**
 * After the link is opened Firebase can invalidate the old session, so the check itself may fail with a
 * token error. That is reported with its own message instead of pretending the link was not opened.
 */
const val MESSAGE_SESSION_EXPIRED =
    "No pudimos confirmar el cambio. Si ya abriste el enlace, cierra sesión e inicia con tu nuevo correo."

data class VerifyEmailLinkUiState(
    val newEmail: String = "",
    val isChecking: Boolean = false,
    val isResending: Boolean = false,
    /** Shown in red where the design shows "Código incorrecto"; the button then reads "REINTENTAR". */
    val error: String? = null,
    val resentNotice: Boolean = false,
)

sealed interface VerifyEmailLinkEvent {
    data object EmailUpdated : VerifyEmailLinkEvent
}

class VerifyEmailLinkViewModel(
    private val accountRepository: AccountRepository,
    newEmail: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerifyEmailLinkUiState(newEmail = newEmail))
    val uiState: StateFlow<VerifyEmailLinkUiState> = _uiState.asStateFlow()

    private val _events = Channel<VerifyEmailLinkEvent>(Channel.BUFFERED)
    val events: Flow<VerifyEmailLinkEvent> = _events.receiveAsFlow()

    /** "YA VERIFIQUÉ MI CORREO" / "REINTENTAR": asks Firebase whether the account email is now the new one. */
    fun onCheckClicked() {
        val state = _uiState.value
        if (state.isChecking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, error = null, resentNotice = false) }
            try {
                if (accountRepository.isEmailChanged(state.newEmail)) {
                    _uiState.update { it.copy(isChecking = false) }
                    _events.send(VerifyEmailLinkEvent.EmailUpdated)
                } else {
                    _uiState.update { it.copy(isChecking = false, error = MESSAGE_LINK_NOT_VERIFIED) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthError.NetworkError) {
                _uiState.update { it.copy(isChecking = false, error = "Sin conexión a internet") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isChecking = false, error = MESSAGE_SESSION_EXPIRED) }
            }
        }
    }

    fun onResendClicked() {
        val state = _uiState.value
        if (state.isResending) return
        viewModelScope.launch {
            _uiState.update { it.copy(isResending = true, error = null, resentNotice = false) }
            try {
                accountRepository.requestEmailChange(state.newEmail)
                _uiState.update { it.copy(isResending = false, resentNotice = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthError.NetworkError) {
                _uiState.update { it.copy(isResending = false, error = "Sin conexión a internet") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isResending = false, error = MESSAGE_GENERIC_ERROR) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val route = createSavedStateHandle().toRoute<VerifyEmailLink>()
                VerifyEmailLinkViewModel(application.container.accountRepository, route.newEmail)
            }
        }
    }
}
