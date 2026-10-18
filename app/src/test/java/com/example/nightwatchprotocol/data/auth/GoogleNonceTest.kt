package com.example.nightwatchprotocol.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleNonceTest {

    private val hex = Regex("^[0-9a-f]+$")

    @Test
    fun theRawNonce_has64LowercaseHexCharacters() {
        val nonce = GoogleNonce.generate()

        assertEquals(64, nonce.raw.length)
        assertTrue(hex.matches(nonce.raw))
    }

    @Test
    fun twoNonces_areNeverTheSame() {
        val random = SecureRandom()
        val nonces = (1..50).map { GoogleNonce.generate(random).raw }.toSet()

        assertEquals(50, nonces.size)
    }

    @Test
    fun theHashedNonce_isTheSha256HexOfTheRawOne() {
        val nonce = GoogleNonce.generate()
        val expected = MessageDigest.getInstance("SHA-256").digest(nonce.raw.toByteArray())
            .joinToString("") { "%02x".format(it) }

        assertEquals(expected, nonce.hashed)
        assertEquals(64, nonce.hashed.length)
        assertNotEquals(nonce.raw, nonce.hashed)
    }

    @Test
    fun sha256Hex_matchesTheKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            GoogleNonce.sha256Hex("abc"),
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            GoogleNonce.sha256Hex(""),
        )
    }

    @Test
    fun attempt_givesTheHashedNonceFirstAndTheRawOneOnce() {
        val attempt = GoogleSignInAttempt { GoogleNonce("crudo") }

        assertEquals(GoogleNonce.sha256Hex("crudo"), attempt.begin())
        assertEquals("crudo", attempt.finish())
        assertNull(attempt.finish())
    }

    @Test
    fun attempt_everyBeginMakesAFreshNonce() {
        val attempt = GoogleSignInAttempt()

        val first = attempt.begin()
        val second = attempt.begin()

        assertNotEquals(first, second)
    }

    @Test
    fun attempt_withoutBegin_hasNoRawNonce() {
        assertNull(GoogleSignInAttempt().finish())
    }
}
