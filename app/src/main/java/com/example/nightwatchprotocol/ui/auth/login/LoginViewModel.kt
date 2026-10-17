package com.example.nightwatchprotocol.ui.auth.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository,
    private val sessionPreferences: SessionPreferences,
    val googleWebClientId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    val events: Flow<LoginEvent> = _events.receiveAsFlow()

    fun onUsernameChanged(value: String) {
        _uiState.update { it.copy(usernameOrEmail = value, fieldError = null, showUsernameRequired = false) }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { it.copy(password = value, fieldError = null, showPasswordRequired = false) }
    }

    fun onRememberSessionChanged(value: Boolean) {
        _uiState.update { it.copy(rememberSession = value) }
    }

    fun onSignInClicked() {
        val state = _uiState.value
        if (state.isLoading) return

        val username = state.usernameOrEmail.trim()
        val password = state.password
        if (username.isBlank() || password.isBlank()) {
            _uiState.update {
                it.copy(
                    fieldError = null,
                    showUsernameRequired = username.isBlank(),
                    showPasswordRequired = password.isBlank(),
                )
            }
            return
        }

        _uiState.update {
            it.copy(isLoading = true, fieldError = null, showUsernameRequired = false, showPasswordRequired = false)
        }
        viewModelScope.launch {
            try {
                authRepository.signIn(username, password)
                sessionPreferences.setRememberSession(state.rememberSession)
                _uiState.update { it.copy(isLoading = false) }
                _events.send(LoginEvent.NavigateToMainMenu)
            } catch (error: AuthError) {
                handleSignInError(error)
            }
        }
    }

    fun onGoogleIdTokenReceived(idToken: String) {
        val state = _uiState.value
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                authRepository.signInWithGoogle(idToken)
                sessionPreferences.setRememberSession(state.rememberSession)
                _uiState.update { it.copy(isLoading = false) }
                _events.send(LoginEvent.NavigateToMainMenu)
            } catch (error: AuthError) {
                _uiState.update { it.copy(isLoading = false) }
                _events.send(LoginEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN))
            }
        }
    }

    fun onGoogleSignInFailed() {
        viewModelScope.launch { _events.send(LoginEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN)) }
    }

    fun onRegisterLinkClicked() {
        viewModelScope.launch { _events.send(LoginEvent.NavigateToRegister) }
    }

    fun onForgotPasswordClicked() {
        viewModelScope.launch { _events.send(LoginEvent.NavigateToForgotPassword) }
    }

    private suspend fun handleSignInError(error: AuthError) {
        val fieldError = when (error) {
            AuthError.WrongPassword -> LoginFieldError.WrongPassword
            AuthError.UserNotFound -> LoginFieldError.UserNotFound
            AuthError.InvalidCredentials -> LoginFieldError.InvalidCredentials
            AuthError.GoogleAccountOnly -> LoginFieldError.GoogleAccountOnly
            AuthError.InvalidEmail -> LoginFieldError.InvalidEmail
            else -> null
        }
        _uiState.update { it.copy(isLoading = false, fieldError = fieldError) }
        if (fieldError == null) {
            val kind = if (error == AuthError.NetworkError) ErrorDialogKind.NETWORK else ErrorDialogKind.UNKNOWN
            _events.send(LoginEvent.ShowErrorDialog(kind))
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                LoginViewModel(
                    application.container.authRepository,
                    application.container.sessionPreferences,
                    application.container.googleWebClientId,
                )
            }
        }
    }
}
