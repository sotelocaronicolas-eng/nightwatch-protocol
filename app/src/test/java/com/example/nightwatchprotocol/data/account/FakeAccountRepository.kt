package com.example.nightwatchprotocol.data.account

class FakeAccountRepository(
    var profile: AccountProfile? = AccountProfile(
        displayName = "Nicolás Sotelo",
        email = "nicolas@example.com",
        creationTimeMillis = 1_785_000_000_000L,
        hasPassword = true,
    ),
) : AccountRepository {
    override fun currentProfile(): AccountProfile? = profile
}
