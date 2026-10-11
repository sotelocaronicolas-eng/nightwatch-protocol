package com.example.nightwatchprotocol.ui.profile.confirmidentity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.account.AccountRepository
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.ui.profile.MESSAGE_ENTER_CURRENT_PASSWORD
import com.example.nightwatchprotocol.ui.profile.MESSAGE_GENERIC_ERROR
import com.example.nightwatchprotocol.ui.profile.passwordOperationErrorMessage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConfirmIdentityUiState(
    val password: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface ConfirmIdentityEvent {
    /** The password was right; the previous screen retries the operation it was waiting on. */
    data object Confirmed : ConfirmIdentityEvent
}

class ConfirmIdentityViewModel(
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConfirmIdentityUiState())
    val uiState: StateFlow<ConfirmIdentityUiState> = _uiState.asStateFlow()

    private val _events = Channel<ConfirmIdentityEvent>(Channel.BUFFERED)
    val events: Flow<ConfirmIdentityEvent> = _events.receiveAsFlow()

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun onConfirmClicked() {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.password.isEmpty()) {
            _uiState.update { it.copy(error = MESSAGE_ENTER_CURRENT_PASSWORD) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                accountRepository.reauthenticate(state.password)
                _uiState.update { it.copy(isLoading = false) }
                _events.send(ConfirmIdentityEvent.Confirmed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AuthError) {
                _uiState.update { it.copy(isLoading = false, error = passwordOperationErrorMessage(e)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = MESSAGE_GENERIC_ERROR) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                ConfirmIdentityViewModel(application.container.accountRepository)
            }
        }
    }
}
