package com.example.nightwatchprotocol.data.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningProgressTest {
    private val order = LearningTopic.entries

    private fun snapshot(
        lessons: Set<LearningTopic> = emptySet(),
        labs: Set<LearningTopic> = emptySet(),
        passed: Boolean = false,
    ) = LearningSnapshot(
        viewedLessons = lessons,
        completedLabs = labs,
        exam = ExamStatus(hasPassed = passed, lastAttempt = null),
    )

    private fun completedFirst(count: Int): Set<LearningTopic> = order.take(count).toSet()

    private fun states(progress: LearningProgress) = progress.islands.map { it.state }

    @Test
    fun nothingDone_firstIslandOpen_theRestLocked_examLocked() {
        val progress = deriveLearningProgress(LearningSnapshot.Empty)

        assertEquals(order, progress.islands.map { it.topic })
        assertEquals(
            listOf(IslandState.Available) + List(5) { IslandState.Locked },
            states(progress),
        )
        assertEquals(ExamGate.Locked, progress.examGate)
        assertFalse(progress.examPassed)
        assertEquals(0, progress.completedCount)
        assertEquals(7, progress.totalCount)
    }

    @Test
    fun anIsland_needsTheLessonAndTheLab_toBeCompleted() {
        val topic = LearningTopic.CONTRASENAS

        val onlyLesson = deriveLearningProgress(snapshot(lessons = setOf(topic)))
        val onlyLab = deriveLearningProgress(snapshot(labs = setOf(topic)))
        val both = deriveLearningProgress(snapshot(lessons = setOf(topic), labs = setOf(topic)))

        assertEquals(IslandState.Available, onlyLesson.island(topic).state)
        assertTrue(onlyLesson.island(topic).lessonViewed)
        assertFalse(onlyLesson.island(topic).labCompleted)
        assertEquals(IslandState.Available, onlyLab.island(topic).state)
        assertFalse(onlyLab.island(topic).lessonViewed)
        assertTrue(onlyLab.island(topic).labCompleted)
        assertEquals(IslandState.Completed, both.island(topic).state)
        assertEquals(1, both.completedCount)
    }

    @Test
    fun theNextIslandUnlocks_onlyWhenThePreviousOneIsCompleted() {
        val lessonOnly = deriveLearningProgress(snapshot(lessons = setOf(LearningTopic.CONTRASENAS)))
        assertEquals(IslandState.Locked, lessonOnly.island(LearningTopic.PHISHING).state)

        val completed = deriveLearningProgress(
            snapshot(lessons = setOf(LearningTopic.CONTRASENAS), labs = setOf(LearningTopic.CONTRASENAS)),
        )
        assertEquals(IslandState.Available, completed.island(LearningTopic.PHISHING).state)
        assertEquals(IslandState.Locked, completed.island(LearningTopic.MALWARE).state)
    }

    @Test
    fun islandsUnlockInOrder_overAProgressOfThreeCompleted() {
        val progress = deriveLearningProgress(snapshot(lessons = completedFirst(3), labs = completedFirst(3)))

        assertEquals(
            List(3) { IslandState.Completed } + IslandState.Available + List(2) { IslandState.Locked },
            states(progress),
        )
        assertEquals(3, progress.completedCount)
    }

    @Test
    fun theExam_isLockedUntilTheSixIslandsAreCompleted() {
        val five = deriveLearningProgress(snapshot(lessons = completedFirst(5), labs = completedFirst(5)))
        assertEquals(ExamGate.Locked, five.examGate)

        val sixLessonsFiveLabs = deriveLearningProgress(snapshot(lessons = completedFirst(6), labs = completedFirst(5)))
        assertEquals(ExamGate.Locked, sixLessonsFiveLabs.examGate)

        val six = deriveLearningProgress(snapshot(lessons = completedFirst(6), labs = completedFirst(6)))
        assertEquals(ExamGate.Available, six.examGate)
        assertEquals(6, six.completedCount)
    }

    @Test
    fun theFinalTest_countsAsCompleted_withAPassedAttempt() {
        val all = completedFirst(6)

        val notPassed = deriveLearningProgress(snapshot(lessons = all, labs = all, passed = false))
        val passed = deriveLearningProgress(snapshot(lessons = all, labs = all, passed = true))

        assertEquals(6, notPassed.completedCount)
        assertFalse(notPassed.examPassed)
        assertEquals(7, passed.completedCount)
        assertTrue(passed.examPassed)
        // Passing does not close the exam: it can be retaken without limit (decision P11).
        assertEquals(ExamGate.Available, passed.examGate)
    }

    @Test
    fun theCounter_neverExceedsSeven() {
        val all = completedFirst(6)
        assertEquals(7, deriveLearningProgress(snapshot(lessons = all, labs = all, passed = true)).completedCount)
    }

    @Test
    fun aLab_doesNotNeedTheLessonToBeSeen_onlyItsIslandUnlocked() {
        val progress = deriveLearningProgress(LearningSnapshot.Empty)

        assertTrue(progress.canOpenLab(LearningTopic.CONTRASENAS))
        assertTrue(progress.canOpenLesson(LearningTopic.CONTRASENAS))
        assertFalse(progress.island(LearningTopic.CONTRASENAS).lessonViewed)
        assertFalse(progress.canOpenLab(LearningTopic.PHISHING))
        assertFalse(progress.canOpenLesson(LearningTopic.PHISHING))
    }

    @Test
    fun aCompletedIsland_canStillBeOpened() {
        val progress = deriveLearningProgress(snapshot(lessons = completedFirst(2), labs = completedFirst(2)))

        assertTrue(progress.canOpenLab(LearningTopic.CONTRASENAS))
        assertTrue(progress.canOpenLesson(LearningTopic.PHISHING))
        assertTrue(progress.canOpenLab(LearningTopic.MALWARE))
        assertFalse(progress.canOpenLab(LearningTopic.INGENIERIA_SOCIAL))
    }

    @Test
    fun nextPendingLesson_isTheFirstIslandWithoutItsLessonSeen() {
        assertEquals(LearningTopic.CONTRASENAS, deriveLearningProgress(LearningSnapshot.Empty).nextPendingLesson)

        val afterOne = deriveLearningProgress(snapshot(lessons = completedFirst(1), labs = completedFirst(1)))
        assertEquals(LearningTopic.PHISHING, afterOne.nextPendingLesson)
    }

    @Test
    fun nextPendingLesson_isNull_whenTheLessonIsSeenButTheLabIsMissing() {
        // The next island is still locked, so there is no lesson to open (decision Q3: "No tienes lecciones pendientes").
        val progress = deriveLearningProgress(snapshot(lessons = setOf(LearningTopic.CONTRASENAS)))

        assertNull(progress.nextPendingLesson)
    }

    @Test
    fun nextPendingLesson_isNull_whenAllTheLessonsAreSeen() {
        val all = completedFirst(6)

        assertNull(deriveLearningProgress(snapshot(lessons = all, labs = all)).nextPendingLesson)
        assertNull(deriveLearningProgress(snapshot(lessons = all)).nextPendingLesson)
    }

    @Test
    fun nextPendingLesson_skipsASeenLessonOfAnOpenIsland() {
        // Island 1 completed, island 2 open with its lesson already seen, island 3 locked: nothing pending.
        val progress = deriveLearningProgress(
            snapshot(
                lessons = completedFirst(2),
                labs = completedFirst(1),
            ),
        )

        assertEquals(IslandState.Available, progress.island(LearningTopic.PHISHING).state)
        assertNull(progress.nextPendingLesson)
    }

    @Test
    fun theLevelOfThePerson_doesNotAppearInTheRules_everybodyStartsOnIslandOne() {
        // deriveLearningProgress has no level input: the same empty progress opens island one for BAJO, MEDIO and ALTO.
        assertEquals(IslandState.Available, deriveLearningProgress(LearningSnapshot.Empty).islands.first().state)
    }
}
