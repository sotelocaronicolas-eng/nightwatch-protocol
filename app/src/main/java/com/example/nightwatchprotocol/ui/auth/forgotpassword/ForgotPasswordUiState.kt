package com.example.nightwatchprotocol.ui.auth.forgotpassword

data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val isEmailSent: Boolean = false,
    val invalidEmailError: Boolean = false,
    val networkError: Boolean = false,
)
