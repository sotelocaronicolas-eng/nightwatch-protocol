package com.example.nightwatchprotocol.data.device

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val TIMEOUT_MILLIS = 5_000L

class FirestoreDeviceRepository(
    private val firestore: FirebaseFirestore,
) : DeviceRepository {

    // Firestore calls with no usable connection can stay pending instead of failing, so each one is bounded.
    override suspend fun getDevice(uid: String, deviceId: String): DeviceRecord? {
        val snapshot = withTimeoutOrNull(TIMEOUT_MILLIS) { document(uid, deviceId).get().await() }
            ?: throw DeviceUnavailableException()
        return if (snapshot.exists()) snapshot.toRecord() else null
    }

    override suspend fun listDevices(uid: String): List<DeviceRecord> {
        val snapshot = withTimeoutOrNull(TIMEOUT_MILLIS) { collection(uid).get().await() }
            ?: throw DeviceUnavailableException()
        return snapshot.documents.map { it.toRecord() }
    }

    override suspend fun saveDevice(uid: String, deviceId: String, name: String, revoked: Boolean?) {
        val data = buildMap<String, Any> {
            put("name", name)
            put("lastSeen", FieldValue.serverTimestamp())
            if (revoked != null) put("revoked", revoked)
        }
        withTimeoutOrNull(TIMEOUT_MILLIS) { document(uid, deviceId).set(data, SetOptions.merge()).await() }
            ?: throw DeviceUnavailableException()
    }

    override suspend fun setRevoked(uid: String, deviceId: String, revoked: Boolean) {
        withTimeoutOrNull(TIMEOUT_MILLIS) { document(uid, deviceId).update("revoked", revoked).await() }
            ?: throw DeviceUnavailableException()
    }

    override suspend fun deleteDevice(uid: String, deviceId: String) {
        withTimeoutOrNull(TIMEOUT_MILLIS) { document(uid, deviceId).delete().await() }
            ?: throw DeviceUnavailableException()
    }

    private fun DocumentSnapshot.toRecord() = DeviceRecord(
        id = id,
        name = getString("name").orEmpty(),
        lastSeenMillis = getTimestamp("lastSeen")?.toDate()?.time,
        revoked = getBoolean("revoked") ?: false,
    )

    private fun collection(uid: String) = firestore.collection("users").document(uid).collection("devices")

    private fun document(uid: String, deviceId: String) = collection(uid).document(deviceId)
}
