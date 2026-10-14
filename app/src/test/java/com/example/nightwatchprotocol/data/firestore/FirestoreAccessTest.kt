package com.example.nightwatchprotocol.data.firestore

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreAccessTest {

    /**
     * The bug behind "No se pudo guardar el correo": a Firestore write is a Task<Void>, whose await() returns
     * null when it SUCCEEDS. The old code read null as "timed out" and always threw.
     */
    @Test
    fun aCallThatSucceedsWithANullResult_isNotATimeout() = runTest {
        val result: Void? = firestoreCall { null }

        assertEquals(null, result)
    }

    @Test
    fun aCallThatSucceedsWithUnit_returnsNormally() = runTest {
        var executed = false

        firestoreCall { executed = true }

        assertTrue(executed)
    }

    @Test
    fun aValueIsReturnedUnchanged() = runTest {
        assertEquals("ok", firestoreCall { "ok" })
    }

    @Test
    fun aCallThatNeverFinishes_isReportedAsOfflineAfterTheTimeout() = runTest {
        try {
            firestoreCall(timeoutMillis = 5_000) { delay(60_000) }
            fail("Expected FirestoreAccessException")
        } catch (e: FirestoreAccessException) {
            assertEquals(FailureKind.Offline, e.kind)
        }
    }

    @Test
    fun aCallThatFinishesInTime_isNotCutOff() = runTest {
        val result = firestoreCall(timeoutMillis = 5_000) {
            delay(4_000)
            "a tiempo"
        }

        assertEquals("a tiempo", result)
    }

    // FirebaseFirestoreException.Code cannot be initialised in a plain JVM test, so the status codes are
    // classified by name (the real exception passes `code.name` to the same function).
    @Test
    fun codesAreMappedToTheRightKind() {
        assertEquals(FailureKind.Offline, classifyCodeName("UNAVAILABLE"))
        assertEquals(FailureKind.Offline, classifyCodeName("DEADLINE_EXCEEDED"))
        assertEquals(FailureKind.PermissionDenied, classifyCodeName("PERMISSION_DENIED"))
        assertEquals(FailureKind.PermissionDenied, classifyCodeName("UNAUTHENTICATED"))
        assertEquals(FailureKind.Unknown, classifyCodeName("INVALID_ARGUMENT"))
        assertEquals(FailureKind.Unknown, classifyCodeName("NOT_FOUND"))
        assertEquals(FailureKind.Unknown, classifyCodeName(""))
    }

    @Test
    fun networkLevelIoErrors_areOffline_andAnythingElseIsUnknown() {
        assertEquals(FailureKind.Offline, classifyFailure(IOException("sin red")))
        assertEquals(FailureKind.Unknown, classifyFailure(IllegalStateException("raro")))
    }

    @Test
    fun anUnexpectedException_isWrappedAsUnknownKeepingTheCause() = runTest {
        try {
            firestoreCall { throw IllegalStateException("boom") }
            fail("Expected FirestoreAccessException")
        } catch (e: FirestoreAccessException) {
            assertEquals(FailureKind.Unknown, e.kind)
            // (coroutines may hand back a copy of the original exception, so compare type and message)
            assertTrue(e.cause is IllegalStateException)
            assertEquals("boom", e.cause?.message)
        }
    }

    @Test
    fun anAlreadyClassifiedFailure_isNotWrappedTwice() = runTest {
        val original = FirestoreAccessException(FailureKind.PermissionDenied)

        try {
            firestoreCall { throw original }
            fail("Expected FirestoreAccessException")
        } catch (e: FirestoreAccessException) {
            assertSame(original, e)
        }
    }

    @Test
    fun cancellingTheCaller_isNeverSwallowedAsAFailure() = runTest {
        var caught: Throwable? = null
        val job = launch {
            try {
                firestoreCall { delay(60_000) }
            } catch (e: Throwable) {
                caught = e
                throw e
            }
        }
        runCurrent() // let it start waiting, without moving the virtual clock past the timeout
        job.cancel()
        job.join()

        assertNotNull(caught)
        assertTrue(caught is CancellationException)
        assertTrue(caught !is FirestoreAccessException)
    }
}
