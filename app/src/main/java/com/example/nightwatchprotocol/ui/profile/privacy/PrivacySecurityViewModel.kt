package com.example.nightwatchprotocol.ui.profile.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.biometric.BiometricAvailability
import com.example.nightwatchprotocol.data.biometric.BiometricCapabilities
import com.example.nightwatchprotocol.data.preferences.UserPreferences
import com.example.nightwatchprotocol.data.preferences.UserSetting
import com.example.nightwatchprotocol.data.device.DeviceRepository
import com.example.nightwatchprotocol.data.profile.DataExportWriter
import com.example.nightwatchprotocol.data.profile.ProfileDataExporter
import com.example.nightwatchprotocol.data.profile.ProfileRepository
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrivacySecurityUiState(
    /** Null while loading, when there is none, or when it could not be read. */
    val recoveryEmail: String? = null,
    /** Devices with an open session in the cooperative registry; null while loading or when it cannot be read. */
    val activeDevices: Int? = null,
    val hidePublicProgress: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val biometricError: String? = null,
    val exportStatus: ExportStatus = ExportStatus.Idle,
)

enum class ExportStatus { Idle, InProgress, Done, Failed }

const val MESSAGE_EXPORT_DONE = "Tus datos se guardaron en el archivo que elegiste."
const val MESSAGE_EXPORT_FAILED = "No se pudo guardar el archivo. Inténtalo de nuevo."

const val MESSAGE_NO_FINGERPRINT_ENROLLED = "No hay una huella registrada en este teléfono"
const val MESSAGE_NO_FINGERPRINT_READER = "Este teléfono no tiene lector de huella"
const val MESSAGE_READER_UNAVAILABLE = "El lector no está disponible ahora, inténtalo más tarde"
const val MESSAGE_FINGERPRINT_NOT_VERIFIED = "No se pudo verificar tu huella"

sealed interface PrivacySecurityEvent {
    /** The screen must show the system prompt that proves the user's fingerprint works before the lock is turned on. */
    data object VerifyBiometric : PrivacySecurityEvent
}

class PrivacySecurityViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val userPreferences: UserPreferences,
    private val biometricCapabilities: BiometricCapabilities,
    private val dataExporter: ProfileDataExporter,
    private val dataExportWriter: DataExportWriter,
    private val deviceRepository: DeviceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacySecurityUiState())
    val uiState: StateFlow<PrivacySecurityUiState> = _uiState.asStateFlow()

    private val _events = Channel<PrivacySecurityEvent>(Channel.BUFFERED)
    val events: Flow<PrivacySecurityEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            userPreferences.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        hidePublicProgress = settings[UserSetting.HIDE_PUBLIC_PROGRESS],
                        biometricLockEnabled = settings[UserSetting.BIOMETRIC_LOCK],
                    )
                }
            }
        }
        refreshRecoveryEmail()
        refreshDevices()
    }

    /** Counts the devices that have not been asked to sign out. */
    fun refreshDevices() {
        val uid = authRepository.currentUserUid ?: return
        viewModelScope.launch {
            val devices = runCatchingKeepCancellation { deviceRepository.listDevices(uid) }.getOrNull()
            _uiState.update { it.copy(activeDevices = devices?.count { device -> !device.revoked }) }
        }
    }

    /** Re-reads the recovery email, which may have just been saved on its own screen. */
    fun refreshRecoveryEmail() {
        val uid = authRepository.currentUserUid ?: return
        viewModelScope.launch {
            val profile = runCatchingKeepCancellation { profileRepository.getProfile(uid) }.getOrNull()
            val recovery = profile?.recoveryEmail?.trim()?.takeIf { it.isNotEmpty() }
            _uiState.update { it.copy(recoveryEmail = recovery) }
        }
    }

    fun onHidePublicProgressChanged(hide: Boolean) {
        viewModelScope.launch { userPreferences.set(UserSetting.HIDE_PUBLIC_PROGRESS, hide) }
    }

    /**
     * Turning the lock off is immediate. Turning it on first checks that a fingerprint can be used and then asks the
     * screen to show a test prompt; without an enrolled fingerprint no prompt is shown and the switch stays off.
     */
    fun onBiometricLockToggled(enable: Boolean) {
        if (!enable) {
            _uiState.update { it.copy(biometricError = null) }
            viewModelScope.launch { userPreferences.set(UserSetting.BIOMETRIC_LOCK, false) }
            return
        }
        val error = when (biometricCapabilities.availability()) {
            BiometricAvailability.Available -> null
            BiometricAvailability.NoneEnrolled -> MESSAGE_NO_FINGERPRINT_ENROLLED
            BiometricAvailability.NoHardware -> MESSAGE_NO_FINGERPRINT_READER
            BiometricAvailability.TemporarilyUnavailable, BiometricAvailability.Unsupported -> MESSAGE_READER_UNAVAILABLE
        }
        _uiState.update { it.copy(biometricError = error) }
        if (error == null) viewModelScope.launch { _events.send(PrivacySecurityEvent.VerifyBiometric) }
    }

    /** The user picked where to save the file ([destination] is the document URI as text); null means they cancelled. */
    fun onExportDestinationChosen(destination: String?) {
        val uid = authRepository.currentUserUid
        if (destination == null || _uiState.value.exportStatus == ExportStatus.InProgress) return
        if (uid == null) {
            _uiState.update { it.copy(exportStatus = ExportStatus.Failed) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(exportStatus = ExportStatus.InProgress) }
            val result = runCatchingKeepCancellation { dataExportWriter.write(destination, dataExporter.buildJson(uid)) }
            _uiState.update { it.copy(exportStatus = if (result.isSuccess) ExportStatus.Done else ExportStatus.Failed) }
        }
    }

    /** The result of the test prompt shown after [PrivacySecurityEvent.VerifyBiometric]. */
    fun onBiometricVerified(success: Boolean) {
        if (success) {
            _uiState.update { it.copy(biometricError = null) }
            viewModelScope.launch { userPreferences.set(UserSetting.BIOMETRIC_LOCK, true) }
        } else {
            _uiState.update { it.copy(biometricError = MESSAGE_FINGERPRINT_NOT_VERIFIED) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                PrivacySecurityViewModel(
                    container.authRepository,
                    container.profileRepository,
                    container.userPreferences,
                    container.biometricCapabilities,
                    container.profileDataExporter,
                    container.dataExportWriter,
                    container.deviceRepository,
                )
            }
        }
    }
}
