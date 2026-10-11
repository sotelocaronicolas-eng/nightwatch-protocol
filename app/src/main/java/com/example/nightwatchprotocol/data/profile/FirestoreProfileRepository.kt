package com.example.nightwatchprotocol.data.profile

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

private const val READ_TIMEOUT_MILLIS = 5_000L

class FirestoreProfileRepository(
    private val firestore: FirebaseFirestore,
) : ProfileRepository {

    // A Firestore read with no usable connection can stay pending instead of failing, so it is bounded.
    override suspend fun getProfile(uid: String): RemoteProfile {
        val snapshot = withTimeoutOrNull(READ_TIMEOUT_MILLIS) { profileDocument(uid).get().await() }
            ?: throw ProfileUnavailableException()
        return RemoteProfile(
            recoveryEmail = snapshot.getString("recoveryEmail"),
            avatarBase64 = snapshot.getString("avatarBase64"),
        )
    }

    private fun profileDocument(uid: String) =
        firestore.collection("users").document(uid).collection("profile").document("main")
}
