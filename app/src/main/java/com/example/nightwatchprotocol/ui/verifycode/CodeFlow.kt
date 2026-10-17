package com.example.nightwatchprotocol.ui.verifycode

import androidx.annotation.Keep
import com.example.nightwatchprotocol.data.account.AccountRepository
import com.example.nightwatchprotocol.data.auth.AuthRepository
import kotlinx.serialization.Serializable

/** Which flow a code screen belongs to. It travels in the navigation route, so it is serializable. */
@Keep
@Serializable
enum class CodePurpose {
    /** Confirming the email of a new account (Supabase "Confirm email"). */
    SIGN_UP,

    /** Confirming the new address of a signed-in user (Supabase, code sent to the new email). */
    EMAIL_CHANGE,
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

/** [email] is the new address, the one the code was sent to. */
class EmailChangeCodeFlow(private val accountRepository: AccountRepository, private val email: String) : CodeFlow {
    override suspend fun confirm(code: String) = accountRepository.verifyEmailChangeCode(email, code)
    override suspend fun resend() = accountRepository.resendEmailChangeCode(email)
}

fun codeFlowFor(
    purpose: CodePurpose,
    email: String,
    authRepository: AuthRepository,
    accountRepository: AccountRepository,
): CodeFlow = when (purpose) {
    CodePurpose.SIGN_UP -> SignUpCodeFlow(authRepository, email)
    CodePurpose.EMAIL_CHANGE -> EmailChangeCodeFlow(accountRepository, email)
}
