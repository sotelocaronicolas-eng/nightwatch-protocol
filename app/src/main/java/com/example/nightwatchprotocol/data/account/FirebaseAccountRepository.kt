package com.example.nightwatchprotocol.data.account

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class FirebaseAccountRepository(
    private val firebaseAuth: FirebaseAuth,
) : AccountRepository {

    override fun currentProfile(): AccountProfile? {
        val user = firebaseAuth.currentUser ?: return null
        return AccountProfile(
            displayName = user.displayName,
            email = user.email,
            creationTimeMillis = user.metadata?.creationTimestamp,
            hasPassword = user.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID },
        )
    }
}
