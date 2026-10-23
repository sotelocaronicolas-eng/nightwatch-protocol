package com.example.nightwatchprotocol.ui.social

import java.time.Duration
import java.time.Instant

/**
 * Whole days between [finishedAt] and [now], for the "hace N días" of a history. It is only a label: the server decides every
 * deadline and every streak, so the phone's date here never changes a rule. Null when the server sent no date.
 */
internal fun daysAgo(finishedAt: Instant?, now: Instant): Int? {
    if (finishedAt == null) return null
    return Duration.between(finishedAt, now).toDays().coerceAtLeast(0).toInt()
}
