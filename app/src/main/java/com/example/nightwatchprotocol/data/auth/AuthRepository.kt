package com.example.nightwatchprotocol.data.auth

interface AuthRepository {
    val currentUserEmail: String?
    suspend fun signIn(email: String, password: String)
    suspend fun register(name: String, email: String, password: String)
    suspend fun sendPasswordResetEmail(email: String)
    suspend fun signInWithGoogle(idToken: String)
    fun signOut()
}
