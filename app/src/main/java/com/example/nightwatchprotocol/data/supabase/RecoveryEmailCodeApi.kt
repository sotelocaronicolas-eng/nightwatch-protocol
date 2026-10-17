package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.auth.AuthError
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** The Edge Function that emails the code to the recovery address (supabase/functions/send-recovery-email-code). */
const val SEND_RECOVERY_CODE_FUNCTION = "send-recovery-email-code"

/** The SQL function (migration 0006) that checks the code and, if it matches, saves the recovery email. */
const val VERIFY_RECOVERY_CODE_RPC = "verify_recovery_email_code"

private const val MAX_RETRY_AFTER_SECONDS = 3_600

/** The two server calls of the recovery-email verification. Both throw [AuthError] on failure. */
interface RecoveryEmailCodeApi {
    /** Asks the Edge Function to send a new 6-digit code to [email]. */
    suspend fun sendCode(email: String)

    /** Checks [code] against the pending one; on a match the server saves the recovery email. */
    suspend fun verifyCode(code: String)
}

class SupabaseRecoveryEmailCodeApi(
    private val functions: Functions,
    private val postgrest: Postgrest,
) : RecoveryEmailCodeApi {

    override suspend fun sendCode(email: String) {
        try {
            functions(SEND_RECOVERY_CODE_FUNCTION) {
                contentType(ContentType.Application.Json)
                setBody(buildJsonObject { put("email", email) }.toString())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw mapSendCodeFailure(e)
        }
    }

    override suspend fun verifyCode(code: String) {
        val answer = try {
            postgrest.rpc(VERIFY_RECOVERY_CODE_RPC, buildJsonObject { put("p_code", code) }).data
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw AuthError.NetworkError
        } catch (e: Exception) {
            throw AuthError.Unknown
        }
        checkVerifyAnswer(answer)
    }
}

private val json = Json { ignoreUnknownKeys = true }

/**
 * A failure of the Edge Function call. The function answers `{ "error": "<code>" }` (plus `retry_after_seconds` on 429).
 * The exception itself is never kept: its message carries the request headers (token and apikey).
 */
internal fun mapSendCodeFailure(error: Throwable): AuthError = when (error) {
    is RestException -> mapSendCodeResponse(error.statusCode, error.error)
    is IOException -> AuthError.NetworkError
    else -> AuthError.Unknown
}

internal fun mapSendCodeResponse(statusCode: Int, body: String): AuthError {
    val fields = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
    val code = (fields?.get("error") as? JsonPrimitive)?.contentOrNull
    return when {
        statusCode == 429 -> AuthError.RateLimited(
            (fields?.get("retry_after_seconds") as? JsonPrimitive)?.intOrNull
                ?.takeIf { it > 0 }?.coerceAtMost(MAX_RETRY_AFTER_SECONDS),
        )
        code == "same_as_primary" -> AuthError.SameAsPrimaryEmail
        code == "invalid_email" -> AuthError.InvalidEmail
        code == "email_failed" -> AuthError.EmailSendFailed
        else -> AuthError.Unknown
    }
}

/**
 * The word [VERIFY_RECOVERY_CODE_RPC] returns (a JSON string, so it may arrive in quotes). 'ok' is success; the rest
 * are the individualized code errors. No pending code at all means it was used up or replaced: same as expired.
 */
internal fun checkVerifyAnswer(raw: String) {
    when (raw.trim().trim('"')) {
        "ok" -> Unit
        "invalid" -> throw AuthError.InvalidCode
        "expired", "not_found" -> throw AuthError.CodeExpired
        "too_many_attempts" -> throw AuthError.TooManyAttempts
        else -> throw AuthError.Unknown
    }
}
