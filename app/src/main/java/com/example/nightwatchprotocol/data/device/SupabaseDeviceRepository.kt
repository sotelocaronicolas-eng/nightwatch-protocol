package com.example.nightwatchprotocol.data.device

import com.example.nightwatchprotocol.data.supabase.requireSessionUserId
import com.example.nightwatchprotocol.data.supabase.supabaseCall
import io.github.jan.supabase.postgrest.Postgrest
import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A row of `public.devices`, as read. */
@Serializable
data class DeviceRow(
    @SerialName("user_id") val userId: String,
    @SerialName("device_id") val deviceId: String,
    val name: String,
    /** `timestamptz` as Postgres prints it, e.g. `2026-10-17T12:00:00.123456+00:00`. */
    @SerialName("last_seen") val lastSeen: String? = null,
    val revoked: Boolean = false,
)

fun DeviceRow.toRecord(): DeviceRecord = DeviceRecord(
    id = deviceId,
    name = name,
    lastSeenMillis = parseTimestampMillis(lastSeen),
    revoked = revoked,
)

/** Null for a missing or unreadable timestamp. */
internal fun parseTimestampMillis(text: String?): Long? =
    text?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

/** The `devices.name` column only accepts 1 to 80 characters (enforced by a CHECK constraint). */
internal const val MAX_DEVICE_NAME_LENGTH = 80

internal fun sanitizeDeviceName(name: String): String =
    name.trim().take(MAX_DEVICE_NAME_LENGTH).ifBlank { "Dispositivo" }

/**
 * Register or refresh this device. `revoked` is part of the body only when it has a value, and the upsert only
 * updates the columns it carries: a plain "seen again" (revoked == null) therefore never clears a revoked mark,
 * and a brand-new row gets the column default (false).
 */
internal fun saveDevicePayload(userId: String, deviceId: String, name: String, revoked: Boolean?, now: Instant): JsonObject =
    buildJsonObject {
        put("user_id", userId)
        put("device_id", deviceId)
        put("name", sanitizeDeviceName(name))
        put("last_seen", now.toString())
        if (revoked != null) put("revoked", revoked)
    }

/**
 * Cooperative device registry on `public.devices`: a revoked mark is only honoured by the other device the next time
 * its app is active.
 *
 * `last_seen` is the phone's clock (an upsert cannot ask the server for `now()`); a badly set clock only shows a
 * wrong "visto hace…" in the list.
 */
class SupabaseDeviceRepository(
    private val postgrest: Postgrest,
    private val currentUserId: () -> String?,
    private val clock: () -> Instant = Instant::now,
) : DeviceRepository {

    override suspend fun getDevice(uid: String, deviceId: String): DeviceRecord? {
        val userId = requireSessionUserId(currentUserId)
        val rows = supabaseCall {
            postgrest.from(TABLE).select {
                filter {
                    eq("user_id", userId)
                    eq("device_id", deviceId)
                }
            }.decodeList<DeviceRow>()
        }
        return rows.firstOrNull()?.toRecord()
    }

    override suspend fun listDevices(uid: String): List<DeviceRecord> {
        val userId = requireSessionUserId(currentUserId)
        val rows = supabaseCall {
            postgrest.from(TABLE).select { filter { eq("user_id", userId) } }.decodeList<DeviceRow>()
        }
        return rows.map { it.toRecord() }
    }

    override suspend fun saveDevice(uid: String, deviceId: String, name: String, revoked: Boolean?) {
        val body = saveDevicePayload(requireSessionUserId(currentUserId), deviceId, name, revoked, clock())
        supabaseCall {
            postgrest.from(TABLE).upsert(body = JsonArray(listOf(body))) { onConflict = "user_id,device_id" }
        }
    }

    // Postgres does not fail when the row does not exist: nothing is changed.
    override suspend fun setRevoked(uid: String, deviceId: String, revoked: Boolean) {
        val userId = requireSessionUserId(currentUserId)
        supabaseCall {
            postgrest.from(TABLE).update(buildJsonObject { put("revoked", revoked) }) {
                filter {
                    eq("user_id", userId)
                    eq("device_id", deviceId)
                }
            }
        }
    }

    override suspend fun deleteDevice(uid: String, deviceId: String) {
        val userId = requireSessionUserId(currentUserId)
        supabaseCall {
            postgrest.from(TABLE).delete {
                filter {
                    eq("user_id", userId)
                    eq("device_id", deviceId)
                }
            }
        }
    }

    private companion object {
        const val TABLE = "devices"
    }
}
