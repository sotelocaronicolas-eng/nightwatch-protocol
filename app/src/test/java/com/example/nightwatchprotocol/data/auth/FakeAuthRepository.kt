package com.example.nightwatchprotocol.data.auth

import kotlinx.coroutines.CompletableDeferred

class FakeAuthRepository : AuthRepository {
    var signInResult: Result<Unit> = Result.success(Unit)
    var registerResult: Result<Unit> = Result.success(Unit)
    var sendPasswordResetEmailResult: Result<Unit> = Result.success(Unit)
    var signInWithGoogleResult: Result<Unit> = Result.success(Unit)

    var signInCallCount = 0
    var registerCallCount = 0
    var sendPasswordResetEmailCallCount = 0
    var signOutCallCount = 0

    override var currentUserEmail: String? = "test@example.com"

    override var currentUserUid: String? = "test-uid"

    /** When set, [awaitSessionReady] suspends until it is completed. */
    var sessionReadyGate: CompletableDeferred<Unit>? = null
    var awaitSessionReadyCallCount = 0

    /** When set, [awaitSessionReady] throws it (a stored session that cannot be read). */
    var awaitSessionReadyFailure: Throwable? = null

    override suspend fun awaitSessionReady() {
        awaitSessionReadyCallCount++
        sessionReadyGate?.await()
        awaitSessionReadyFailure?.let { throw it }
    }

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

    val signInWithGoogleNonces = mutableListOf<String>()

    override suspend fun signInWithGoogle(idToken: String, nonce: String) {
        signInWithGoogleNonces += nonce
        signInWithGoogleResult.getOrThrow()
    }

    override fun signOut() {
        signOutCallCount++
    }

    var verifyRecoveryCodeResult: Result<Unit> = Result.success(Unit)
    val verifyRecoveryCodeCalls = mutableListOf<Pair<String, String>>()

    override suspend fun verifyRecoveryCode(email: String, code: String) {
        verifyRecoveryCodeCalls += email to code
        verifyRecoveryCodeResult.getOrThrow()
    }

    var signOutOtherSessionsResult: Result<Unit> = Result.success(Unit)
    var signOutOtherSessionsCallCount = 0

    override suspend fun signOutOtherSessions() {
        signOutOtherSessionsCallCount++
        signOutOtherSessionsResult.getOrThrow()
    }

    var verifySignUpCodeResult: Result<Unit> = Result.success(Unit)
    var resendSignUpCodeResult: Result<Unit> = Result.success(Unit)
    val verifySignUpCodeCalls = mutableListOf<Pair<String, String>>()
    val resendSignUpCodeCalls = mutableListOf<String>()

    override suspend fun verifySignUpCode(email: String, code: String) {
        verifySignUpCodeCalls += email to code
        verifySignUpCodeResult.getOrThrow()
    }

    override suspend fun resendSignUpCode(email: String) {
        resendSignUpCodeCalls += email
        resendSignUpCodeResult.getOrThrow()
    }
}
