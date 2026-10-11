package com.example.nightwatchprotocol.data.profile

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val TIMEOUT_MILLIS = 5_000L

class FirestoreProfileRepository(
    private val firestore: FirebaseFirestore,
) : ProfileRepository {

    // A Firestore call with no usable connection can stay pending instead of failing, so each one is bounded.
    override suspend fun getProfile(uid: String): RemoteProfile {
        val snapshot = withTimeoutOrNull(TIMEOUT_MILLIS) { profileDocument(uid).get().await() }
            ?: throw ProfileUnavailableException()
        return RemoteProfile(
            recoveryEmail = snapshot.getString("recoveryEmail"),
            avatarBase64 = snapshot.getString("avatarBase64"),
        )
    }

    override suspend fun saveAvatar(uid: String, avatarBase64: String) =
        merge(uid, mapOf("avatarBase64" to avatarBase64))

    override suspend fun removeAvatar(uid: String) =
        merge(uid, mapOf("avatarBase64" to FieldValue.delete()))

    override suspend fun saveRecoveryEmail(uid: String, recoveryEmail: String) =
        merge(uid, mapOf("recoveryEmail" to recoveryEmail))

    private suspend fun merge(uid: String, fields: Map<String, Any>) {
        val data = fields + ("updatedAt" to FieldValue.serverTimestamp())
        withTimeoutOrNull(TIMEOUT_MILLIS) { profileDocument(uid).set(data, SetOptions.merge()).await() }
            ?: throw ProfileUnavailableException()
    }

    private fun profileDocument(uid: String) =
        firestore.collection("users").document(uid).collection("profile").document("main")
}
