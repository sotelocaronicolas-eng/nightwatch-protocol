package com.example.nightwatchprotocol.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthRepository
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
) : ViewModel() {

    private val _startRoute = MutableStateFlow<StartRoute?>(null)
    val startRoute: StateFlow<StartRoute?> = _startRoute.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.awaitSessionReady()
            _startRoute.value = if (authRepository.currentUserEmail != null) StartRoute.PostAuthGate else StartRoute.Login
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                StartupViewModel(application.container.authRepository)
            }
        }
    }
}
