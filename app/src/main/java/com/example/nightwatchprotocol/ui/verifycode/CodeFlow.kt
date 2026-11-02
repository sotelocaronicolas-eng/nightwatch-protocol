package com.example.nightwatchprotocol.ui.verifycode

import androidx.annotation.Keep
import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.account.AccountRepository
import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.data.profile.ProfileRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/** Which flow a code screen belongs to. It travels in the navigation route, so it is serializable. */
@Keep
@Serializable
enum class CodePurpose {
    /** Confirming the email of a new account (Supabase "Confirm email"). */
    SIGN_UP,

    /** Confirming the new address of a signed-in user (Supabase, code sent to the new email). */
    EMAIL_CHANGE,

    /** Confirming the alternate (recovery) email of a signed-in user; the code goes to that address. */
    RECOVERY_EMAIL,
}

/**
 * A non-blocking warning for the screen that follows a successful code. It travels in the navigation route as its [name]
 * (a key, not a text), and the confirmation screen turns it into text in the language of that moment.
 */
enum class CompletionNotice {
    /** The email changed, but the old Google account could not be unlinked. */
    GOOGLE_UNLINK_FAILED,
    ;

    fun toUiText(): UiText = when (this) {
        GOOGLE_UNLINK_FAILED -> MESSAGE_GOOGLE_UNLINK_FAILED
    }

    companion object {
        /** Null for a missing or unknown key, so a bad route never breaks the screen. */
        fun fromKey(key: String?): CompletionNotice? = entries.firstOrNull { it.name == key }
    }
}

/** What the code screen does: check the code, and send a new one. Both throw `AuthError` on failure. */
interface CodeFlow {
    suspend fun confirm(code: String)
    suspend fun resend()

    /** A non-blocking warning for the screen that follows a successful [confirm]; null when everything went well. */
    val completionNotice: CompletionNotice? get() = null

    /**
     * The code screen was closed while [confirm] was running (it has been cancelled and awaited). Undoes whatever a
     * confirmation that went through may have left behind. Nothing by default.
     */
    suspend fun abandon() {}
}

class SignUpCodeFlow(private val authRepository: AuthRepository, private val email: String) : CodeFlow {
    override suspend fun confirm(code: String) = authRepository.verifySignUpCode(email, code)
    override suspend fun resend() = authRepository.resendSignUpCode(email)
}

val MESSAGE_GOOGLE_UNLINK_FAILED: UiText = UiText.of(R.string.verify_code_google_unlink_failed)

/**
 * [email] is the new address, the one the code was sent to. Once it is confirmed, the Google accounts with another
 * email are unlinked; if that fails the change still stands and [completionNotice] says so.
 */
class EmailChangeCodeFlow(private val accountRepository: AccountRepository, private val email: String) : CodeFlow {
    override var completionNotice: CompletionNotice? = null
        private set

    override suspend fun confirm(code: String) {
        accountRepository.verifyEmailChangeCode(email, code)
        completionNotice =
            if (accountRepository.unlinkOutdatedGoogleAccounts(email)) null else CompletionNotice.GOOGLE_UNLINK_FAILED
    }

    override suspend fun resend() = accountRepository.resendEmailChangeCode(email)
}

/**
 * Password recovery. It has no navigation purpose because its code screen is the dialog over Login, not a route.
 * Supabase cannot "resend" a recovery code, so resending is asking for a new recovery email.
 */
class RecoveryCodeFlow(
    private val authRepository: AuthRepository,
    private val sessionPreferences: SessionPreferences,
    private val email: String,
) : CodeFlow {
    /**
     * Verifying opens a session without a typed password, so "recovery in progress" is stored BEFORE asking: if the app
     * dies right after the session opens, the next start still finds the mark and closes it. A refused code removes it.
     */
    override suspend fun confirm(code: String) {
        sessionPreferences.setRecoveryInProgress(true)
        try {
            authRepository.verifyRecoveryCode(email, code)
        } catch (e: Exception) {
            withContext(NonCancellable) { sessionPreferences.setRecoveryInProgress(false) }
            throw e
        }
    }

    override suspend fun resend() = authRepository.sendPasswordResetEmail(email)

    /**
     * The dialog was closed while the code was being checked: a recovery session may have opened with nobody to set the
     * new password, so it is closed and the "recovery in progress" mark removed (which is also what the next start does).
     */
    override suspend fun abandon() {
        authRepository.signOut()
        withContext(NonCancellable) { sessionPreferences.setRecoveryInProgress(false) }
    }
}

/** [email] is the recovery address the code was sent to; the server keeps the pending address, only the code is sent. */
class RecoveryEmailCodeFlow(private val profileRepository: ProfileRepository, private val email: String) : CodeFlow {
    override suspend fun confirm(code: String) = profileRepository.verifyRecoveryEmailCode(code)
    override suspend fun resend() = profileRepository.requestRecoveryEmailCode(email)
}

fun codeFlowFor(
    purpose: CodePurpose,
    email: String,
    authRepository: AuthRepository,
    accountRepository: AccountRepository,
    profileRepository: ProfileRepository,
): CodeFlow = when (purpose) {
    CodePurpose.SIGN_UP -> SignUpCodeFlow(authRepository, email)
    CodePurpose.EMAIL_CHANGE -> EmailChangeCodeFlow(accountRepository, email)
    CodePurpose.RECOVERY_EMAIL -> RecoveryEmailCodeFlow(profileRepository, email)
}
