package com.example.nightwatchprotocol.ui.labs

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.common.DataAccessException
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.FakeLearningContentRepository
import com.example.nightwatchprotocol.data.learning.FakeLearningProgressRepository
import com.example.nightwatchprotocol.data.learning.IslandState
import com.example.nightwatchprotocol.data.learning.LearningTopic
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LabsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val progress = FakeLearningProgressRepository()
    private val content = FakeLearningContentRepository()

    private fun viewModel() = LabsViewModel(progress, content)

    private fun completeIslands(count: Int) {
        LearningTopic.entries.take(count).forEach {
            progress.viewedLessons += it
            progress.completedLabs += it
        }
    }

    private fun LabsViewModel.states() = uiState.value.labs.map { it.state }

    // ---- states of the cards ----

    @Test
    fun beforeTheFirstRead_itIsLoading_withNoCards() = runTest {
        val vm = viewModel()

        assertEquals(LabsStatus.Loading, vm.uiState.value.status)
        assertTrue(vm.uiState.value.labs.isEmpty())
    }

    @Test
    fun nothingDone_firstLabOpen_restLocked_examLocked() = runTest {
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(LabsStatus.Ready, vm.uiState.value.status)
        assertEquals(listOf(IslandState.Available) + List(5) { IslandState.Locked }, vm.states())
        assertEquals(IslandState.Locked, vm.uiState.value.finalTest)
    }

    @Test
    fun theSixCards_followTheTopicOrder_withTheTitlesOfTheContent() = runTest {
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(LearningTopic.entries, vm.uiState.value.labs.map { it.topic })
        assertEquals(content.content.labs.map { it.cardTitle }, vm.uiState.value.labs.map { it.title })
        assertEquals(content.content.labs.map { it.difficulty }, vm.uiState.value.labs.map { it.difficulty })
    }

    @Test
    fun twoIslandsCompleted_twoLabsCompleted_thirdOpen_restLocked() = runTest {
        completeIslands(2)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(
            listOf(IslandState.Completed, IslandState.Completed, IslandState.Available) + List(3) { IslandState.Locked },
            vm.states(),
        )
    }

    @Test
    fun aLabIsOpen_evenIfItsLessonWasNeverSeen() = runTest {
        // Decision Q4: the lab of an unlocked island does not need its lesson.
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertTrue(progress.viewedLessons.isEmpty())
        assertEquals(IslandState.Available, vm.states().first())
    }

    @Test
    fun aLabDone_showsCompleted_evenWithTheLessonNotSeen_andTheNextOneOpensOnlyWithTheIslandDone() = runTest {
        progress.completedLabs += LearningTopic.CONTRASENAS
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        // The island is not complete (no lesson), so the second lab stays locked.
        assertEquals(IslandState.Completed, vm.states()[0])
        assertEquals(IslandState.Locked, vm.states()[1])
    }

    @Test
    fun examStaysLocked_withFiveIslands_andOpensWithSix() = runTest {
        completeIslands(5)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(IslandState.Locked, vm.uiState.value.finalTest)

        completeIslands(6)
        vm.refresh()
        advanceUntilIdle()
        assertEquals(IslandState.Available, vm.uiState.value.finalTest)
        assertTrue(vm.states().all { it == IslandState.Completed })
    }

    @Test
    fun examShowsCompleted_withAPassedAttempt() = runTest {
        completeIslands(6)
        progress.examAttempts += 6
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(IslandState.Completed, vm.uiState.value.finalTest)
    }

    // ---- taps ----

    @Test
    fun tapOnALockedLab_saysCompleteThePreviousIsland_andDoesNotOpen() = runTest {
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onLabClicked(LearningTopic.MALWARE)
            assertEquals(LabsEvent.ShowMessage(LabsMessage.CompletePreviousIsland), awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun tapOnAnOpenLab_opensIt() = runTest {
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onLabClicked(LearningTopic.CONTRASENAS)
            assertEquals(LabsEvent.OpenLab(LearningTopic.CONTRASENAS), awaitItem())
        }
    }

    @Test
    fun tapOnACompletedLab_opensItAgain() = runTest {
        completeIslands(1)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onLabClicked(LearningTopic.CONTRASENAS)
            assertEquals(LabsEvent.OpenLab(LearningTopic.CONTRASENAS), awaitItem())
        }
    }

    @Test
    fun tapOnTheLockedExam_saysCompleteTheSixIslands() = runTest {
        completeIslands(5)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onFinalTestClicked()
            assertEquals(LabsEvent.ShowMessage(LabsMessage.CompleteAllIslands), awaitItem())
        }
    }

    @Test
    fun tapOnTheUnlockedExam_saysComingSoon_provisionally() = runTest {
        completeIslands(6)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onFinalTestClicked()
            assertEquals(LabsEvent.ShowMessage(LabsMessage.ComingSoon), awaitItem())
        }
    }

    @Test
    fun tapsAreIgnoredWhileTheFirstReadRuns() = runTest {
        val vm = viewModel()

        vm.events.test {
            vm.onLabClicked(LearningTopic.CONTRASENAS)
            vm.onFinalTestClicked()
            expectNoEvents()
        }
    }

    // ---- errors ----

    @Test
    fun offlineRead_failsWithOffline_showsLabOneOpen_andNoOtherDoor() = runTest {
        progress.readFailure = FailureKind.Offline
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(LabsStatus.Failed(offline = true), vm.uiState.value.status)
        assertEquals(listOf(IslandState.Available) + List(5) { IslandState.Locked }, vm.states())
        assertEquals(IslandState.Locked, vm.uiState.value.finalTest)
    }

    @Test
    fun otherReadError_failsWithoutOffline() = runTest {
        progress.readFailure = FailureKind.Unknown
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(LabsStatus.Failed(offline = false), vm.uiState.value.status)
    }

    @Test
    fun retry_afterAnError_showsLoading_andThenTheRealProgress() = runTest {
        completeIslands(2)
        progress.readFailure = FailureKind.Offline
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.status is LabsStatus.Failed)

        progress.readFailure = null
        vm.uiState.test {
            awaitItem() // Failed
            vm.refresh()
            assertEquals(LabsStatus.Loading, awaitItem().status)
            val ready = awaitItem()
            assertEquals(LabsStatus.Ready, ready.status)
            assertEquals(IslandState.Completed, ready.labs[0].state)
            assertEquals(IslandState.Available, ready.labs[2].state)
        }
    }

    @Test
    fun retry_thatFailsAgain_staysFailed() = runTest {
        progress.readFailure = FailureKind.Offline
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(LabsStatus.Failed(offline = true), vm.uiState.value.status)
        assertEquals(2, progress.getSnapshotCallCount)
    }

    @Test
    fun aFailedLaterRead_keepsTheLastKnownProgress() = runTest {
        completeIslands(2)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        progress.readFailure = FailureKind.Offline
        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.status is LabsStatus.Failed)
        assertEquals(IslandState.Completed, vm.states()[0])
        assertEquals(IslandState.Available, vm.states()[2])
    }

    @Test
    fun contentThatCannotBeRead_failsWithNoCards() = runTest {
        content.failure = DataAccessException(FailureKind.Unknown)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.status is LabsStatus.Failed)
        assertTrue(vm.uiState.value.labs.isEmpty())
    }

    @Test
    fun aRefreshWhileAnotherRuns_isNotDuplicated() = runTest {
        val vm = viewModel()
        vm.refresh()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(1, progress.getSnapshotCallCount)
    }
}
