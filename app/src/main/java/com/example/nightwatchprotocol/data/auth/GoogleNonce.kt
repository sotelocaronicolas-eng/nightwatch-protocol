package com.example.nightwatchprotocol.data.auth

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The nonce of one Google sign-in on Supabase. Supabase's Google provider checks nonces ("Skip nonce checks" is off),
 * and the two halves travel differently:
 *  - [hashed] (SHA-256, lowercase hex) goes to Credential Manager, so Google writes it into the ID token;
 *  - [raw] goes, as it is, to Supabase, which hashes it again and compares it with the token's claim.
 * A nonce is used for one attempt only.
 */
class GoogleNonce(val raw: String) {
    val hashed: String = sha256Hex(raw)

    companion object {
        /** 32 random bytes, i.e. 256 bits, written as 64 hex characters. */
        const val RAW_BYTES = 32

        fun generate(random: SecureRandom = SecureRandom()): GoogleNonce {
            val bytes = ByteArray(RAW_BYTES).also(random::nextBytes)
            return GoogleNonce(bytes.toHex())
        }

        fun sha256Hex(input: String): String =
            MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).toHex()

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}

/**
 * Keeps the nonce of the Google sign-in in progress between "open Credential Manager" and "got the ID token".
 */
class GoogleSignInAttempt(
    private val newNonce: () -> GoogleNonce = { GoogleNonce.generate() },
) {
    private var pending: GoogleNonce? = null

    /** A new nonce for this attempt: its hash goes into Credential Manager's request. */
    fun begin(): String {
        val nonce = newNonce()
        pending = nonce
        return nonce.hashed
    }

    /**
     * The raw nonce that goes with the ID token of the attempt started by [begin]; consumed on read. Null if no attempt
     * is pending (the screen was recreated while the account chooser was open).
     */
    fun finish(): String? = pending?.raw.also { pending = null }
}
