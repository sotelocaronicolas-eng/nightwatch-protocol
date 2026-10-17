package com.example.nightwatchprotocol.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StartRoute { Login, PostAuthGate }

/**
 * Decides the first screen only once the stored session is readable: the activity keeps the splash screen up
 * while [startRoute] is null. Reading the user before [AuthRepository.awaitSessionReady] returns could send a
 * signed-in user to Login.
 */
class StartupViewModel(
    authRepository: AuthRepository,
    sessionPreferences: SessionPreferences,
) : ViewModel() {

    private val _startRoute = MutableStateFlow<StartRoute?>(null)
    val startRoute: StateFlow<StartRoute?> = _startRoute.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.awaitSessionReady()
            // A password recovery that was interrupted left a session open without the user ever typing a password:
            // close it (and wait for the sign-out to finish) before deciding the route, and go to Login.
            if (sessionPreferences.isRecoveryInProgress()) {
                authRepository.signOut()
                authRepository.awaitSessionReady()
                sessionPreferences.setRecoveryInProgress(false)
                _startRoute.value = StartRoute.Login
                return@launch
            }
            _startRoute.value = if (authRepository.currentUserEmail != null) StartRoute.PostAuthGate else StartRoute.Login
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                StartupViewModel(application.container.authRepository, application.container.sessionPreferences)
            }
        }
    }
}
