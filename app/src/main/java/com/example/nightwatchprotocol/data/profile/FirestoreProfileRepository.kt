package com.example.nightwatchprotocol.data.profile

import com.example.nightwatchprotocol.data.firestore.firestoreCall
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class FirestoreProfileRepository(
    private val firestore: FirebaseFirestore,
) : ProfileRepository {

    // Every call is bounded and reports its failure kind (see firestoreCall); a write's await() returns null on success.
    override suspend fun getProfile(uid: String): RemoteProfile {
        val snapshot = firestoreCall { profileDocument(uid).get().await() }
        return RemoteProfile(
            recoveryEmail = snapshot.getString("recoveryEmail"),
            avatarBase64 = snapshot.getString("avatarBase64"),
            gender = snapshot.getString("gender"),
            birthDate = snapshot.getString("birthDate"),
        )
    }

    // A null value deletes the field, so "no definido" is really removed from the document.
    override suspend fun saveExtras(uid: String, gender: String?, birthDate: String?) =
        merge(
            uid,
            mapOf(
                "gender" to (gender ?: FieldValue.delete()),
                "birthDate" to (birthDate ?: FieldValue.delete()),
            ),
        )

    override suspend fun saveAvatar(uid: String, avatarBase64: String) =
        merge(uid, mapOf("avatarBase64" to avatarBase64))

    override suspend fun removeAvatar(uid: String) =
        merge(uid, mapOf("avatarBase64" to FieldValue.delete()))

    override suspend fun saveRecoveryEmail(uid: String, recoveryEmail: String) =
        merge(uid, mapOf("recoveryEmail" to recoveryEmail))

    private suspend fun merge(uid: String, fields: Map<String, Any>) {
        val data = fields + ("updatedAt" to FieldValue.serverTimestamp())
        firestoreCall { profileDocument(uid).set(data, SetOptions.merge()).await() }
    }

    private fun profileDocument(uid: String) =
        firestore.collection("users").document(uid).collection("profile").document("main")
}
