package com.example.nightwatchprotocol.data.notifications

import java.util.Calendar
import java.util.TimeZone

/**
 * Milliseconds from [nowMillis] until the next [hour]:[minute] on the wall clock of [timeZone].
 * At or after today's time it is tomorrow's; the result is always greater than zero.
 */
fun millisUntilNext(
    nowMillis: Long,
    hour: Int,
    minute: Int,
    timeZone: TimeZone = TimeZone.getDefault(),
): Long {
    val target = Calendar.getInstance(timeZone).apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_YEAR, 1)
    }
    return target.timeInMillis - nowMillis
}
