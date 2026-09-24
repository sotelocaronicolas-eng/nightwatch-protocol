package com.example.nightwatchprotocol.data.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Constructing a real FirebaseException (even with just a message) reaches into
 * android.text.TextUtils via play-services-basement's Preconditions, which the
 * android.jar stub used by JVM unit tests doesn't implement. This needs a real
 * Android runtime, hence an instrumented test rather than testDebugUnitTest.
 */
@RunWith(AndroidJUnit4::class)
class AuthErrorMapperInstrumentedTest {
    @Test
    fun networkException_mapsToNetworkError() {
        val exception = FirebaseNetworkException("no connection")
        assertEquals(AuthError.NetworkError, mapFirebaseThrowable(exception))
    }

    @Test
    fun otherFirebaseException_mapsToUnknown() {
        val exception = FirebaseException("too many requests")
        assertEquals(AuthError.Unknown, mapFirebaseThrowable(exception))
    }
}
