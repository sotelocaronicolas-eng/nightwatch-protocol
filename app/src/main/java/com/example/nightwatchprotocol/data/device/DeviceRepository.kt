package com.example.nightwatchprotocol.data.device

/** One document of `users/{uid}/devices/{deviceId}`. */
data class DeviceRecord(
    val id: String,
    val name: String,
    val lastSeenMillis: Long?,
    val revoked: Boolean,
)

/**
 * Our own device registry (the `devices` table). Every call throws when it fails or takes longer than the repository's
 * timeout. A revoked mark is only a request that the other device honours the next time its app is active:
 * it does not revoke any session (that is `AuthRepository.signOutOtherSessions`).
 */
interface DeviceRepository {
    suspend fun getDevice(uid: String, deviceId: String): DeviceRecord?

    suspend fun listDevices(uid: String): List<DeviceRecord>

    /** Merges name and lastSeen; [revoked] is written only when not null (a plain "seen again" never clears a mark). */
    suspend fun saveDevice(uid: String, deviceId: String, name: String, revoked: Boolean?)

    /** Updates only the revoked field of an existing document. */
    suspend fun setRevoked(uid: String, deviceId: String, revoked: Boolean)

    suspend fun deleteDevice(uid: String, deviceId: String)
}
