package com.example.nightwatchprotocol.ui.auth.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nightwatchprotocol.NightWatchApplication
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState = _uiState.asStateFlow()

    fun onEmailChanged(value: String) {
        _uiState.update {
            it.copy(email = value, invalidEmailError = false, networkError = false, isEmailSent = false)
        }
    }

    fun onSendClicked() {
        val state = _uiState.value
        if (state.isLoading || state.email.isBlank()) return

        _uiState.update { it.copy(isLoading = true, invalidEmailError = false, networkError = false) }
        viewModelScope.launch {
            try {
                authRepository.sendPasswordResetEmail(state.email.trim())
                _uiState.update { it.copy(isLoading = false, isEmailSent = true) }
            } catch (error: AuthError) {
                when (error) {
                    AuthError.InvalidEmail -> _uiState.update { it.copy(isLoading = false, invalidEmailError = true) }
                    AuthError.NetworkError -> _uiState.update { it.copy(isLoading = false, networkError = true) }
                    else -> _uiState.update { it.copy(isLoading = false, isEmailSent = true) }
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NightWatchApplication
                ForgotPasswordViewModel(application.container.authRepository)
            }
        }
    }
}
