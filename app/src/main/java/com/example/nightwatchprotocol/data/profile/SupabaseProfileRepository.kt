package com.example.nightwatchprotocol.data.profile

import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.avatar.AVATAR_STORAGE_TIMEOUT_MILLIS
import com.example.nightwatchprotocol.data.avatar.AvatarFormat
import com.example.nightwatchprotocol.data.avatar.AvatarStorage
import com.example.nightwatchprotocol.data.avatar.avatarObjectPath
import com.example.nightwatchprotocol.data.avatar.canUploadAvatar
import com.example.nightwatchprotocol.data.avatar.detectAvatarFormat
import com.example.nightwatchprotocol.data.avatar.isOwnAvatarPath
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.supabase.RecoveryEmailCodeApi
import com.example.nightwatchprotocol.data.supabase.requireSessionUserId
import com.example.nightwatchprotocol.data.supabase.supabaseCall
import io.github.jan.supabase.postgrest.Postgrest
import kotlin.io.encoding.Base64
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
 * Partial writes. PostgREST's upsert only touches the columns present in the body, and a JSON null (JsonNull,
 * written explicitly because the serializer would drop a Kotlin null) clears the column.
 */

internal fun extrasPayload(userId: String, gender: String?, birthDate: String?): JsonObject = buildJsonObject {
    put("user_id", userId)
    put("gender", gender?.let(::JsonPrimitive) ?: JsonNull)
    put("birth_date", birthDate?.let(::JsonPrimitive) ?: JsonNull)
}

internal fun avatarPathPayload(userId: String, avatarPath: String?): JsonObject = buildJsonObject {
    put("user_id", userId)
    put("avatar_path", avatarPath?.let(::JsonPrimitive) ?: JsonNull)
}

class SupabaseProfileRepository(
    private val postgrest: Postgrest,
    private val avatarStorage: AvatarStorage,
    private val currentUserId: () -> String?,
    private val recoveryCodes: RecoveryEmailCodeApi,
) : ProfileRepository {

    // No row yet simply means an empty profile.
    override suspend fun getProfile(uid: String): RemoteProfile {
        val userId = requireSessionUserId(currentUserId)
        val rows = supabaseCall {
            postgrest.from(TABLE).select { filter { eq("user_id", userId) } }.decodeList<ProfileRow>()
        }
        return rows.firstOrNull()?.toRemoteProfile() ?: RemoteProfile()
    }

    override suspend fun saveExtras(uid: String, gender: String?, birthDate: String?) =
        upsertPartial(extrasPayload(requireSessionUserId(currentUserId), gender, birthDate))

    /*
     * Migration 0006 closed `profile.recovery_email` to the client: no upsert of this app sends it (it would fail with
     * "permission denied"). The column is written by the server when a code is verified.
     */
    override suspend fun requestRecoveryEmailCode(email: String) {
        if (currentUserId() == null) throw AuthError.Unknown
        recoveryCodes.sendCode(email)
    }

    override suspend fun verifyRecoveryEmailCode(code: String) {
        if (currentUserId() == null) throw AuthError.Unknown
        recoveryCodes.verifyCode(code)
    }

    /**
     * The photo is a file in the private 'avatars' bucket (`<user_id>/avatar.jpg`, upserted) and only its path is
     * stored in `profile.avatar_path`. The bytes are the ones the processor already made (256x256 JPEG), which
     * [AvatarRepository] hands over Base64-encoded as it does for every backend. The file goes first: if the second
     * step fails, the previous path (or none) still points at a valid state.
     */
    override suspend fun saveAvatar(uid: String, avatarBase64: String) {
        val userId = requireSessionUserId(currentUserId)
        val bytes = runCatching { Base64.Default.decode(avatarBase64) }.getOrNull()
        val format = bytes?.let(::detectAvatarFormat)
        if (bytes == null || format == null || !canUploadAvatar(bytes)) {
            throw DataAccessException(FailureKind.Unknown)
        }
        val path = avatarObjectPath(userId, format)
        supabaseCall(AVATAR_STORAGE_TIMEOUT_MILLIS) { avatarStorage.upload(path, bytes, format) }
        upsertPartial(avatarPathPayload(userId, path))
    }

    /** Deletes the file first and then clears the path; a retry after a half-done removal just finishes the job. */
    override suspend fun removeAvatar(uid: String) {
        val userId = requireSessionUserId(currentUserId)
        val path = avatarObjectPath(userId, AvatarFormat.Jpeg)
        supabaseCall(AVATAR_STORAGE_TIMEOUT_MILLIS) { avatarStorage.delete(path) }
        upsertPartial(avatarPathPayload(userId, null))
    }

    // Authenticated download (the bucket is private; no public URL exists). Only a path inside the user's own folder.
    override suspend fun downloadAvatar(uid: String, profile: RemoteProfile): ByteArray? {
        val userId = requireSessionUserId(currentUserId)
        val path = profile.avatarPath?.takeIf { isOwnAvatarPath(userId, it) } ?: return null
        return supabaseCall(AVATAR_STORAGE_TIMEOUT_MILLIS) { avatarStorage.download(path) }
    }

    private suspend fun upsertPartial(body: JsonObject) {
        supabaseCall { postgrest.from(TABLE).upsert(body = JsonArray(listOf(body))) }
    }

    private companion object {
        const val TABLE = "profile"
    }
}
