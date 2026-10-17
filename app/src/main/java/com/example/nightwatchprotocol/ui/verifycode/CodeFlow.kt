package com.example.nightwatchprotocol.ui.verifycode

import androidx.annotation.Keep
import com.example.nightwatchprotocol.data.auth.AuthRepository
import kotlinx.serialization.Serializable

/** Which flow a code screen belongs to. It travels in the navigation route, so it is serializable. */
@Keep
@Serializable
enum class CodePurpose {
    /** Confirming the email of a new account (Supabase "Confirm email"). */
    SIGN_UP,
}

/** What the code screen does: check the code, and send a new one. Both throw `AuthError` on failure. */
interface CodeFlow {
    suspend fun confirm(code: String)
    suspend fun resend()
}

class SignUpCodeFlow(private val authRepository: AuthRepository, private val email: String) : CodeFlow {
    override suspend fun confirm(code: String) = authRepository.verifySignUpCode(email, code)
    override suspend fun resend() = authRepository.resendSignUpCode(email)
}

fun codeFlowFor(purpose: CodePurpose, email: String, authRepository: AuthRepository): CodeFlow = when (purpose) {
    CodePurpose.SIGN_UP -> SignUpCodeFlow(authRepository, email)
}
