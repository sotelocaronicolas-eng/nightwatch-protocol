package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.common.DATA_TIMEOUT_MILLIS
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialRejection
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.data.social.socialErrorFromCode
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

/*
 * Failures of the Social calls. The central classification of data failures ([classifySupabaseFailure], the same one every other
 * repository uses) decides offline, permission denied and unknown; this file only adds what Social needs on top:
 *  - A SQL function that RAISES (`raise exception 'rate_limited'`) arrives as a PostgrestRestException with the SQLSTATE `P0001`
 *    and the code as its message: no_session, social_not_enabled, bad_action, bad_query, rate_limited (and, in the battle
 *    functions, battle_over, stale_turn...). [socialErrorFromCode] reads it.
 *  - The insert of `social_profile` is refused by Postgres itself: 23505 (unique username, without regard to case) and 23514
 *    (the length/characters check).
 * Business results are not failures of the call: the function answers `{"status":"error","code":"..."}` and the parser reads it.
 *
 * The exception is never kept: [SocialError] carries no cause, because a RestException's message includes the request headers
 * (token and apikey).
 */

/** SQLSTATE of `raise exception` in plpgsql. */
const val POSTGRES_RAISE_EXCEPTION = "P0001"

/** `unique_violation`: the username (or, with a probability close to zero, the friend code) is taken. */
const val POSTGRES_UNIQUE_VIOLATION = "23505"

/** `check_violation`: the username does not meet the check of the table. */
const val POSTGRES_CHECK_VIOLATION = "23514"

fun classifySocialFailure(error: Throwable): SocialError {
    if (error is PostgrestRestException) {
        when (error.code) {
            POSTGRES_RAISE_EXCEPTION -> return socialErrorFromCode(error.error)
            POSTGRES_UNIQUE_VIOLATION -> return SocialError.Rejected(SocialRejection.UsernameTaken)
            POSTGRES_CHECK_VIOLATION -> return SocialError.Rejected(SocialRejection.InvalidUsername)
        }
    }
    return when (classifySupabaseFailure(error)) {
        FailureKind.Offline -> SocialError.Offline
        FailureKind.PermissionDenied -> SocialError.PermissionDenied
        FailureKind.Unknown -> SocialError.Unknown
    }
}

/**
 * Runs a Social call with a time limit and returns its result or a classified failure; nothing is thrown, except the
 * cancellation of the caller (never swallowed). A call that takes longer than [timeoutMillis] is [SocialError.Offline].
 */
suspend fun <T> socialCall(timeoutMillis: Long = DATA_TIMEOUT_MILLIS, block: suspend () -> T): SocialResult<T> =
    try {
        SocialResult.Success(withTimeout(timeoutMillis) { block() })
    } catch (e: TimeoutCancellationException) {
        SocialResult.Failure(SocialError.Offline)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        SocialResult.Failure(classifySocialFailure(e))
    }
