package com.example.nightwatchprotocol.data.biometric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {
    @Test
    fun reLockDelayIsThirtySeconds() {
        assertEquals(30_000L, RELOCK_AFTER_BACKGROUND_MILLIS)
    }

    @Test
    fun coldStart_locksOnlyWithASessionAndTheLockOn() {
        assertTrue(shouldLockOnColdStart(signedIn = true, lockEnabled = true))
        assertFalse(shouldLockOnColdStart(signedIn = true, lockEnabled = false))
        assertFalse(shouldLockOnColdStart(signedIn = false, lockEnabled = true))
        assertFalse(shouldLockOnColdStart(signedIn = false, lockEnabled = false))
    }

    @Test
    fun returnAfterLessThanThirtySeconds_doesNotLock() {
        assertFalse(shouldLockOnReturn(true, true, backgroundedAtMillis = 1_000, nowMillis = 30_999))
    }

    @Test
    fun returnAfterExactlyThirtySeconds_locks() {
        assertTrue(shouldLockOnReturn(true, true, backgroundedAtMillis = 1_000, nowMillis = 31_000))
    }

    @Test
    fun returnAfterMoreThanThirtySeconds_locks() {
        assertTrue(shouldLockOnReturn(true, true, backgroundedAtMillis = 0, nowMillis = 600_000))
    }

    @Test
    fun returnWithTheLockOff_doesNotLock() {
        assertFalse(shouldLockOnReturn(true, false, backgroundedAtMillis = 0, nowMillis = 600_000))
    }

    @Test
    fun returnWithoutASession_doesNotLock() {
        assertFalse(shouldLockOnReturn(false, true, backgroundedAtMillis = 0, nowMillis = 600_000))
    }

    @Test
    fun returnWithoutHavingBeenInTheBackground_doesNotLock() {
        assertFalse(shouldLockOnReturn(true, true, backgroundedAtMillis = null, nowMillis = 600_000))
    }
}
