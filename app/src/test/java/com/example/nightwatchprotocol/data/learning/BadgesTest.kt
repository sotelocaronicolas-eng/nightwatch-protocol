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
    fun aBadgeForModule4_saysSo_inItsNote_andLeyendaIsMarkedAsAnAssumptionOfTheGroup() {
        BadgeCatalog.filter { it.condition == BadgeCondition.AvailableFromModule4 }
            .forEach { assertTrue(it.name, it.note!!.contains("módulo 4")) }
        val leyenda = BadgeCatalog.first { it.name == "Leyenda NightWatch" }
        assertTrue(leyenda.note!!.contains("SUPUESTO elegido por el grupo"))
        assertEquals(
            BadgeCondition.AllOf(listOf(BadgeCondition.AllIslandsCompleted, BadgeCondition.ExamPerfect)),
            leyenda.condition,
        )
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
    fun aBadgeForModule4_neverUnlocks_whateverTheFacts() {
        val everything = facts(lessons = topics(6), labs = topics(6), examPassed = true, examPerfect = true, bestStreak = 999)
        assertFalse(BadgeCondition.AvailableFromModule4.met(everything))
    }

    @Test
    fun withEverythingDone_theNineUnlock() {
        val state = evaluateBadges(facts(lessons = topics(6), labs = topics(6), examPassed = true, examPerfect = true, bestStreak = 30))

        assertEquals(9, state.count { it.unlocked })
    }

    @Test
    fun withAPassedButNotPerfectExam_onlyLeyendaStaysLocked() {
        val state = evaluateBadges(facts(lessons = topics(6), labs = topics(6), examPassed = true, examPerfect = false, bestStreak = 30))

        assertEquals(listOf("Leyenda NightWatch"), state.filter { !it.unlocked }.map { it.definition.name })
    }

    // ---- Leyenda NightWatch: six islands AND a perfect exam ----

    private fun leyenda(facts: BadgeFacts) = unlocked(evaluateBadges(facts), "Leyenda NightWatch")

    @Test
    fun leyenda_needsTheSixIslands_andThePerfectExam() {
        // Neither of the two.
        assertFalse(leyenda(facts()))
        // Perfect exam with five islands (the edge: 5 of 6).
        assertFalse(leyenda(facts(lessons = topics(5), labs = topics(5), examPassed = true, examPerfect = true)))
        // Six islands with an exam passed with 5 right answers (not perfect).
        assertFalse(leyenda(facts(lessons = topics(6), labs = topics(6), examPassed = true, examPerfect = false)))
        // Six lessons but not the labs, or the labs but not the lessons: the islands are not completed.
        assertFalse(leyenda(facts(lessons = topics(6), labs = topics(0), examPassed = true, examPerfect = true)))
        assertFalse(leyenda(facts(lessons = topics(0), labs = topics(6), examPassed = true, examPerfect = true)))
        // Both.
        assertTrue(leyenda(facts(lessons = topics(6), labs = topics(6), examPassed = true, examPerfect = true)))
    }

    @Test
    fun leyenda_fromTheHistory_atTheEdgeOfFiveAndSixRightAnswers() {
        fun snapshot(correct: Int, islands: Int) = LearningSnapshot(
            viewedLessons = topics(islands),
            completedLabs = topics(islands),
            exam = ExamStatus(hasPassed = correct >= 5, lastAttempt = null),
            examAttempts = listOf(ExamAttempt(correct, correct >= 5, takenAt = at(today))),
        )

        assertFalse(unlocked(summarizeProgress(snapshot(5, 6), now, bogota).badges, "Leyenda NightWatch"))
        assertFalse(unlocked(summarizeProgress(snapshot(6, 5), now, bogota).badges, "Leyenda NightWatch"))
        assertTrue(unlocked(summarizeProgress(snapshot(6, 6), now, bogota).badges, "Leyenda NightWatch"))
    }

    @Test
    fun leyenda_onceEarned_staysEarned_withMoreAttempts() {
        val earned = LearningSnapshot(
            viewedLessons = topics(6),
            completedLabs = topics(6),
            exam = ExamStatus(true, null),
            examAttempts = listOf(ExamAttempt(2, false, at(today)), ExamAttempt(6, true, at(today.minusDays(3)))),
        )

        assertTrue(unlocked(summarizeProgress(earned, now, bogota).badges, "Leyenda NightWatch"))
    }

    @Test
    fun theCompositeConditions_followTheirParts() {
        val sixIslands = facts(lessons = topics(6), labs = topics(6))
        assertTrue(BadgeCondition.AllIslandsCompleted.met(sixIslands))
        assertFalse(BadgeCondition.AllIslandsCompleted.met(facts(lessons = topics(6), labs = topics(5))))
        assertTrue(BadgeCondition.AllOf(emptyList()).met(facts()))
        assertFalse(BadgeCondition.AllOf(listOf(BadgeCondition.AllIslandsCompleted, BadgeCondition.ExamPassed)).met(sixIslands))
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
