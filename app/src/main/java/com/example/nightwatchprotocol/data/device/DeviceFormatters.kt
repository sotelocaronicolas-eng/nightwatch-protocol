package com.example.nightwatchprotocol.data.device

import com.example.nightwatchprotocol.R
import com.example.nightwatchprotocol.data.common.UiText

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** "Ahora", "Hace 5 min", "Hace 2 h", "Hace 3 d"; "—" when the device has no timestamp yet. */
fun relativeTimeLabel(lastSeenMillis: Long?, nowMillis: Long): UiText {
    if (lastSeenMillis == null) return UiText.dynamic("—")
    val elapsed = (nowMillis - lastSeenMillis).coerceAtLeast(0)
    return when {
        elapsed < MINUTE -> UiText.of(R.string.devices_time_now)
        elapsed < HOUR -> UiText.of(R.string.devices_time_minutes, elapsed / MINUTE)
        elapsed < DAY -> UiText.of(R.string.devices_time_hours, elapsed / HOUR)
        else -> UiText.of(R.string.devices_time_days, elapsed / DAY)
    }
}
