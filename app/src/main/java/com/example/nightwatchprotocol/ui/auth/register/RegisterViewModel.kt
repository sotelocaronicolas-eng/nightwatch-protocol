package com.example.nightwatchprotocol.ui.auth.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.auth.GoogleSignInAttempt
import com.example.nightwatchprotocol.data.session.SessionPreferences
import com.example.nightwatchprotocol.ui.auth.login.ErrorDialogKind
import com.example.nightwatchprotocol.ui.auth.login.googleErrorKind
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RegisterViewModel(
    private val authRepository: AuthRepository,
    private val sessionPreferences: SessionPreferences,
    val googleWebClientId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = Channel<RegisterEvent>(Channel.BUFFERED)
    val events: Flow<RegisterEvent> = _events.receiveAsFlow()

    private val googleAttempt = GoogleSignInAttempt(authRepository)

    fun onFullNameChanged(value: String) = _uiState.update { it.copy(fullName = value, fieldError = null) }
    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, fieldError = null) }
    fun onPasswordChanged(value: String) = _uiState.update { it.copy(password = value, fieldError = null) }
    fun onConfirmPasswordChanged(value: String) =
        _uiState.update { it.copy(confirmPassword = value, fieldError = null) }

    fun onRegisterClicked() {
        val state = _uiState.value
        if (state.isLoading) return
        if (state.fullName.isBlank() || state.email.isBlank() ||
            state.password.isBlank() || state.confirmPassword.isBlank()
        ) {
            return
        }
        if (state.password != state.confirmPassword) {
            _uiState.update { it.copy(fieldError = RegisterFieldError.PasswordMismatch) }
            return
        }
        if (!state.passwordRules.allSatisfied) {
            _uiState.update { it.copy(fieldError = RegisterFieldError.WeakPassword) }
            return
        }

        _uiState.update { it.copy(isLoading = true, fieldError = null) }
        viewModelScope.launch {
            try {
                authRepository.register(state.fullName.trim(), state.email.trim(), state.password)
                sessionPreferences.setRememberSession(true)
                _uiState.update { it.copy(isLoading = false) }
                _events.send(RegisterEvent.NavigateToMainMenu)
            } catch (error: AuthError.EmailConfirmationRequired) {
                // Not a failure: with "Confirm email" on, the account waits for the code that was just emailed.
                sessionPreferences.setRememberSession(true)
                _uiState.update { it.copy(isLoading = false) }
                _events.send(RegisterEvent.NavigateToVerifyCode(state.email.trim()))
            } catch (error: AuthError) {
                handleRegisterError(error)
            }
        }
    }

    fun onNavigateBackClicked() {
        viewModelScope.launch { _events.send(RegisterEvent.NavigateBack) }
    }

    /** The screen is about to open Google's account chooser; returns the hashed nonce for its request, or null. */
    fun onGoogleSignInStarted(): String? = googleAttempt.begin()

    fun onGoogleIdTokenReceived(idToken: String) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true) }
        val nonce = googleAttempt.finish()
        viewModelScope.launch {
            try {
                authRepository.signInWithGoogle(idToken, nonce)
                sessionPreferences.setRememberSession(true)
                _uiState.update { it.copy(isLoading = false) }
                _events.send(RegisterEvent.NavigateToMainMenu)
            } catch (error: AuthError) {
                _uiState.update { it.copy(isLoading = false) }
                _events.send(RegisterEvent.ShowErrorDialog(googleErrorKind(authRepository, error)))
            }
        }
    }

    /** The user closed the account chooser: no message, except on Firebase, which keeps its generic dialog. */
    fun onGoogleSignInCancelled() {
        if (!authRepository.usesGoogleNonce) onGoogleSignInFailed()
    }

    fun onGoogleSignInFailed() {
        viewModelScope.launch { _events.send(RegisterEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN)) }
    }

    private suspend fun handleRegisterError(error: AuthError) {
        val fieldError = when (error) {
            AuthError.EmailAlreadyInUse -> RegisterFieldError.EmailAlreadyInUse
            AuthError.GoogleAccountOnly -> RegisterFieldError.EmailRegisteredWithGoogle
            AuthError.InvalidEmail -> RegisterFieldError.InvalidEmail
            AuthError.WeakPassword -> RegisterFieldError.WeakPassword
            else -> null
        }
        _uiState.update { it.copy(isLoading = false, fieldError = fieldError) }
        if (fieldError == null) {
            val kind = if (error == AuthError.NetworkError) ErrorDialogKind.NETWORK else ErrorDialogKind.UNKNOWN
            _events.send(RegisterEvent.ShowErrorDialog(kind))
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                RegisterViewModel(
                    application.container.authRepository,
                    application.container.sessionPreferences,
                    application.container.googleWebClientId,
                )
            }
        }
    }
}
