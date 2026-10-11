package com.example.nightwatchprotocol.data.account

import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.mapFirebaseThrowable
import com.google.firebase.FirebaseException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

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

    override suspend fun updateDisplayName(name: String) {
        val user = requireUser()
        runCatchingFirebase {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
        }
    }

    override suspend fun reauthenticate(currentPassword: String) {
        val user = requireUser()
        val email = user.email ?: throw AuthError.Unknown
        runCatchingFirebase {
            user.reauthenticate(EmailAuthProvider.getCredential(email, currentPassword)).await()
        }
    }

    override suspend fun updatePassword(newPassword: String) {
        val user = requireUser()
        runCatchingFirebase { user.updatePassword(newPassword).await() }
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        reauthenticate(currentPassword)
        updatePassword(newPassword)
    }

    override suspend fun requestEmailChange(newEmail: String) {
        val user = requireUser()
        runCatchingFirebase { user.verifyBeforeUpdateEmail(newEmail).await() }
    }

    override suspend fun isEmailChanged(newEmail: String): Boolean {
        val user = requireUser()
        runCatchingFirebase { user.reload().await() }
        return firebaseAuth.currentUser?.email.equals(newEmail, ignoreCase = true)
    }

    private fun requireUser(): FirebaseUser = firebaseAuth.currentUser ?: throw AuthError.Unknown

    private inline fun <T> runCatchingFirebase(block: () -> T): T {
        try {
            return block()
        } catch (e: FirebaseException) {
            throw mapFirebaseThrowable(e)
        }
    }
}
