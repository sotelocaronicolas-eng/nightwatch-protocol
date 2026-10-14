package com.example.nightwatchprotocol.data.biometric

/** How long the app may stay in the background before the biometric lock asks again (decision P13). */
const val RELOCK_AFTER_BACKGROUND_MILLIS = 30_000L

/** A fresh process with a signed-in user and the lock turned on starts locked. */
fun shouldLockOnColdStart(signedIn: Boolean, lockEnabled: Boolean): Boolean = signedIn && lockEnabled

/**
 * Coming back from the background locks only when someone is signed in, the lock is on and the app
 * was away for at least [RELOCK_AFTER_BACKGROUND_MILLIS]. Times come from a monotonic clock.
 */
fun shouldLockOnReturn(
    signedIn: Boolean,
    lockEnabled: Boolean,
    backgroundedAtMillis: Long?,
    nowMillis: Long,
): Boolean = signedIn &&
    lockEnabled &&
    backgroundedAtMillis != null &&
    nowMillis - backgroundedAtMillis >= RELOCK_AFTER_BACKGROUND_MILLIS
