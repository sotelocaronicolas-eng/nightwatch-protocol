package com.example.nightwatchprotocol.ui.path

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.FakeLearningContentRepository
import com.example.nightwatchprotocol.data.learning.FakeLearningProgressRepository
import com.example.nightwatchprotocol.data.learning.IslandState
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.quiz.FakeQuizResultRepository
import com.example.nightwatchprotocol.data.quiz.PlacementLevel
import com.example.nightwatchprotocol.data.quiz.PlacementResult
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LearningPathViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val quiz = FakeQuizResultRepository()
    private val progress = FakeLearningProgressRepository()
    private val content = FakeLearningContentRepository()

    private fun viewModel() = LearningPathViewModel(auth, quiz, progress, content)

    private fun completeIslands(count: Int) {
        LearningTopic.entries.take(count).forEach {
            progress.viewedLessons += it
            progress.completedLabs += it
        }
    }

    private fun LearningPathViewModel.states() = uiState.value.islands.map { it.state }

    // ---- states of the islands and the "Prueba Final" ----

    @Test
    fun nothingDone_firstIslandOpen_restLocked_finalTestLocked_zeroOfSeven() = runTest {
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(PathStatus.Ready, vm.uiState.value.status)
        assertEquals(listOf(IslandState.Available) + List(5) { IslandState.Locked }, vm.states())
        assertEquals(IslandState.Locked, vm.uiState.value.finalTest)
        assertEquals(0, vm.uiState.value.completedCount)
        assertEquals(7, vm.uiState.value.totalCount)
    }

    @Test
    fun theIslandsComeInOrder_withTheNameAndEmojiOfTheLessons() = runTest {
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(LearningTopic.entries, vm.uiState.value.islands.map { it.topic })
        vm.uiState.value.islands.forEach { island ->
            assertEquals("Isla ${island.topic.name}", island.name)
            assertEquals("📘", island.emoji)
        }
    }

    @Test
    fun threeCompleted_threeDoneOneAvailableTwoLocked_threeOfSeven() = runTest {
        completeIslands(3)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(
            List(3) { IslandState.Completed } + IslandState.Available + List(2) { IslandState.Locked },
            vm.states(),
        )
        assertEquals(3, vm.uiState.value.completedCount)
        assertEquals(IslandState.Locked, vm.uiState.value.finalTest)
    }

    @Test
    fun anIslandWithOnlyTheLessonOrOnlyTheLab_isAvailable_andTheNextOneStaysLocked() = runTest {
        progress.viewedLessons += LearningTopic.CONTRASENAS
        val onlyLesson = viewModel().also { it.refresh() }
        advanceUntilIdle()
        assertEquals(IslandState.Available, onlyLesson.states()[0])
        assertEquals(IslandState.Locked, onlyLesson.states()[1])

        progress.viewedLessons.clear()
        progress.completedLabs += LearningTopic.CONTRASENAS
        val onlyLab = viewModel().also { it.refresh() }
        advanceUntilIdle()
        assertEquals(IslandState.Available, onlyLab.states()[0])
        assertEquals(IslandState.Locked, onlyLab.states()[1])
    }

    @Test
    fun sixCompleted_unlocksTheFinalTest_withSixOfSeven() = runTest {
        completeIslands(6)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(List(6) { IslandState.Completed }, vm.states())
        assertEquals(IslandState.Available, vm.uiState.value.finalTest)
        assertEquals(6, vm.uiState.value.completedCount)
    }

    @Test
    fun aPassedExam_completesTheFinalTest_andCountsSevenOfSeven() = runTest {
        completeIslands(6)
        progress.examAttempts += 5
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(IslandState.Completed, vm.uiState.value.finalTest)
        assertEquals(7, vm.uiState.value.completedCount)
    }

    @Test
    fun aFailedExam_doesNotCompleteTheFinalTest() = runTest {
        completeIslands(6)
        progress.examAttempts += 4
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(IslandState.Available, vm.uiState.value.finalTest)
        assertEquals(6, vm.uiState.value.completedCount)
    }

    // ---- level ----

    @Test
    fun levelIsTheOneFromTheQuiz_orNotAssessed() = runTest {
        PlacementLevel.entries.forEach { level ->
            quiz.getResultResult = Result.success(PlacementResult(level, emptySet()))
            val vm = viewModel()
            advanceUntilIdle()
            assertEquals(level.name, vm.uiState.value.level)
        }

        quiz.getResultResult = Result.success(null)
        val none = viewModel()
        advanceUntilIdle()
        assertEquals("Sin evaluar", none.uiState.value.level)

        auth.currentUserUid = null
        assertEquals("Sin evaluar", viewModel().uiState.value.level)
    }

    @Test
    fun theLevelDoesNotChangeWhatIsUnlocked() = runTest {
        PlacementLevel.entries.forEach { level ->
            quiz.getResultResult = Result.success(PlacementResult(level, emptySet()))
            val vm = viewModel()
            vm.refresh()
            advanceUntilIdle()

            assertEquals(listOf(IslandState.Available) + List(5) { IslandState.Locked }, vm.states())
        }
    }

    // ---- taps ----

    @Test
    fun tappingALockedIsland_showsTheLockNotice_andDoesNotOpenAnything() = runTest {
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onIslandClicked(LearningTopic.PHISHING)
            assertEquals(LearningPathEvent.ShowMessage(PathMessage.CompletePreviousIsland), awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun tappingAnAvailableIsland_opensItsLesson() = runTest {
        completeIslands(2)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onIslandClicked(LearningTopic.MALWARE)
            assertEquals(LearningPathEvent.OpenLesson(LearningTopic.MALWARE), awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun tappingACompletedIsland_opensItsLessonAgain() = runTest {
        completeIslands(2)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onIslandClicked(LearningTopic.CONTRASENAS)
            assertEquals(LearningPathEvent.OpenLesson(LearningTopic.CONTRASENAS), awaitItem())
        }
    }

    @Test
    fun everyTopic_opensItsOwnLesson_whenUnlocked() = runTest {
        completeIslands(6)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            LearningTopic.entries.forEach { topic ->
                vm.onIslandClicked(topic)
                assertEquals(LearningPathEvent.OpenLesson(topic), awaitItem())
            }
        }
    }

    @Test
    fun theLockedFinalTest_saysToCompleteTheSixIslands() = runTest {
        completeIslands(5)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onFinalTestClicked()
            assertEquals(LearningPathEvent.ShowMessage(PathMessage.CompleteAllIslands), awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun theUnlockedFinalTest_saysComingSoon_untilTheExamExists() = runTest {
        completeIslands(6)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onFinalTestClicked()
            assertEquals(LearningPathEvent.ShowMessage(PathMessage.ComingSoon), awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun tapsAreIgnoredWhileTheFirstReadRuns() = runTest {
        val vm = viewModel()
        vm.refresh()

        vm.events.test {
            vm.onIslandClicked(LearningTopic.PHISHING)
            vm.onFinalTestClicked()
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    // ---- loading and errors ----

    @Test
    fun beforeTheFirstRead_itIsLoading_withNoIslandsYet() = runTest {
        val vm = viewModel()

        assertEquals(PathStatus.Loading, vm.uiState.value.status)
        assertTrue(vm.uiState.value.islands.isEmpty())
    }

    @Test
    fun anOfflineReadError_showsTheMapWithIslandOneOpen_andAnOfflineNotice() = runTest {
        progress.readFailure = FailureKind.Offline
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(PathStatus.Failed(offline = true), vm.uiState.value.status)
        assertEquals(listOf(IslandState.Available) + List(5) { IslandState.Locked }, vm.states())
        assertEquals(0, vm.uiState.value.completedCount)
    }

    @Test
    fun withAReadError_islandOneStillOpensItsLesson_andTheRestSayLocked() = runTest {
        progress.readFailure = FailureKind.Offline
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onIslandClicked(LearningTopic.CONTRASENAS)
            assertEquals(LearningPathEvent.OpenLesson(LearningTopic.CONTRASENAS), awaitItem())
            vm.onIslandClicked(LearningTopic.PHISHING)
            assertEquals(LearningPathEvent.ShowMessage(PathMessage.CompletePreviousIsland), awaitItem())
        }
    }

    @Test
    fun otherReadErrors_areNotOffline() = runTest {
        listOf(FailureKind.PermissionDenied, FailureKind.Unknown).forEach { kind ->
            progress.readFailure = kind
            val vm = viewModel()
            vm.refresh()
            advanceUntilIdle()

            assertEquals(PathStatus.Failed(offline = false), vm.uiState.value.status)
        }
    }

    @Test
    fun retrying_afterAnError_loadsTheRealProgress() = runTest {
        completeIslands(2)
        progress.readFailure = FailureKind.Offline
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.status is PathStatus.Failed)

        progress.readFailure = null
        vm.refresh()
        // At once, before the read ends: the notice gives way to the loading state.
        assertEquals(PathStatus.Loading, vm.uiState.value.status)
        advanceUntilIdle()

        assertEquals(PathStatus.Ready, vm.uiState.value.status)
        assertEquals(2, vm.uiState.value.completedCount)
        assertEquals(IslandState.Available, vm.states()[2])
    }

    @Test
    fun retrying_andFailingAgain_stillShowsTheNotice() = runTest {
        progress.readFailure = FailureKind.Offline
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(PathStatus.Failed(offline = true), vm.uiState.value.status)
        assertEquals(2, progress.getSnapshotCallCount)
    }

    @Test
    fun aFailedRefresh_keepsTheLastKnownProgress() = runTest {
        completeIslands(3)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        progress.readFailure = FailureKind.Offline
        vm.refresh()
        advanceUntilIdle()

        assertEquals(PathStatus.Failed(offline = true), vm.uiState.value.status)
        assertEquals(3, vm.uiState.value.completedCount)
        assertEquals(IslandState.Completed, vm.states()[0])
    }

    @Test
    fun refreshingWhileAReadRuns_doesNotReadTwice() = runTest {
        val vm = viewModel()

        vm.refresh()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(1, progress.getSnapshotCallCount)
    }

    @Test
    fun refreshing_picksUpAnIslandCompletedMeanwhile() = runTest {
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(IslandState.Locked, vm.states()[1])

        completeIslands(1)
        vm.refresh()
        advanceUntilIdle()

        assertEquals(IslandState.Available, vm.states()[1])
        assertEquals(1, vm.uiState.value.completedCount)
    }

    @Test
    fun ifTheContentCannotBeRead_itFailsWithoutCrashing_andHasNoIslandLabels() = runTest {
        content.failure = IllegalStateException("asset dañado")
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(PathStatus.Failed(offline = false), vm.uiState.value.status)
        assertTrue(vm.uiState.value.islands.isEmpty())
    }
}
