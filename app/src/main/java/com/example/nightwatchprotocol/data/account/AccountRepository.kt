package com.example.nightwatchprotocol.data.account

data class AccountProfile(
    val displayName: String?,
    val email: String?,
    val creationTimeMillis: Long?,
    val hasPassword: Boolean,
)

interface AccountRepository {
    /** The signed-in user's data from Firebase Auth, or null when nobody is signed in. */
    fun currentProfile(): AccountProfile?
}
