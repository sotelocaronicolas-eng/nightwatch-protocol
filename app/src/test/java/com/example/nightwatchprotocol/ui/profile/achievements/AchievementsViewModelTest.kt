package com.example.nightwatchprotocol.ui.profile.achievements

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.BadgeCatalog
import com.example.nightwatchprotocol.data.learning.FakeLearningProgressRepository
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.learning.ProgressSummaryProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AchievementsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val progress = FakeLearningProgressRepository()
    private val now = Instant.parse("2026-10-18T15:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun viewModel() = AchievementsViewModel(ProgressSummaryProvider(progress, clock))

    private fun AchievementsViewModel.unlockedNames() =
        uiState.value.badges.filter { it.unlocked }.map { it.definition.name }

    // ---- loading ----

    @Test
    fun beforeTheFirstRead_itIsLoading_withTheNineBadgesLocked_andNoRealCounter() = runTest {
        val vm = viewModel()

        val state = vm.uiState.value
        assertEquals(AchievementsStatus.Loading, state.status)
        assertEquals(9, state.badges.size)
        assertTrue(state.badges.none { it.unlocked })
        assertFalse(state.loaded)
    }

    // ---- the real state ----

    @Test
    fun aNewAccount_hasZeroOfNine() = runTest {
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(AchievementsStatus.Ready, state.status)
        assertTrue(state.loaded)
        assertEquals(0, state.unlockedCount)
        assertEquals(9, state.totalCount)
        assertEquals(BadgeCatalog.map { it.name }, state.badges.map { it.definition.name })
    }

    @Test
    fun theCounter_comesFromTheCalculation_notFromAFixedNumber() = runTest {
        progress.viewedLessons += LearningTopic.CONTRASENAS
        progress.completedLabs += LearningTopic.CONTRASENAS
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf("Primeros pasos", "Maestro de Contraseñas"), vm.unlockedNames())
        assertEquals(2, vm.uiState.value.unlockedCount)
    }

    @Test
    fun passingTheExam_unlocksGraduado() = runTest {
        progress.recordExamAttempt(5)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf("Graduado NightWatch"), vm.unlockedNames())
    }

    @Test
    fun aFailedExam_doesNotUnlockGraduado() = runTest {
        progress.recordExamAttempt(4)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.unlockedNames().isEmpty())
    }

    @Test
    fun aSevenDayStreak_unlocksTheBadge_evenIfItWasLostLater() = runTest {
        // Seven consecutive days a month ago (attempts are the only moment that can repeat): the best streak is 7, the current 0.
        progress.clock = { now.minusSeconds(86_400L * 40) }
        repeat(7) {
            progress.recordExamAttempt(1)
            progress.clock = { now.minusSeconds(86_400L * (39 - it)) }
        }
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf("Racha de 7 días"), vm.unlockedNames())
    }

    @Test
    fun theBadgesBlockedOnPurpose_neverUnlock_evenWithEverythingDone() = runTest {
        LearningTopic.entries.forEach {
            progress.viewedLessons += it
            progress.completedLabs += it
        }
        progress.recordExamAttempt(6)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertFalse("Leyenda NightWatch" in vm.unlockedNames())
        assertTrue("Graduado NightWatch" in vm.unlockedNames())
    }

    // ---- errors ----

    @Test
    fun aFailedFirstRead_failsWithNoRealCounter_andRetryRecovers() = runTest {
        progress.readFailure = FailureKind.Offline
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(AchievementsStatus.Failed(offline = true), vm.uiState.value.status)
        assertFalse(vm.uiState.value.loaded)

        progress.readFailure = null
        progress.viewedLessons += LearningTopic.CONTRASENAS
        vm.refresh()
        advanceUntilIdle()

        assertEquals(AchievementsStatus.Ready, vm.uiState.value.status)
        assertEquals(1, vm.uiState.value.unlockedCount)
    }

    @Test
    fun aNonNetworkError_isNotOffline() = runTest {
        progress.readFailure = FailureKind.PermissionDenied
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(AchievementsStatus.Failed(offline = false), vm.uiState.value.status)
    }

    @Test
    fun retry_afterAnError_showsLoadingFirst() = runTest {
        progress.readFailure = FailureKind.Offline
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        progress.readFailure = null
        vm.uiState.test {
            awaitItem() // Failed
            vm.refresh()
            assertEquals(AchievementsStatus.Loading, awaitItem().status)
            assertEquals(AchievementsStatus.Ready, awaitItem().status)
        }
    }

    @Test
    fun aFailedLaterRead_keepsTheLastKnownBadges() = runTest {
        progress.viewedLessons += LearningTopic.CONTRASENAS
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        progress.readFailure = FailureKind.Offline
        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.status is AchievementsStatus.Failed)
        assertTrue(vm.uiState.value.loaded)
        assertEquals(listOf("Primeros pasos"), vm.unlockedNames())
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
