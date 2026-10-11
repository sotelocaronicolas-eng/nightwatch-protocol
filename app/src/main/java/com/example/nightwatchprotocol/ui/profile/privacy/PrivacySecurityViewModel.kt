package com.example.nightwatchprotocol.ui.profile.privacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.preferences.UserPreferences
import com.example.nightwatchprotocol.data.preferences.UserSetting
import com.example.nightwatchprotocol.data.profile.FixedProfileData
import com.example.nightwatchprotocol.data.profile.ProfileRepository
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrivacySecurityUiState(
    /** Null while loading, when there is none, or when it could not be read. */
    val recoveryEmail: String? = null,
    val activeDevices: Int = FixedProfileData.ACTIVE_DEVICES,
    val hidePublicProgress: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val biometricError: String? = null,
)

class PrivacySecurityViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrivacySecurityUiState())
    val uiState: StateFlow<PrivacySecurityUiState> = _uiState.asStateFlow()

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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                val container = application.container
                PrivacySecurityViewModel(
                    container.authRepository,
                    container.profileRepository,
                    container.userPreferences,
                )
            }
        }
    }
}
