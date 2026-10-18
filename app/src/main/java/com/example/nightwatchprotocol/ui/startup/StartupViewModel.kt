package com.example.nightwatchprotocol.ui.startup

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import kotlin.coroutines.cancellation.CancellationException
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
    private val authRepository: AuthRepository,
    private val sessionPreferences: SessionPreferences,
    /** Receives only the kind of failure (the exception class name), never its message or any data. */
    private val reportFailure: (category: String) -> Unit = { Log.w(TAG, "Arranque: falló ($it), se va al login") },
) : ViewModel() {

    private val _startRoute = MutableStateFlow<StartRoute?>(null)
    val startRoute: StateFlow<StartRoute?> = _startRoute.asStateFlow()

    init {
        viewModelScope.launch {
            // The splash waits for this value, so it must always be set: any failure of the stored session or of the
            // local preferences ends at Login (and the session is closed, as it may be a recovery one).
            _startRoute.value = try {
                decideRoute()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportFailure(e::class.java.simpleName)
                runCatching { authRepository.signOut() }
                StartRoute.Login
            }
        }
    }

    private suspend fun decideRoute(): StartRoute {
        authRepository.awaitSessionReady()
        // A password recovery that was interrupted left a session open without the user ever typing a password:
        // close it (and wait for the sign-out to finish) before deciding the route, and go to Login.
        if (sessionPreferences.isRecoveryInProgress()) {
            authRepository.signOut()
            authRepository.awaitSessionReady()
            sessionPreferences.setRecoveryInProgress(false)
            return StartRoute.Login
        }
        return if (authRepository.currentUserEmail != null) StartRoute.PostAuthGate else StartRoute.Login
    }

    companion object {
        private const val TAG = "StartupViewModel"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                StartupViewModel(application.container.authRepository, application.container.sessionPreferences)
            }
        }
    }
}
