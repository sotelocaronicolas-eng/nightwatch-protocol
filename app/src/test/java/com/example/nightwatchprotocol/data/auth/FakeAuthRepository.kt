package com.example.nightwatchprotocol.data.auth

class FakeAuthRepository : AuthRepository {
    var signInResult: Result<Unit> = Result.success(Unit)
    var registerResult: Result<Unit> = Result.success(Unit)
    var sendPasswordResetEmailResult: Result<Unit> = Result.success(Unit)
    var signInWithGoogleResult: Result<Unit> = Result.success(Unit)

    var signInCallCount = 0
    var registerCallCount = 0
    var sendPasswordResetEmailCallCount = 0
    var signOutCallCount = 0

    override val currentUserEmail: String = "test@example.com"

    override var currentUserUid: String? = "test-uid"

    override suspend fun signIn(email: String, password: String) {
        signInCallCount++
        signInResult.getOrThrow()
    }

    override suspend fun register(name: String, email: String, password: String) {
        registerCallCount++
        registerResult.getOrThrow()
    }

    override suspend fun sendPasswordResetEmail(email: String) {
        sendPasswordResetEmailCallCount++
        sendPasswordResetEmailResult.getOrThrow()
    }

    override suspend fun signInWithGoogle(idToken: String) {
        signInWithGoogleResult.getOrThrow()
    }

    override fun signOut() {
        signOutCallCount++
    }
}
