package com.example.nightwatchprotocol.data.auth

interface AuthRepository {
    val currentUserEmail: String?
    val currentUserUid: String?

    /**
     * Returns once [currentUserEmail] and [currentUserUid] can be trusted. Some backends load the stored session
     * asynchronously, so a read before this returns can say "nobody" for a user who is signed in. Call it once at
     * startup (and at the start of any background work that reads the session) before reading them.
     */
    suspend fun awaitSessionReady()

    suspend fun signIn(email: String, password: String)
    suspend fun register(name: String, email: String, password: String)
    suspend fun sendPasswordResetEmail(email: String)
    suspend fun signInWithGoogle(idToken: String)
    fun signOut()
}
