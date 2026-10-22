package com.example.nightwatchprotocol.data.social

import kotlin.math.roundToLong

/** A clock that only moves forward and does not depend on the date or time of the phone (`System.nanoTime`). */
fun interface MonotonicClock {
    fun nanoTime(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nanoTime(): Long = System.nanoTime()
}

/**
 * What the screen shows as a countdown (a question's minute, the 2 minutes of an ulti, a challenge's 2 minutes). The SERVER decides
 * the time: it sends how many seconds were left ([secondsAtReceipt]) and the app only counts down from the moment it received
 * the answer ([receivedAtNanos], from a [MonotonicClock]). It never uses the phone's date or time, so changing the phone's
 * clock changes nothing, and it never decides that time ran out: the next call to the server says so.
 */
data class ServerCountdown(val secondsAtReceipt: Double, val receivedAtNanos: Long) {

    /** Milliseconds left at [nowNanos] (never negative). */
    fun remainingMillis(nowNanos: Long): Long {
        val elapsedMillis = (nowNanos - receivedAtNanos) / NANOS_PER_MILLI
        return (secondsAtReceipt * MILLIS_PER_SECOND).roundToLong().minus(elapsedMillis).coerceAtLeast(0L)
    }

    /** Whole seconds left, rounded up (the way a countdown is read: "1" until it is really over). */
    fun remainingSeconds(nowNanos: Long): Int = ((remainingMillis(nowNanos) + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
