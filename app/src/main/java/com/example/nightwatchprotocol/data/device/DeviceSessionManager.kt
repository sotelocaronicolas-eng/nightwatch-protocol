package com.example.nightwatchprotocol.data.device

import com.example.nightwatchprotocol.data.auth.AuthRepository
import com.example.nightwatchprotocol.data.avatar.AvatarRepository
import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import kotlinx.coroutines.withTimeoutOrNull

private const val SIGN_OUT_CLEANUP_TIMEOUT_MILLIS = 2_000L

enum class SessionCheck {
    /** Nobody is signed in on this device. */
    NotSignedIn,

    /** This device is registered and was not revoked. */
    Active,

    /** The registry could not be read (offline, rules): the session is left alone rather than closed by mistake. */
    Unknown,

    /** Another device asked this one to sign out: the session was closed and local data cleared. */
    Revoked,
}

/**
 * Cooperative sessions. This device registers itself in the `devices` table and, each time the app is active, reads
 * its own mark: if another device set `revoked`, it signs itself out. The mark alone revokes no session; a modified
 * client could simply ignore it.
 */
class DeviceSessionManager(
    private val authRepository: AuthRepository,
    private val deviceRepository: DeviceRepository,
    private val deviceIdStore: DeviceIdStore,
    private val deviceInfo: DeviceInfoProvider,
    private val avatarRepository: AvatarRepository,
) {
    /** Registers or refreshes this device and honours a revocation. Call when the app opens or returns to the front. */
    suspend fun onAppActive(): SessionCheck {
        val uid = authRepository.currentUserUid ?: return SessionCheck.NotSignedIn
        val deviceId = deviceIdStore.getOrCreate()

        val existing = runCatchingKeepCancellation { deviceRepository.getDevice(uid, deviceId) }
            .getOrElse { return SessionCheck.Unknown }

        if (existing?.revoked == true) {
            // Removing the document lets this device register again, cleanly, the next time someone signs in on it.
            runCatchingKeepCancellation { deviceRepository.deleteDevice(uid, deviceId) }
            authRepository.signOut()
            avatarRepository.wipe(uid)
            return SessionCheck.Revoked
        }

        // A brand-new document starts as not revoked; an existing one keeps whatever mark it has.
        runCatchingKeepCancellation {
            deviceRepository.saveDevice(
                uid = uid,
                deviceId = deviceId,
                name = deviceInfo.deviceName(),
                revoked = if (existing == null) false else null,
            )
        }
        return SessionCheck.Active
    }

    /** The user's own "Cerrar sesión": removes this device from the list (best effort), then signs out. */
    suspend fun endSession() {
        val uid = authRepository.currentUserUid
        if (uid != null) {
            // The delete needs the session, so it goes first; a short timeout keeps sign-out from hanging offline.
            withTimeoutOrNull(SIGN_OUT_CLEANUP_TIMEOUT_MILLIS) {
                runCatchingKeepCancellation { deviceRepository.deleteDevice(uid, deviceIdStore.getOrCreate()) }
            }
        }
        authRepository.signOut()
        avatarRepository.clear()
    }

    /** This installation's id, to tell "this device" apart in the list. */
    suspend fun currentDeviceId(): String = deviceIdStore.getOrCreate()
}
