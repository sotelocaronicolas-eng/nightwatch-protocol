package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.common.DATA_TIMEOUT_MILLIS
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.data.social.SocialRpcClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Calls the SQL functions of `0008_social.sql` through PostgREST (`rpc`). The caller is identified by the session's token, which
 * the Supabase client attaches to every request: no id travels in the parameters. A call without a session is
 * [SocialError.NoSession] before any request.
 *
 * Not tested against a real server from here (CLAUDE.md, F-03): the classification and the answers are tested without a network,
 * and the real calls are checked by hand with the scenarios script and in the phone.
 */
class SupabaseSocialRpcClient(
    private val postgrest: Postgrest,
    private val currentUserId: () -> String?,
    private val timeoutMillis: Long = DATA_TIMEOUT_MILLIS,
) : SocialRpcClient {

    override suspend fun call(function: String, params: JsonObject): SocialResult<JsonElement> = invoke(function, params, ::classifySocialFailure)

    override suspend fun callNewAction(function: String, params: JsonObject): SocialResult<JsonElement> =
        invoke(function, params, ::classifyNewActionFailure)

    private suspend fun invoke(function: String, params: JsonObject, classify: (Throwable) -> SocialError): SocialResult<JsonElement> {
        if (currentUserId() == null) return SocialResult.Failure(SocialError.NoSession)
        return socialCall(timeoutMillis, classify) { Json.parseToJsonElement(postgrest.rpc(function, params).data) }
    }
}
