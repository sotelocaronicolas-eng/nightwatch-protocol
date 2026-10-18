package com.example.nightwatchprotocol.data.account

class FakeAccountRepository(
    var profile: AccountProfile? = AccountProfile(
        displayName = "Nicolás Sotelo",
        email = "nicolas@example.com",
        creationTimeMillis = 1_785_000_000_000L,
        hasPassword = true,
    ),
) : AccountRepository {
    var updateDisplayNameResult: Result<Unit> = Result.success(Unit)
    var reauthenticateResult: Result<Unit> = Result.success(Unit)

    /** Results returned by successive [updatePassword] calls; the last one repeats. */
    var updatePasswordResults: MutableList<Result<Unit>> = mutableListOf(Result.success(Unit))
    var requestEmailChangeResults: MutableList<Result<Unit>> = mutableListOf(Result.success(Unit))

    var updateDisplayNameCalls = mutableListOf<String>()
    var reauthenticateCalls = mutableListOf<String>()
    var updatePasswordCalls = mutableListOf<String>()
    var changePasswordCalls = mutableListOf<Pair<String, String>>()
    var requestEmailChangeCalls = mutableListOf<String>()

    override fun currentProfile(): AccountProfile? = profile

    override suspend fun updateDisplayName(name: String) {
        updateDisplayNameCalls += name
        updateDisplayNameResult.getOrThrow()
        profile = profile?.copy(displayName = name)
    }

    override suspend fun reauthenticate(currentPassword: String) {
        reauthenticateCalls += currentPassword
        reauthenticateResult.getOrThrow()
    }

    override suspend fun updatePassword(newPassword: String) {
        updatePasswordCalls += newPassword
        nextResult(updatePasswordResults).getOrThrow()
    }

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        changePasswordCalls += currentPassword to newPassword
        reauthenticateResult.getOrThrow()
        nextResult(updatePasswordResults).getOrThrow()
    }

    override suspend fun requestEmailChange(newEmail: String) {
        requestEmailChangeCalls += newEmail
        nextResult(requestEmailChangeResults).getOrThrow()
    }

    var verifyEmailChangeCodeResult: Result<Unit> = Result.success(Unit)
    var resendEmailChangeCodeResult: Result<Unit> = Result.success(Unit)
    val verifyEmailChangeCodeCalls = mutableListOf<Pair<String, String>>()
    val resendEmailChangeCodeCalls = mutableListOf<String>()

    override suspend fun verifyEmailChangeCode(newEmail: String, code: String) {
        verifyEmailChangeCodeCalls += newEmail to code
        verifyEmailChangeCodeResult.getOrThrow()
        profile = profile?.copy(email = newEmail)
    }

    override suspend fun resendEmailChangeCode(newEmail: String) {
        resendEmailChangeCodeCalls += newEmail
        resendEmailChangeCodeResult.getOrThrow()
    }

    var unlinkOutdatedGoogleAccountsResult = true
    val unlinkOutdatedGoogleAccountsCalls = mutableListOf<String>()

    override suspend fun unlinkOutdatedGoogleAccounts(newEmail: String): Boolean {
        unlinkOutdatedGoogleAccountsCalls += newEmail
        return unlinkOutdatedGoogleAccountsResult
    }

    private fun nextResult(results: MutableList<Result<Unit>>): Result<Unit> =
        if (results.size > 1) results.removeAt(0) else results.first()
}
