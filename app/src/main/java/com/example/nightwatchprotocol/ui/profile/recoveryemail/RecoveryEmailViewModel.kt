package com.example.nightwatchprotocol.ui.profile.recoveryemail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.firestore.FailureKind
import com.example.nightwatchprotocol.data.firestore.FirestoreAccessException
import com.example.nightwatchprotocol.data.profile.ProfileRepository
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.ui.profile.isPlausibleEmail
import com.example.nightwatchprotocol.ui.verifycode.MESSAGE_WAIT_BEFORE_NEW_CODE
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MESSAGE_RECOVERY_EMAIL_INVALID = "Correo electrónico inválido"
const val MESSAGE_RECOVERY_EMAIL_NOT_SAVED = "No se pudo guardar el correo. Inténtalo de nuevo."
const val MESSAGE_RECOVERY_EMAIL_OFFLINE = "Sin conexión a internet. Revisa tu conexión e inténtalo de nuevo."
const val MESSAGE_RECOVERY_EMAIL_PERMISSION =
    "No pudimos guardar el correo por un problema de permisos de tu cuenta. Inténtalo más tarde."

/** One short message per kind of failure; the technical cause is never shown to the user. */
fun recoveryEmailErrorMessage(error: Throwable?): String = when ((error as? FirestoreAccessException)?.kind) {
    FailureKind.Offline -> MESSAGE_RECOVERY_EMAIL_OFFLINE
    FailureKind.PermissionDenied -> MESSAGE_RECOVERY_EMAIL_PERMISSION
    FailureKind.Unknown, null -> MESSAGE_RECOVERY_EMAIL_NOT_SAVED
}

data class RecoveryEmailUiState(
    val email: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
)

/** Why asking for the code failed (Supabase). The seconds, when the server gave them, are in the text. */
fun recoveryEmailCodeErrorMessage(error: Throwable?): String = when (error) {
    is AuthError.RateLimited ->
        error.retryAfterSeconds?.let { "Espera $it s antes de pedir otro código." } ?: MESSAGE_WAIT_BEFORE_NEW_CODE
    AuthError.SameAsPrimaryEmail -> AuthError.SameAsPrimaryEmail.message.orEmpty()
    AuthError.EmailSendFailed -> AuthError.EmailSendFailed.message.orEmpty()
    AuthError.InvalidEmail -> MESSAGE_RECOVERY_EMAIL_INVALID
    AuthError.NetworkError -> MESSAGE_RECOVERY_EMAIL_OFFLINE
    else -> MESSAGE_RECOVERY_EMAIL_NOT_SAVED
}

sealed interface RecoveryEmailEvent {
    /** The email was saved directly (Firebase). */
    data object Saved : RecoveryEmailEvent

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
        val uid = authRepository.currentUserUid
        if (uid == null) {
            _uiState.update { it.copy(error = MESSAGE_RECOVERY_EMAIL_NOT_SAVED) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            if (profileRepository.verifiesRecoveryEmailWithCode) {
                // The address is only saved once the code sent to it is confirmed on the next screen.
                val sent = runCatchingKeepCancellation { profileRepository.requestRecoveryEmailCode(email) }
                if (sent.isSuccess) {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(RecoveryEmailEvent.CodeSent(email))
                } else {
                    _uiState.update { it.copy(isSaving = false, error = recoveryEmailCodeErrorMessage(sent.exceptionOrNull())) }
                }
                return@launch
            }
            val result = runCatchingKeepCancellation { profileRepository.saveRecoveryEmail(uid, email) }
            if (result.isSuccess) {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(RecoveryEmailEvent.Saved)
            } else {
                _uiState.update { it.copy(isSaving = false, error = recoveryEmailErrorMessage(result.exceptionOrNull())) }
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
