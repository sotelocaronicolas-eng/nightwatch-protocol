package com.example.nightwatchprotocol.data.auth

import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.supabase.supabaseCall
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What `public.account_status(email)` (migration 0005) says about an email.
 *
 * COMPROMISE ACCEPTED BY THE COURSE: this lets anyone ask whether an email has an account (account enumeration).
 * It exists only so login and sign-up can show individual errors ("not registered", "wrong password"), because Supabase answers
 * `invalid_credentials` for everything on purpose.
 */
enum class AccountStatus(val wire: String) {
    /** No user with that email. */
    NotFound("not_found"),

    /** The user signs in with a password (an `email` identity). */
    Password("password"),

    /** The user exists but has no password identity, e.g. only Google. */
    OtherProvider("other_provider"),
}

/** The function returns a JSON string (`"password"`); the quotes and spaces are tolerated. Null if unrecognised. */
fun parseAccountStatus(raw: String?): AccountStatus? {
    val value = raw?.trim()?.trim('"')?.trim()
    return AccountStatus.entries.firstOrNull { it.wire == value }
}

/** Login: the error to show once the server said `invalid_credentials`. A failed lookup keeps the generic error. */
fun loginErrorFor(status: AccountStatus?): AuthError = when (status) {
    AccountStatus.NotFound -> AuthError.UserNotFound
    AccountStatus.Password -> AuthError.WrongPassword
    AccountStatus.OtherProvider -> AuthError.GoogleAccountOnly
    null -> AuthError.InvalidCredentials
}

/** Sign-up: the error that stops the registration, or null to go on (not registered, or the lookup failed). */
fun registerErrorFor(status: AccountStatus?): AuthError? = when (status) {
    AccountStatus.Password -> AuthError.EmailAlreadyInUse
    AccountStatus.OtherProvider -> AuthError.GoogleAccountOnly
    AccountStatus.NotFound, null -> null
}

/** Asks the backend about an email. Throws on any failure; callers treat a failure as "unknown". */
fun interface AccountStatusLookup {
    suspend fun statusOf(email: String): AccountStatus
}

class SupabaseAccountStatusLookup(private val postgrest: Postgrest) : AccountStatusLookup {

    override suspend fun statusOf(email: String): AccountStatus {
        val body = buildJsonObject { put("p_email", email.trim()) }
        val raw = supabaseCall { postgrest.rpc("account_status", body).data }
        return parseAccountStatus(raw) ?: throw DataAccessException(FailureKind.Unknown)
    }
}
