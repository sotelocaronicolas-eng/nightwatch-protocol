package com.example.nightwatchprotocol.ui.profile.recoveryemail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.firestore.FailureKind
import com.example.nightwatchprotocol.data.firestore.FirestoreAccessException
import com.example.nightwatchprotocol.data.profile.ProfileRepository
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import com.example.nightwatchprotocol.ui.profile.isPlausibleEmail
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

sealed interface RecoveryEmailEvent {
    data object Saved : RecoveryEmailEvent
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
