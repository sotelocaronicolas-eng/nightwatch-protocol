package com.example.nightwatchprotocol.data.profile

import com.example.nightwatchprotocol.data.firestore.FailureKind
import com.example.nightwatchprotocol.data.firestore.FirestoreAccessException
import com.example.nightwatchprotocol.data.supabase.requireSessionUserId
import com.example.nightwatchprotocol.data.supabase.supabaseCall
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A row of `public.profile`, as read. Every column but the key can be null. `updated_at` is only ever read: the
 * trigger sets it, so no write below sends it.
 */
@Serializable
data class ProfileRow(
    @SerialName("user_id") val userId: String? = null,
    @SerialName("recovery_email") val recoveryEmail: String? = null,
    @SerialName("avatar_path") val avatarPath: String? = null,
    val gender: String? = null,
    /** `date` column: Postgres returns `yyyy-MM-dd`, the same text [RemoteProfile.birthDate] holds. */
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

fun ProfileRow.toRemoteProfile(): RemoteProfile = RemoteProfile(
    recoveryEmail = recoveryEmail,
    avatarBase64 = null,
    gender = gender,
    birthDate = birthDate,
    avatarPath = avatarPath,
)

/*
 * Partial writes. PostgREST's upsert only touches the columns present in the body, which is what
 * `set(..., merge())` did in Firestore, and a JSON null (JsonNull, written explicitly because the serializer would
 * drop a Kotlin null) clears the column, like `FieldValue.delete()`.
 */

internal fun extrasPayload(userId: String, gender: String?, birthDate: String?): JsonObject = buildJsonObject {
    put("user_id", userId)
    put("gender", gender?.let(::JsonPrimitive) ?: JsonNull)
    put("birth_date", birthDate?.let(::JsonPrimitive) ?: JsonNull)
}

internal fun recoveryEmailPayload(userId: String, recoveryEmail: String): JsonObject = buildJsonObject {
    put("user_id", userId)
    put("recovery_email", recoveryEmail)
}

internal fun avatarPathPayload(userId: String, avatarPath: String?): JsonObject = buildJsonObject {
    put("user_id", userId)
    put("avatar_path", avatarPath?.let(::JsonPrimitive) ?: JsonNull)
}

class SupabaseProfileRepository(
    private val postgrest: Postgrest,
    private val currentUserId: () -> String?,
) : ProfileRepository {

    // No row yet simply means an empty profile, like a missing Firestore document.
    override suspend fun getProfile(uid: String): RemoteProfile {
        val userId = requireSessionUserId(currentUserId)
        val rows = supabaseCall {
            postgrest.from(TABLE).select { filter { eq("user_id", userId) } }.decodeList<ProfileRow>()
        }
        return rows.firstOrNull()?.toRemoteProfile() ?: RemoteProfile()
    }

    override suspend fun saveExtras(uid: String, gender: String?, birthDate: String?) =
        upsertPartial(extrasPayload(requireSessionUserId(currentUserId), gender, birthDate))

    override suspend fun saveRecoveryEmail(uid: String, recoveryEmail: String) =
        upsertPartial(recoveryEmailPayload(requireSessionUserId(currentUserId), recoveryEmail))

    // The photo goes to Storage, not to this table: it is wired in the avatar stage.
    override suspend fun saveAvatar(uid: String, avatarBase64: String) {
        throw FirestoreAccessException(FailureKind.Unknown)
    }

    override suspend fun removeAvatar(uid: String) {
        throw FirestoreAccessException(FailureKind.Unknown)
    }

    private suspend fun upsertPartial(body: JsonObject) {
        supabaseCall { postgrest.from(TABLE).upsert(body = JsonArray(listOf(body))) }
    }

    private companion object {
        const val TABLE = "profile"
    }
}
