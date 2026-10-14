package com.example.nightwatchprotocol.ui.profile.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.device.DeviceRecord
import com.example.nightwatchprotocol.data.device.DeviceRepository
import com.example.nightwatchprotocol.data.device.DeviceSessionManager
import com.example.nightwatchprotocol.data.device.SessionCheck
import com.example.nightwatchprotocol.data.device.relativeTimeLabel
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MESSAGE_DEVICES_LOAD_FAILED = "No pudimos cargar tus dispositivos. Revisa tu conexión e inténtalo de nuevo."
const val MESSAGE_DEVICE_SIGN_OUT_FAILED = "No pudimos cerrar la sesión en ese dispositivo. Inténtalo de nuevo."
const val MESSAGE_NO_OTHER_DEVICES = "No hay otros dispositivos con sesión abierta."

/**
 * Shown on the screen and on the confirmation: signing out another device is a request that device honours the
 * next time its app opens. It does NOT revoke the Firebase session (a client-only limitation).
 */
const val DEVICES_LIMITATION_NOTICE =
    "Cerrar la sesión en otro dispositivo le pide a NightWatch que se cierre allí la próxima vez que se abra la app. " +
        "No revoca el acceso de tu cuenta de Firebase."

data class DeviceItem(
    val id: String,
    val name: String,
    /** "Actual", "Cierre pendiente" or how long ago the device was last seen. */
    val statusLabel: String,
    val isCurrent: Boolean,
    val isRevoked: Boolean,
)

data class DevicesUiState(
    val devices: List<DeviceItem> = emptyList(),
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
)

sealed interface DevicesEvent {
    /** One or more other devices were asked to sign out. */
    data object DeviceSignedOut : DevicesEvent

    /** This very device turned out to be revoked while the list was loading, so its session was closed. */
    data object SessionEnded : DevicesEvent
}

class DevicesViewModel(
    private val authRepository: AuthRepository,
    private val deviceRepository: DeviceRepository,
    private val sessionManager: DeviceSessionManager,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DevicesUiState())
    val uiState: StateFlow<DevicesUiState> = _uiState.asStateFlow()

    private val _events = Channel<DevicesEvent>(Channel.BUFFERED)
    val events: Flow<DevicesEvent> = _events.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh(keepError: Boolean = false) {
        val uid = authRepository.currentUserUid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = if (keepError) it.error else null) }
            // Registers this device first, so it always shows up (and honours a revocation that arrived meanwhile).
            if (sessionManager.onAppActive() == SessionCheck.Revoked) {
                _events.send(DevicesEvent.SessionEnded)
                return@launch
            }
            val currentId = sessionManager.currentDeviceId()
            val result = runCatchingKeepCancellation { deviceRepository.listDevices(uid) }
            result.onSuccess { records ->
                _uiState.update { it.copy(isLoading = false, devices = records.toItems(currentId)) }
            }.onFailure {
                _uiState.update { it.copy(isLoading = false, error = MESSAGE_DEVICES_LOAD_FAILED) }
            }
        }
    }

    /** Tapping another device asks it to sign out. The current device and already-requested ones do nothing. */
    fun onDeviceClicked(deviceId: String) {
        val item = _uiState.value.devices.firstOrNull { it.id == deviceId } ?: return
        if (item.isCurrent || item.isRevoked) return
        revoke(listOf(item))
    }

    /** "CERRAR SESIÓN EN TODOS": every device except this one. */
    fun onSignOutAllClicked() {
        val others = _uiState.value.devices.filter { !it.isCurrent && !it.isRevoked }
        if (others.isEmpty()) {
            _uiState.update { it.copy(notice = MESSAGE_NO_OTHER_DEVICES, error = null) }
            return
        }
        revoke(others)
    }

    private fun revoke(targets: List<DeviceItem>) {
        val uid = authRepository.currentUserUid ?: return
        if (_uiState.value.isWorking) return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null, notice = null) }
            val failures = targets.count { target ->
                runCatchingKeepCancellation { deviceRepository.setRevoked(uid, target.id, true) }.isFailure
            }
            _uiState.update { it.copy(isWorking = false) }
            if (failures == 0) {
                _events.send(DevicesEvent.DeviceSignedOut)
            } else {
                _uiState.update { it.copy(error = MESSAGE_DEVICE_SIGN_OUT_FAILED) }
            }
            refresh(keepError = failures > 0)
        }
    }

    private fun List<DeviceRecord>.toItems(currentId: String): List<DeviceItem> {
        val now = clock()
        return sortedWith(compareByDescending<DeviceRecord> { it.id == currentId }.thenByDescending { it.lastSeenMillis ?: 0L })
            .map { record ->
                val isCurrent = record.id == currentId
                DeviceItem(
                    id = record.id,
                    name = record.name.ifBlank { "Dispositivo sin nombre" },
                    statusLabel = when {
                        isCurrent -> "Actual"
                        record.revoked -> "Cierre pendiente"
                        else -> relativeTimeLabel(record.lastSeenMillis, now)
                    },
                    isCurrent = isCurrent,
                    isRevoked = record.revoked,
                )
            }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                DevicesViewModel(container.authRepository, container.deviceRepository, container.deviceSessionManager)
            }
        }
    }
}
