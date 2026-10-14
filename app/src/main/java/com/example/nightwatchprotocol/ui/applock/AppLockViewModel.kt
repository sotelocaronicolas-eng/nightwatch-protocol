package com.example.nightwatchprotocol.ui.applock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.biometric.BiometricAvailability
import com.example.nightwatchprotocol.data.biometric.BiometricCapabilities
import com.example.nightwatchprotocol.data.biometric.shouldLockOnColdStart
import com.example.nightwatchprotocol.data.biometric.shouldLockOnReturn
import com.example.nightwatchprotocol.data.preferences.UserPreferences
import com.example.nightwatchprotocol.data.preferences.UserSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class AppLockUiState(
    val isLocked: Boolean,
    /**
     * False only while the app started locked and has not been unlocked yet: the navigation graph is then
     * not composed at all, so no screen can show for even a moment. After the first unlock it stays composed
     * (a later re-lock draws the lock screen over it, so the user's place is kept).
     */
    val showContent: Boolean,
)

/**
 * Scoped to the activity: it survives rotation but not a new process, so a new process counts as a cold start.
 * The activity reports when it goes to the background and comes back; the policy lives in `AppLockPolicy`.
 */
class AppLockViewModel(
    private val authRepository: AuthRepository,
    private val userPreferences: UserPreferences,
    private val capabilities: BiometricCapabilities,
    initialLockEnabled: Boolean,
) : ViewModel() {

    private var lockEnabled = initialLockEnabled
    private var backgroundedAtMillis: Long? = null

    private val _uiState = MutableStateFlow(
        shouldLockOnColdStart(signedIn = authRepository.currentUserUid != null, lockEnabled = initialLockEnabled)
            .let { lockedAtStart -> AppLockUiState(isLocked = lockedAtStart, showContent = !lockedAtStart) },
    )
    val uiState: StateFlow<AppLockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            userPreferences.settings.collect { lockEnabled = it[UserSetting.BIOMETRIC_LOCK] }
        }
    }

    fun onActivityStopped(nowMillis: Long) {
        if (backgroundedAtMillis == null) backgroundedAtMillis = nowMillis
    }

    fun onActivityStarted(nowMillis: Long) {
        val awaySince = backgroundedAtMillis
        backgroundedAtMillis = null
        val signedIn = authRepository.currentUserUid != null
        if (!_uiState.value.isLocked && shouldLockOnReturn(signedIn, lockEnabled, awaySince, nowMillis)) {
            _uiState.update { it.copy(isLocked = true) }
        }
    }

    /**
     * Called by the lock screen before it shows the system prompt. Returns true when the prompt can be shown.
     * When the phone no longer has a usable biometric, the lock turns itself off so the user is never locked
     * out of their own account (decision P15c) and the app opens.
     */
    fun requestUnlock(): Boolean {
        if (!_uiState.value.isLocked) return false
        if (capabilities.availability() != BiometricAvailability.Available) {
            lockEnabled = false
            viewModelScope.launch { userPreferences.set(UserSetting.BIOMETRIC_LOCK, false) }
            unlock()
            return false
        }
        return true
    }

    fun onAuthenticationSucceeded() = unlock()

    private fun unlock() {
        _uiState.value = AppLockUiState(isLocked = false, showContent = true)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                // Read synchronously, like the remembered-session flag, so nothing shows before the lock decision.
                val lockEnabled = runBlocking { container.userPreferences.settings.first()[UserSetting.BIOMETRIC_LOCK] }
                AppLockViewModel(
                    container.authRepository,
                    container.userPreferences,
                    container.biometricCapabilities,
                    lockEnabled,
                )
            }
        }
    }
}
