package com.example.nightwatchprotocol.data.learning

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The streak of practice days. [current] counts the consecutive days that end today (or yesterday, see [computeStreak]);
 * [best] is the longest run of consecutive days in the whole history.
 */
data class Streak(val current: Int, val best: Int) {
    companion object {
        val None = Streak(current = 0, best = 0)
    }
}

/**
 * The moments that make a "practice day": the `completed_at` of every saved lesson and lab and the `taken_at` of every exam
 * attempt. LIMIT (known, documented in the spec): repeating a lesson or a lab that is already saved writes no new row (the
 * registration ignores duplicates), so it adds no day; fixing that would need an activity table (a draft migration 0008).
 */
fun practiceMoments(snapshot: LearningSnapshot): List<Instant> =
    snapshot.lessonDates.values + snapshot.labDates.values + snapshot.examAttempts.mapNotNull { it.takenAt }

/**
 * Pure streak calculation with an injectable clock and zone: every instant (UTC) is first converted to the local date of
 * [zone], so a lab done at 23:30 local time that is already tomorrow in UTC counts for the local day.
 *
 *  - Several moments on the same day are one day.
 *  - [Streak.current]: the consecutive days ending at the last day with activity, as long as that day is today or yesterday
 *    (if today has no activity yet but yesterday does, the streak is still alive and counts up to yesterday). If the last day
 *    with activity is older than yesterday, the current streak is 0.
 *  - [Streak.best]: the longest run of consecutive days ever.
 *
 * A moment dated after today (a wrong device clock) is treated as the last day, so it never makes the current streak negative.
 */
fun computeStreak(moments: Collection<Instant>, now: Instant, zone: ZoneId): Streak {
    val days = moments.map { it.atZone(zone).toLocalDate() }.toSortedSet()
    if (days.isEmpty()) return Streak.None

    var best = 0
    var run = 0
    var previous: LocalDate? = null
    for (day in days) {
        run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
        if (run > best) best = run
        previous = day
    }

    val today = now.atZone(zone).toLocalDate()
    val last = days.last()
    val current = if (last.isBefore(today.minusDays(1))) {
        0
    } else {
        var count = 1
        var day = last
        while (days.contains(day.minusDays(1))) {
            count++
            day = day.minusDays(1)
        }
        count
    }
    return Streak(current = current, best = best)
}
