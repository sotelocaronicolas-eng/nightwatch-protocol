package com.example.nightwatchprotocol.data.learning

import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BadgesTest {
    private val bogota = ZoneId.of("America/Bogota")
    private val today = LocalDate.of(2026, 10, 18)
    private val now = at(today, "15:00")

    private fun at(date: LocalDate, time: String = "12:00"): Instant = LocalDateTime.parse("${date}T$time").atZone(bogota).toInstant()

    private fun facts(
        lessons: Set<LearningTopic> = emptySet(),
        labs: Set<LearningTopic> = emptySet(),
        examPassed: Boolean = false,
        examPerfect: Boolean = false,
        bestStreak: Int = 0,
    ) = BadgeFacts(lessons, labs, examPassed, examPerfect, bestStreak)

    private fun BadgeCondition.met(facts: BadgeFacts) = isMet(facts)

    private fun unlocked(state: List<BadgeState>, name: String) = state.first { it.definition.name == name }.unlocked

    private fun topics(count: Int): Set<LearningTopic> = LearningTopic.entries.take(count).toSet()

    // ---- the catalog ----

    @Test
    fun theCatalog_hasExactlyTheNineBadgesOfFigma_inOrder() {
        assertEquals(9, BadgeCatalog.size)
        assertEquals(
            listOf(
                "Primeros pasos",
                "Racha de 7 días",
                "Maestro de Contraseñas",
                "Cazador de Phishing",
                "Guardián de Redes",
                "Leyenda NightWatch",
                "Detective de Ingeniería Social",
                "Racha de 30 días",
                "Graduado NightWatch",
            ),
            BadgeCatalog.map { it.name },
        )
    }

    @Test
    fun everyBadge_hasAnEmoji_aSource_andANote() {
        BadgeCatalog.forEach { badge ->
            assertTrue(badge.name, badge.emoji.isNotBlank())
            assertTrue(badge.name, badge.source.startsWith("Figma"))
            assertNotNull(badge.name, badge.note)
            assertTrue(badge.name, badge.note!!.isNotBlank())
        }
        assertEquals(BadgeCatalog.size, BadgeCatalog.map { it.name }.toSet().size)
    }

    @Test
    fun aBadgeBlockedOnPurpose_saysSo_inItsNote() {
        BadgeCatalog.filter { it.condition == BadgeCondition.Unverifiable || it.condition == BadgeCondition.AvailableFromModule4 }
            .forEach { assertTrue(it.name, it.note!!.contains("SUPUESTO") || it.note!!.contains("módulo 4")) }
    }

    @Test
    fun theStreakBadges_takeTheirNumberFromTheirName() {
        assertEquals(BadgeCondition.BestStreakAtLeast(7), BadgeCatalog.first { it.name == "Racha de 7 días" }.condition)
        assertEquals(BadgeCondition.BestStreakAtLeast(30), BadgeCatalog.first { it.name == "Racha de 30 días" }.condition)
    }

    // ---- each kind of condition, with and without, and on its edge ----

    @Test
    fun lessonsViewed_zeroNo_oneYes() {
        val condition = BadgeCondition.LessonsViewedAtLeast(1)
        assertFalse(condition.met(facts(lessons = topics(0))))
        assertTrue(condition.met(facts(lessons = topics(1))))
        assertTrue(condition.met(facts(lessons = topics(6))))
    }

    @Test
    fun labsCompleted_fiveNo_sixYes() {
        val condition = BadgeCondition.LabsCompletedAtLeast(6)
        assertFalse(condition.met(facts(labs = topics(0))))
        assertFalse(condition.met(facts(labs = topics(5))))
        assertTrue(condition.met(facts(labs = topics(6))))
    }

    @Test
    fun allLessons_fiveNo_sixYes() {
        val condition = BadgeCondition.LessonsViewedAtLeast(6)
        assertFalse(condition.met(facts(lessons = topics(5))))
        assertTrue(condition.met(facts(lessons = topics(6))))
    }

    @Test
    fun islandCompleted_needsTheLessonAndTheLab() {
        val condition = BadgeCondition.IslandCompleted(LearningTopic.PHISHING)
        val phishing = setOf(LearningTopic.PHISHING)
        assertFalse(condition.met(facts()))
        assertFalse(condition.met(facts(lessons = phishing)))
        assertFalse(condition.met(facts(labs = phishing)))
        assertFalse(condition.met(facts(lessons = setOf(LearningTopic.MALWARE), labs = setOf(LearningTopic.MALWARE))))
        assertTrue(condition.met(facts(lessons = phishing, labs = phishing)))
    }

    @Test
    fun bestStreak_oneBelowNo_exactlyYes() {
        assertFalse(BadgeCondition.BestStreakAtLeast(7).met(facts(bestStreak = 6)))
        assertTrue(BadgeCondition.BestStreakAtLeast(7).met(facts(bestStreak = 7)))
        assertFalse(BadgeCondition.BestStreakAtLeast(30).met(facts(bestStreak = 29)))
        assertTrue(BadgeCondition.BestStreakAtLeast(30).met(facts(bestStreak = 30)))
    }

    @Test
    fun examPassed_andExamPerfect_followTheirFacts() {
        assertFalse(BadgeCondition.ExamPassed.met(facts()))
        assertTrue(BadgeCondition.ExamPassed.met(facts(examPassed = true)))
        assertFalse(BadgeCondition.ExamPerfect.met(facts(examPassed = true)))
        assertTrue(BadgeCondition.ExamPerfect.met(facts(examPassed = true, examPerfect = true)))
    }

    @Test
    fun theExamBadges_atTheEdgeOfFiveAndSixRightAnswers() {
        fun snapshotWith(vararg correct: Int) = LearningSnapshot(
            viewedLessons = emptySet(),
            completedLabs = emptySet(),
            exam = ExamStatus(hasPassed = correct.any { it >= 5 }, lastAttempt = null),
            examAttempts = correct.map { ExamAttempt(it, it >= 5, takenAt = at(today)) },
        )

        val four = summarizeProgress(snapshotWith(4), now, bogota)
        val five = summarizeProgress(snapshotWith(5), now, bogota)
        val six = summarizeProgress(snapshotWith(4, 6), now, bogota)
        assertFalse(unlocked(four.badges, "Graduado NightWatch"))
        assertTrue(unlocked(five.badges, "Graduado NightWatch"))
        assertTrue(unlocked(six.badges, "Graduado NightWatch"))
    }

    // ---- the blocked ones ----

    @Test
    fun aBadgeForModule4_andAnUnverifiableOne_neverUnlock_whateverTheFacts() {
        val everything = facts(lessons = topics(6), labs = topics(6), examPassed = true, examPerfect = true, bestStreak = 999)
        assertFalse(BadgeCondition.AvailableFromModule4.met(everything))
        assertFalse(BadgeCondition.Unverifiable.met(everything))
        assertFalse(unlocked(evaluateBadges(everything), "Leyenda NightWatch"))
    }

    @Test
    fun withEverythingDone_eightOfNineUnlock_andOnlyLeyendaStaysLocked() {
        val state = evaluateBadges(facts(lessons = topics(6), labs = topics(6), examPassed = true, examPerfect = true, bestStreak = 30))

        assertEquals(8, state.count { it.unlocked })
        assertEquals(listOf("Leyenda NightWatch"), state.filter { !it.unlocked }.map { it.definition.name })
    }

    // ---- the real badges from the history ----

    @Test
    fun aNewAccount_hasNoBadge_andNoStreak() {
        val summary = summarizeProgress(LearningSnapshot.Empty, now, bogota)

        assertEquals(0, summary.unlockedCount)
        assertEquals(9, summary.totalCount)
        assertEquals(Streak.None, summary.streak)
    }

    @Test
    fun theFirstLesson_unlocksPrimerosPasos_andNothingElse() {
        val snapshot = LearningSnapshot(
            viewedLessons = setOf(LearningTopic.CONTRASENAS),
            completedLabs = emptySet(),
            exam = ExamStatus.NotTaken,
            lessonDates = mapOf(LearningTopic.CONTRASENAS to at(today)),
        )

        val summary = summarizeProgress(snapshot, now, bogota)

        assertEquals(listOf("Primeros pasos"), summary.badges.filter { it.unlocked }.map { it.definition.name })
        assertEquals(1, summary.streak.current)
    }

    @Test
    fun theIslandOfAThemeUnlocksItsBadge_onlyWithLessonAndLab() {
        val lessonOnly = LearningSnapshot(setOf(LearningTopic.CONTRASENAS), emptySet(), ExamStatus.NotTaken)
        val both = LearningSnapshot(setOf(LearningTopic.CONTRASENAS), setOf(LearningTopic.CONTRASENAS), ExamStatus.NotTaken)

        assertFalse(unlocked(summarizeProgress(lessonOnly, now, bogota).badges, "Maestro de Contraseñas"))
        assertTrue(unlocked(summarizeProgress(both, now, bogota).badges, "Maestro de Contraseñas"))
    }

    @Test
    fun aBadgeOnceEarnedByStreak_doesNotLockAgainWhenTheStreakBreaks() {
        // Seven consecutive days of practice long ago (exam attempts are the only moment that can repeat many times).
        val dates = (20L..26L).map { at(today.minusDays(it)) }
        val snapshot = LearningSnapshot(
            viewedLessons = emptySet(),
            completedLabs = emptySet(),
            exam = ExamStatus.NotTaken,
            examAttempts = dates.map { ExamAttempt(3, false, it) },
        )

        val summary = summarizeProgress(snapshot, now, bogota)

        assertEquals(0, summary.streak.current)
        assertEquals(7, summary.streak.best)
        assertTrue(unlocked(summary.badges, "Racha de 7 días"))
        assertFalse(unlocked(summary.badges, "Racha de 30 días"))
    }

    @Test
    fun moreHistory_neverLocksABadge() {
        val small = LearningSnapshot(setOf(LearningTopic.CONTRASENAS), setOf(LearningTopic.CONTRASENAS), ExamStatus.NotTaken)
        val bigger = LearningSnapshot(
            viewedLessons = LearningTopic.entries.toSet(),
            completedLabs = LearningTopic.entries.toSet(),
            exam = ExamStatus(true, null),
            examAttempts = listOf(ExamAttempt(6, true, at(today))),
        )

        val before = summarizeProgress(small, now, bogota).badges
        val after = summarizeProgress(bigger, now, bogota).badges

        before.zip(after).forEach { (a, b) -> assertTrue(a.definition.name, !a.unlocked || b.unlocked) }
    }

    // ---- the provider ----

    @Test
    fun theProvider_readsTheRepository_withTheClockAndZoneItIsGiven() = runTest {
        val repository = FakeLearningProgressRepository()
        repository.viewedLessons += LearningTopic.CONTRASENAS
        repository.lessonTimes[LearningTopic.CONTRASENAS] = at(today.minusDays(1))
        val provider = ProgressSummaryProvider(repository, Clock.fixed(now, bogota))

        val summary = provider.get()

        assertEquals(Streak(1, 1), summary.streak)
        assertEquals(1, summary.unlockedCount)
        assertEquals(1, repository.getSnapshotCallCount)
    }

    @Test
    fun theProvider_letsTheRepositoryErrorsThrough_asDataAccessException() = runTest {
        FailureKind.entries.forEach { kind ->
            val repository = FakeLearningProgressRepository().apply { readFailure = kind }
            try {
                ProgressSummaryProvider(repository, Clock.fixed(now, bogota)).get()
                fail("Debía fallar con $kind")
            } catch (e: DataAccessException) {
                assertEquals(kind, e.kind)
            }
        }
    }
}
