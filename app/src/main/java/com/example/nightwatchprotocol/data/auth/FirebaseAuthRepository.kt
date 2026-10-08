package com.example.nightwatchprotocol.data.auth

import com.google.firebase.FirebaseException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth,
) : AuthRepository {

    override val currentUserEmail: String?
        get() = firebaseAuth.currentUser?.email

    override val currentUserUid: String?
        get() = firebaseAuth.currentUser?.uid

    override suspend fun signIn(email: String, password: String) {
        try {
            runCatchingFirebase {
                firebaseAuth.signInWithEmailAndPassword(email, password).await()
            }
        } catch (error: AuthError.WrongPassword) {
            // The SDK reports ERROR_WRONG_PASSWORD both for a bad password and for an
            // account that has no password at all (created with Google), so ask which
            // providers the email uses before telling the user their password is wrong.
            throw if (isGoogleOnlyAccount(email)) AuthError.GoogleAccountOnly else error
        }
    }

    // Deprecated, but it is the only client API that lists an email's providers. It only
    // returns data while email-enumeration protection is off; any failure falls back to WrongPassword.
    @Suppress("DEPRECATION")
    private suspend fun isGoogleOnlyAccount(email: String): Boolean = try {
        val methods = firebaseAuth.fetchSignInMethodsForEmail(email).await().signInMethods.orEmpty()
        GoogleAuthProvider.PROVIDER_ID in methods && EmailAuthProvider.EMAIL_PASSWORD_SIGN_IN_METHOD !in methods
    } catch (e: FirebaseException) {
        false
    }

    override suspend fun register(name: String, email: String, password: String) {
        runCatchingFirebase {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val profileUpdate = UserProfileChangeRequest.Builder().setDisplayName(name).build()
            result.user?.updateProfile(profileUpdate)?.await()
        }
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        runCatchingFirebase {
            firebaseAuth.sendPasswordResetEmail(email).await()
        }
    }

    override suspend fun signInWithGoogle(idToken: String) {
        runCatchingFirebase {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            firebaseAuth.signInWithCredential(credential).await()
        }
    }

    override fun signOut() {
        firebaseAuth.signOut()
    }

    private inline fun <T> runCatchingFirebase(block: () -> T): T {
        try {
            return block()
        } catch (e: FirebaseException) {
            throw mapFirebaseThrowable(e)
        }
    }
}
