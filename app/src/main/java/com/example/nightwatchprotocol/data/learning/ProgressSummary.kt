package com.example.nightwatchprotocol.data.learning

import com.example.nightwatchprotocol.data.profile.runCatchingKeepCancellation
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** What the streak shows: still reading, impossible to read (no data kept yet), or the current days. */
sealed interface StreakState {
    data object Loading : StreakState

    data object Unavailable : StreakState

    data class Days(val current: Int) : StreakState
}

/** The streak and the nine badges of one read of the progress. */
data class ProgressSummary(
    val streak: Streak,
    val badges: List<BadgeState>,
) {
    val unlockedCount: Int get() = badges.count { it.unlocked }
    val totalCount: Int get() = badges.size
}

/** Derives the streak and the badges from a snapshot. Pure: the clock and the zone come from the caller. */
fun summarizeProgress(snapshot: LearningSnapshot, now: Instant, zone: ZoneId): ProgressSummary {
    val streak = computeStreak(practiceMoments(snapshot), now, zone)
    val attempts = snapshot.examAttempts
    val facts = BadgeFacts(
        viewedLessons = snapshot.viewedLessons,
        completedLabs = snapshot.completedLabs,
        examPassed = snapshot.exam.hasPassed || attempts.any { it.passed },
        examPerfect = attempts.any { it.correctCount == EXAM_QUESTION_COUNT },
        bestStreak = streak.best,
    )
    return ProgressSummary(streak, evaluateBadges(facts))
}

/**
 * Reads the progress and summarizes it. The [clock] gives "now" and the zone in which the days are counted: Colombia by default
 * ([ColombiaZone], so the personal streak agrees with the friends' and the pairs'); a test passes a fixed one. Errors are the
 * ones of the repository (`DataAccessException`).
 */
class ProgressSummaryProvider(
    private val learningProgressRepository: LearningProgressRepository,
    private val clock: Clock = colombiaClock(),
) {
    suspend fun get(): ProgressSummary = summarizeProgress(learningProgressRepository.getSnapshot(), clock.instant(), clock.zone)
}

/**
 * The current streak for a screen that only shows that: the new value, or, when the read fails, the last known one
 * ([previous]) or "not available" if there was none yet.
 */
suspend fun ProgressSummaryProvider.readStreak(previous: StreakState): StreakState =
    runCatchingKeepCancellation { get() }.fold(
        onSuccess = { StreakState.Days(it.streak.current) },
        onFailure = { if (previous is StreakState.Days) previous else StreakState.Unavailable },
    )
