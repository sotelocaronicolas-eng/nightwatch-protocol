package com.example.nightwatchprotocol.data.device

import com.example.nightwatchprotocol.data.firestore.firestoreCall
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreDeviceRepository(
    private val firestore: FirebaseFirestore,
) : DeviceRepository {

    // Every call is bounded and reports its failure kind (see firestoreCall); a write's await() returns null on success.
    override suspend fun getDevice(uid: String, deviceId: String): DeviceRecord? {
        val snapshot = firestoreCall { document(uid, deviceId).get().await() }
        return if (snapshot.exists()) snapshot.toRecord() else null
    }

    override suspend fun listDevices(uid: String): List<DeviceRecord> {
        val snapshot = firestoreCall { collection(uid).get().await() }
        return snapshot.documents.map { it.toRecord() }
    }

    override suspend fun saveDevice(uid: String, deviceId: String, name: String, revoked: Boolean?) {
        val data = buildMap<String, Any> {
            put("name", name)
            put("lastSeen", FieldValue.serverTimestamp())
            if (revoked != null) put("revoked", revoked)
        }
        firestoreCall { document(uid, deviceId).set(data, SetOptions.merge()).await() }
    }

    override suspend fun setRevoked(uid: String, deviceId: String, revoked: Boolean) {
        firestoreCall { document(uid, deviceId).update("revoked", revoked).await() }
    }

    override suspend fun deleteDevice(uid: String, deviceId: String) {
        firestoreCall { document(uid, deviceId).delete().await() }
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
