package com.example.nightwatchprotocol.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.device.DeviceSessionManager
import com.example.nightwatchprotocol.data.device.SessionCheck
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Do not ask the server on every quick app switch: at most once in this many milliseconds. */
const val MIN_DEVICE_CHECK_INTERVAL_MILLIS = 30_000L

/**
 * Scoped to the activity. When the app opens or comes back to the front it lets [DeviceSessionManager] check
 * whether another device asked this one to sign out; if so, [sessionEndedByAnotherDevice] turns true so the
 * activity can go back to Login and tell the user.
 */
class DeviceSessionViewModel(
    private val sessionManager: DeviceSessionManager,
) : ViewModel() {

    private val _sessionEndedByAnotherDevice = MutableStateFlow(false)
    val sessionEndedByAnotherDevice: StateFlow<Boolean> = _sessionEndedByAnotherDevice.asStateFlow()

    private var lastCheckAtMillis: Long? = null

    /** [nowMillis] must come from a monotonic clock. */
    fun onActivityStarted(nowMillis: Long) {
        val last = lastCheckAtMillis
        if (last != null && nowMillis - last < MIN_DEVICE_CHECK_INTERVAL_MILLIS) return
        lastCheckAtMillis = nowMillis
        viewModelScope.launch {
            if (sessionManager.onAppActive() == SessionCheck.Revoked) _sessionEndedByAnotherDevice.value = true
        }
    }

    /** The user acknowledged the notice. */
    fun onNoticeDismissed() {
        _sessionEndedByAnotherDevice.value = false
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                DeviceSessionViewModel(application.container.deviceSessionManager)
            }
        }
    }
}
