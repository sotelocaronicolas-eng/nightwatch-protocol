package com.example.nightwatchprotocol.data.auth

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth,
) : AuthRepository {

    override val currentUserEmail: String?
        get() = firebaseAuth.currentUser?.email

    override suspend fun signIn(email: String, password: String) {
        runCatchingFirebase {
            firebaseAuth.signInWithEmailAndPassword(email, password).await()
        }
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
        } catch (e: FirebaseNetworkException) {
            throw AuthError.NetworkError
        } catch (e: FirebaseAuthException) {
            throw mapAuthErrorCode(e.errorCode)
        }
    }
}
