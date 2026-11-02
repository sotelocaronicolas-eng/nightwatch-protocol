package com.example.nightwatchprotocol.ui.profile.recoveryemail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.data.profile.ProfileRepository
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.ui.common.toUiText
import com.example.nightwatchprotocol.ui.profile.isPlausibleEmail
import com.example.nightwatchprotocol.ui.verifycode.TEXT_WAIT_BEFORE_NEW_CODE
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val MESSAGE_RECOVERY_EMAIL_INVALID: UiText = UiText.of(R.string.common_invalid_email)
val MESSAGE_RECOVERY_EMAIL_NOT_SAVED: UiText = UiText.of(R.string.profile_error_recovery_email_not_saved)
val MESSAGE_RECOVERY_EMAIL_OFFLINE: UiText = UiText.of(R.string.profile_error_recovery_email_offline)

data class RecoveryEmailUiState(
    val email: String = "",
    val isSaving: Boolean = false,
    val error: UiText? = null,
)

/**
 * Why asking for the code failed. One short message per kind of failure, the technical cause is never shown. The
 * seconds, when the server gave them, are in the text.
 */
fun recoveryEmailCodeErrorMessage(error: Throwable?): UiText = when (error) {
    is AuthError.RateLimited ->
        error.retryAfterSeconds?.let { UiText.of(R.string.profile_error_wait_seconds, it) } ?: TEXT_WAIT_BEFORE_NEW_CODE
    AuthError.SameAsPrimaryEmail -> AuthError.SameAsPrimaryEmail.toUiText()
    AuthError.EmailSendFailed -> AuthError.EmailSendFailed.toUiText()
    AuthError.InvalidEmail -> MESSAGE_RECOVERY_EMAIL_INVALID
    AuthError.NetworkError -> MESSAGE_RECOVERY_EMAIL_OFFLINE
    else -> MESSAGE_RECOVERY_EMAIL_NOT_SAVED
}

sealed interface RecoveryEmailEvent {
    /** A 6-digit code was sent to [email]; the next screen is where it is typed, and it saves the email when accepted. */
    data class CodeSent(val email: String) : RecoveryEmailEvent
}

class RecoveryEmailViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecoveryEmailUiState())
    val uiState: StateFlow<RecoveryEmailUiState> = _uiState.asStateFlow()

    private val _events = Channel<RecoveryEmailEvent>(Channel.BUFFERED)
    val events: Flow<RecoveryEmailEvent> = _events.receiveAsFlow()

    private var typedByUser = false

    init {
        // Pre-fills the saved address; whatever the user types in the meantime wins over a late answer.
        authRepository.currentUserUid?.let { uid ->
            viewModelScope.launch {
                val saved = runCatchingKeepCancellation { profileRepository.getProfile(uid) }.getOrNull()
                    ?.recoveryEmail?.trim()?.takeIf { it.isNotEmpty() }
                if (saved != null && !typedByUser) _uiState.update { it.copy(email = saved) }
            }
        }
    }

    fun onEmailChanged(value: String) {
        typedByUser = true
        _uiState.update { it.copy(email = value, error = null) }
    }

    fun onSaveClicked() {
        val state = _uiState.value
        if (state.isSaving) return
        val email = state.email.trim()
        if (!isPlausibleEmail(email)) {
            _uiState.update { it.copy(error = MESSAGE_RECOVERY_EMAIL_INVALID) }
            return
        }
        if (authRepository.currentUserUid == null) {
            _uiState.update { it.copy(error = MESSAGE_RECOVERY_EMAIL_NOT_SAVED) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            // The address is only saved once the code sent to it is confirmed on the next screen.
            val sent = runCatchingKeepCancellation { profileRepository.requestRecoveryEmailCode(email) }
            if (sent.isSuccess) {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(RecoveryEmailEvent.CodeSent(email))
            } else {
                _uiState.update { it.copy(isSaving = false, error = recoveryEmailCodeErrorMessage(sent.exceptionOrNull())) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                RecoveryEmailViewModel(container.authRepository, container.profileRepository)
            }
        }
    }
}
