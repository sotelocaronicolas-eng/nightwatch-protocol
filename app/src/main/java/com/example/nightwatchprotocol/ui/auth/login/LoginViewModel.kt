package com.example.nightwatchprotocol.ui.auth.login

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

    private val googleAttempt = GoogleSignInAttempt(authRepository)

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
            } catch (error: AuthError.EmailNotConfirmed) {
                continueToVerification(username, state.rememberSession)
            } catch (error: AuthError) {
                handleSignInError(error)
            }
        }
    }

    /**
     * A user who never confirmed the email ends up on the same code screen as a new sign-up, with a fresh code. If
     * the new code cannot be sent because of the connection, say so; any other failure (e.g. a code was sent a
     * moment ago) still goes on: that code is valid, and the screen can resend.
     */
    private suspend fun continueToVerification(email: String, rememberSession: Boolean) {
        try {
            authRepository.resendSignUpCode(email)
        } catch (error: AuthError.NetworkError) {
            _uiState.update { it.copy(isLoading = false) }
            _events.send(LoginEvent.ShowErrorDialog(ErrorDialogKind.NETWORK))
            return
        } catch (error: AuthError) {
            // keep going
        }
        sessionPreferences.setRememberSession(rememberSession)
        _uiState.update { it.copy(isLoading = false) }
        _events.send(LoginEvent.NavigateToVerifyCode(email))
    }

    /** The screen is about to open Google's account chooser; returns the hashed nonce for its request, or null. */
    fun onGoogleSignInStarted(): String? = googleAttempt.begin()

    fun onGoogleIdTokenReceived(idToken: String) {
        val state = _uiState.value
        if (state.isLoading) return
        _uiState.update { it.copy(isLoading = true) }
        val nonce = googleAttempt.finish()
        viewModelScope.launch {
            try {
                authRepository.signInWithGoogle(idToken, nonce)
                sessionPreferences.setRememberSession(state.rememberSession)
                _uiState.update { it.copy(isLoading = false) }
                _events.send(LoginEvent.NavigateToMainMenu)
            } catch (error: AuthError) {
                _uiState.update { it.copy(isLoading = false) }
                _events.send(LoginEvent.ShowErrorDialog(googleErrorKind(authRepository, error)))
            }
        }
    }

    /** The user closed the account chooser: no message, except on Firebase, which keeps its generic dialog. */
    fun onGoogleSignInCancelled() {
        if (!authRepository.usesGoogleNonce) onGoogleSignInFailed()
    }

    fun onGoogleSignInFailed() {
        viewModelScope.launch { _events.send(LoginEvent.ShowErrorDialog(ErrorDialogKind.UNKNOWN)) }
    }

    fun onRegisterLinkClicked() {
        viewModelScope.launch { _events.send(LoginEvent.NavigateToRegister) }
    }

    fun onForgotPasswordClicked() {
        viewModelScope.launch {
            _events.send(
                if (authRepository.recoversPasswordWithCode) {
                    LoginEvent.OpenRecoveryDialog(_uiState.value.usernameOrEmail.trim())
                } else {
                    LoginEvent.NavigateToForgotPassword
                },
            )
        }
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
