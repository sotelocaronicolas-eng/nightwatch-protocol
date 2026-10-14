package com.example.nightwatchprotocol.data.device

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR

/** "Ahora", "Hace 5 min", "Hace 2 h", "Hace 3 d"; "—" when the device has no timestamp yet. */
fun relativeTimeLabel(lastSeenMillis: Long?, nowMillis: Long): String {
    if (lastSeenMillis == null) return "—"
    val elapsed = (nowMillis - lastSeenMillis).coerceAtLeast(0)
    return when {
        elapsed < MINUTE -> "Ahora"
        elapsed < HOUR -> "Hace ${elapsed / MINUTE} min"
        elapsed < DAY -> "Hace ${elapsed / HOUR} h"
        else -> "Hace ${elapsed / DAY} d"
    }
}
