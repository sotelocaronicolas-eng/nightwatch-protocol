package com.example.nightwatchprotocol.data.account

import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.DISPLAY_NAME_KEY
import com.example.nightwatchprotocol.data.auth.SupabaseAuthRepository
import com.example.nightwatchprotocol.data.supabase.mapSupabaseAuthError
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime

/**
 * Account operations on Supabase Auth: display name, identity confirmation, password change and email change
 * (with a 6-digit code, see [requestEmailChange]).
 */
class SupabaseAccountRepository(
    private val auth: Auth,
    private val authRepository: SupabaseAuthRepository,
) : AccountRepository {

    override fun currentProfile(): AccountProfile? = authRepository.currentUser?.toAccountProfile()

    // The name is stored in the user metadata, the same place register() puts it.
    override suspend fun updateDisplayName(name: String) {
        requireUser()
        accountCall {
            auth.updateUser { data = buildJsonObject { put(DISPLAY_NAME_KEY, name) } }
        }
    }

    /**
     * Supabase has no "recent login" window, so identity is proven by signing in again with the current email and the
     * typed password. A wrong password fails as [AuthError.InvalidCredentials]. It replaces the session, so access
     * and refresh tokens are renewed (which also satisfies "Secure password change" when it is on).
     */
    override suspend fun reauthenticate(currentPassword: String) {
        val email = requireUser().email ?: throw AuthError.Unknown
        accountCall {
            auth.signInWith(Email, redirectUrl = null) {
                this.email = email
                this.password = currentPassword
            }
        }
    }

    /**
     * With "Secure password change" enabled in the project, the server only accepts the change for a session that
     * signed in recently, otherwise it answers `reauthentication_needed` ([AuthError.RecentLoginRequired]).
     * [changePassword] always re-signs in first, so it satisfies that.
     */
    override suspend fun updatePassword(newPassword: String) {
        requireUser()
        accountCall { auth.updateUser { password = newPassword } }
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        reauthenticate(currentPassword)
        updatePassword(newPassword)
    }

    override val changesEmailWithCode: Boolean get() = true

    /**
     * With "Secure email change" OFF in the panel, Supabase sends ONE code, to the new address (the "Change email
     * address" template prints `{{ .Token }}`). The account email stays the old one until the code is verified.
     */
    override suspend fun requestEmailChange(newEmail: String) {
        requireUser()
        accountCall { auth.updateUser(redirectUrl = null) { email = newEmail } }
    }

    /** Verifies with the email-change OTP type; the session that comes back already carries the new email. */
    override suspend fun verifyEmailChangeCode(newEmail: String, code: String) {
        requireUser()
        accountCall { auth.verifyEmailOtp(OtpType.Email.EMAIL_CHANGE, newEmail, code) }
    }

    override suspend fun resendEmailChangeCode(newEmail: String) {
        requireUser()
        accountCall { auth.resendEmail(OtpType.Email.EMAIL_CHANGE, newEmail) }
    }

    // The link flow is Firebase's. Here the change is confirmed by verifyEmailChangeCode, never by polling.
    override suspend fun isEmailChanged(newEmail: String): Boolean = false

    private fun requireUser(): UserInfo = authRepository.currentUser ?: throw AuthError.Unknown

    private inline fun <T> accountCall(block: () -> T): T {
        try {
            return block()
        } catch (e: AuthError) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw mapSupabaseAuthError(e)
        }
    }
}

// supabase-kt 3.2.4 types the dates as kotlin.time.Instant, still experimental in Kotlin 2.2.
@OptIn(ExperimentalTime::class)
internal fun UserInfo.toAccountProfile() = AccountProfile(
    displayName = (userMetadata?.get(DISPLAY_NAME_KEY) as? JsonPrimitive)?.contentOrNull,
    email = email,
    creationTimeMillis = createdAt?.toEpochMilliseconds(),
    hasPassword = identities.orEmpty().any { it.provider == EMAIL_PROVIDER },
)

private const val EMAIL_PROVIDER = "email"
