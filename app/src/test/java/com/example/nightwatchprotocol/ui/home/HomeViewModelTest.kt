package com.example.nightwatchprotocol.ui.home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.account.AccountProfile
import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.avatar.AvatarDisplay
import com.example.nightwatchprotocol.data.avatar.AvatarRepository
import com.example.nightwatchprotocol.data.avatar.FakeAvatarCache
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.device.DeviceIdStore
import com.example.nightwatchprotocol.data.device.DeviceRecord
import com.example.nightwatchprotocol.data.device.DeviceSessionManager
import com.example.nightwatchprotocol.data.device.FakeDeviceInfoProvider
import com.example.nightwatchprotocol.data.device.FakeDeviceRepository
import com.example.nightwatchprotocol.data.learning.FakeLearningContentRepository
import com.example.nightwatchprotocol.data.learning.FakeLearningProgressRepository
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import com.example.nightwatchprotocol.data.quiz.FakeQuizResultRepository
import com.example.nightwatchprotocol.data.quiz.PlacementLevel
import com.example.nightwatchprotocol.data.quiz.PlacementResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val account = FakeAccountRepository()
    private val quiz = FakeQuizResultRepository()
    private val profile = FakeProfileRepository()
    private val avatars = AvatarRepository(profile, FakeAvatarCache(), UnconfinedTestDispatcher())
    private val progress = FakeLearningProgressRepository()
    private val content = FakeLearningContentRepository()
    private val devices = FakeDeviceRepository()
    private val sessionManager = DeviceSessionManager(
        auth,
        devices,
        DeviceIdStore.inMemory { "device-me" },
        FakeDeviceInfoProvider(),
        avatars,
    )

    private fun viewModel(
        initialTab: HomeTab = HomeTab.Inicio,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ) = HomeViewModel(
        initialTab = initialTab,
        savedStateHandle = savedStateHandle,
        authRepository = auth,
        accountRepository = account,
        avatarRepository = avatars,
        quizResultRepository = quiz,
        learningProgressRepository = progress,
        learningContentRepository = content,
        sessionManager = sessionManager,
    )

    private fun completeIslands(count: Int) {
        LearningTopic.entries.take(count).forEach {
            progress.viewedLessons += it
            progress.completedLabs += it
        }
    }

    // ---- the chapa: name, avatar, level, streak ----

    @Test
    fun showsNameInitialAndTheFixedStreak() = runTest {
        account.profile = AccountProfile("Nicolás Sotelo", "n@example.com", null, hasPassword = true)
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("Nicolás Sotelo", state.displayName)
        assertEquals(AvatarDisplay.Initial("N"), state.avatar)
        assertEquals(15, state.streakDays)
    }

    @Test
    fun levelIsTheOneFromTheQuiz_asBajoMedioOAlto() = runTest {
        PlacementLevel.entries.forEach { level ->
            quiz.getResultResult = Result.success(PlacementResult(level, emptySet()))
            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(level.name, vm.uiState.value.level)
        }
    }

    @Test
    fun levelIsNotAssessed_withoutResult_withAFailedRead_andWithoutSession() = runTest {
        quiz.getResultResult = Result.success(null)
        val noResult = viewModel()
        advanceUntilIdle()
        assertEquals("Sin evaluar", noResult.uiState.value.level)

        quiz.getResultResult = Result.failure(IllegalStateException("sin red"))
        val failed = viewModel()
        advanceUntilIdle()
        assertEquals("Sin evaluar", failed.uiState.value.level)

        auth.currentUserUid = null
        val noSession = viewModel()
        assertEquals("Sin evaluar", noSession.uiState.value.level)
    }

    @Test
    fun levelIsNullWhileItIsBeingRead() = runTest {
        val vm = viewModel()

        assertEquals(null, vm.uiState.value.level)
        advanceUntilIdle()
        assertEquals("Sin evaluar", vm.uiState.value.level)
    }

    // ---- tabs ----

    @Test
    fun startsOnTheTabOfTheRoute() = runTest {
        assertEquals(HomeTab.Inicio, viewModel().uiState.value.selectedTab)
        assertEquals(HomeTab.Ruta, viewModel(initialTab = HomeTab.Ruta).uiState.value.selectedTab)
    }

    @Test
    fun changingTab_updatesTheState() = runTest {
        val vm = viewModel()

        vm.onTabSelected(HomeTab.Labs)
        assertEquals(HomeTab.Labs, vm.uiState.value.selectedTab)
        vm.onTabSelected(HomeTab.Ruta)
        assertEquals(HomeTab.Ruta, vm.uiState.value.selectedTab)
        vm.onTabSelected(HomeTab.Inicio)
        assertEquals(HomeTab.Inicio, vm.uiState.value.selectedTab)
    }

    @Test
    fun theSelectedTab_survivesARecreation_becauseItIsInTheSavedState() = runTest {
        val handle = SavedStateHandle()
        viewModel(initialTab = HomeTab.Inicio, savedStateHandle = handle).onTabSelected(HomeTab.Labs)

        // A rotation or process death builds a new view model on the same saved state; the route still says Inicio.
        val recreated = viewModel(initialTab = HomeTab.Inicio, savedStateHandle = handle)

        assertEquals(HomeTab.Labs, recreated.uiState.value.selectedTab)
    }

    @Test
    fun social_doesNotChangeTheTab_andEmitsTheNoticeOnce() = runTest {
        val vm = viewModel(initialTab = HomeTab.Ruta)
        advanceUntilIdle()

        vm.events.test {
            vm.onSocialClicked()
            assertEquals(HomeEvent.ShowComingSoon, awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(HomeTab.Ruta, vm.uiState.value.selectedTab)
    }

    @Test
    fun eachTapOnSocial_emitsOneNotice() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSocialClicked()
            vm.onSocialClicked()
            assertEquals(HomeEvent.ShowComingSoon, awaitItem())
            assertEquals(HomeEvent.ShowComingSoon, awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun theTwoDimmedCards_emitTheSameNotice_andDoNotChangeTheTab() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onComingSoonCardClicked()
            assertEquals(HomeEvent.ShowComingSoon, awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(HomeTab.Inicio, vm.uiState.value.selectedTab)
    }

    @Test
    fun profile_asksToOpenTheUserMenu_andKeepsTheTab() = runTest {
        val vm = viewModel()
        vm.onTabSelected(HomeTab.Labs)
        advanceUntilIdle()

        vm.events.test {
            vm.onProfileClicked()
            assertEquals(HomeEvent.OpenUserMenu, awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(HomeTab.Labs, vm.uiState.value.selectedTab)
    }

    // ---- sign out ----

    @Test
    fun signOut_endsTheSession_andNavigatesToLogin() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSignOutClicked()
            assertEquals(HomeEvent.SignedOut, awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals(1, auth.signOutCallCount)
    }

    @Test
    fun signOut_removesThisDeviceFromTheRegistry() = runTest {
        devices.put("test-uid", DeviceRecord("device-me", "Pixel", 1L, revoked = false))
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSignOutClicked()
            assertEquals(HomeEvent.SignedOut, awaitItem())
        }

        assertEquals(listOf("device-me"), devices.deleteCalls)
    }

    @Test
    fun signOut_tappedTwice_signsOutOnce() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSignOutClicked()
            vm.onSignOutClicked()
            assertEquals(HomeEvent.SignedOut, awaitItem())
            advanceUntilIdle()
            expectNoEvents()
        }

        assertEquals(1, auth.signOutCallCount)
    }

    @Test
    fun aSessionRevokedFromAnotherDevice_leadsToLogin() = runTest {
        devices.put("test-uid", DeviceRecord("device-me", "Pixel", 1L, revoked = true))
        val vm = viewModel()

        vm.events.test {
            advanceUntilIdle()
            assertEquals(HomeEvent.SignedOut, awaitItem())
        }
        assertEquals(1, auth.signOutCallCount)
    }

    @Test
    fun anActiveSession_doesNotLeaveHome() = runTest {
        val vm = viewModel()

        vm.events.test {
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(0, auth.signOutCallCount)
    }

    // ---- "Lección diaria" ----

    @Test
    fun dailyLesson_isLoadingUntilTheProgressIsRead() = runTest {
        val vm = viewModel()

        assertEquals(DailyLessonState.Loading, vm.uiState.value.dailyLesson)
        vm.refreshProgress()
        assertEquals(DailyLessonState.Loading, vm.uiState.value.dailyLesson)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.dailyLesson is DailyLessonState.Pending)
    }

    @Test
    fun dailyLesson_withNothingDone_isTheFirstLesson() = runTest {
        val vm = viewModel()
        vm.refreshProgress()
        advanceUntilIdle()

        assertEquals(
            DailyLessonState.Pending(LearningTopic.CONTRASENAS, "Lección CONTRASENAS"),
            vm.uiState.value.dailyLesson,
        )
    }

    @Test
    fun dailyLesson_isTheNextIslandsLesson_afterCompletingOne() = runTest {
        completeIslands(1)
        val vm = viewModel()
        vm.refreshProgress()
        advanceUntilIdle()

        assertEquals(
            DailyLessonState.Pending(LearningTopic.PHISHING, "Lección PHISHING"),
            vm.uiState.value.dailyLesson,
        )
    }

    @Test
    fun dailyLesson_withTheLessonSeenButTheLabMissing_hasNothingPending() = runTest {
        progress.viewedLessons += LearningTopic.CONTRASENAS
        val vm = viewModel()
        vm.refreshProgress()
        advanceUntilIdle()

        assertEquals(DailyLessonState.NoneLeft, vm.uiState.value.dailyLesson)
    }

    @Test
    fun dailyLesson_withEverythingDone_hasNothingPending() = runTest {
        completeIslands(6)
        val vm = viewModel()
        vm.refreshProgress()
        advanceUntilIdle()

        assertEquals(DailyLessonState.NoneLeft, vm.uiState.value.dailyLesson)
    }

    @Test
    fun dailyLesson_offline_isAnOfflineFailure_andTheRestOfHomeStillWorks() = runTest {
        progress.readFailure = FailureKind.Offline
        account.profile = AccountProfile("Nicolás Sotelo", "n@example.com", null, hasPassword = true)
        quiz.getResultResult = Result.success(PlacementResult(PlacementLevel.MEDIO, emptySet()))
        val vm = viewModel()
        vm.refreshProgress()
        advanceUntilIdle()

        assertEquals(DailyLessonState.Failed(offline = true), vm.uiState.value.dailyLesson)
        // The failure only touches the card: name, level and the tabs keep working.
        assertEquals("Nicolás Sotelo", vm.uiState.value.displayName)
        assertEquals("MEDIO", vm.uiState.value.level)
        vm.onTabSelected(HomeTab.Ruta)
        assertEquals(HomeTab.Ruta, vm.uiState.value.selectedTab)
    }

    @Test
    fun dailyLesson_otherReadFailures_areNotOffline() = runTest {
        listOf(FailureKind.PermissionDenied, FailureKind.Unknown).forEach { kind ->
            progress.readFailure = kind
            val vm = viewModel()
            vm.refreshProgress()
            advanceUntilIdle()

            assertEquals(DailyLessonState.Failed(offline = false), vm.uiState.value.dailyLesson)
        }
    }

    @Test
    fun dailyLesson_whenTheContentCannotBeRead_failsWithoutCrashing() = runTest {
        content.failure = IllegalStateException("asset dañado")
        val vm = viewModel()
        vm.refreshProgress()
        advanceUntilIdle()

        assertEquals(DailyLessonState.Failed(offline = false), vm.uiState.value.dailyLesson)
    }

    @Test
    fun retryingAfterAFailure_showsLoadingAndThenTheLesson() = runTest {
        progress.readFailure = FailureKind.Offline
        val vm = viewModel()
        vm.refreshProgress()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.dailyLesson is DailyLessonState.Failed)

        progress.readFailure = null
        vm.refreshProgress()
        assertEquals(DailyLessonState.Loading, vm.uiState.value.dailyLesson)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.dailyLesson is DailyLessonState.Pending)
    }

    @Test
    fun refreshingWhileAReadIsRunning_doesNotReadTwice() = runTest {
        val vm = viewModel()

        vm.refreshProgress()
        vm.refreshProgress()
        advanceUntilIdle()

        assertEquals(1, progress.getSnapshotCallCount)
    }

    @Test
    fun refreshing_keepsTheLastAnswerUntilTheNewOneArrives() = runTest {
        val vm = viewModel()
        vm.refreshProgress()
        advanceUntilIdle()
        val first = vm.uiState.value.dailyLesson
        assertTrue(first is DailyLessonState.Pending)

        completeIslands(6)
        vm.refreshProgress()
        // No flicker back to "loading": the card keeps its last answer while the new read is in flight.
        assertEquals(first, vm.uiState.value.dailyLesson)
        advanceUntilIdle()

        assertEquals(DailyLessonState.NoneLeft, vm.uiState.value.dailyLesson)
        assertFalse(vm.uiState.value.dailyLesson is DailyLessonState.Loading)
    }

    @Test
    fun theDailyLessonDoesNotDependOnTheLevel() = runTest {
        PlacementLevel.entries.forEach { level ->
            quiz.getResultResult = Result.success(PlacementResult(level, emptySet()))
            val vm = viewModel()
            vm.refreshProgress()
            advanceUntilIdle()

            assertEquals(
                DailyLessonState.Pending(LearningTopic.CONTRASENAS, "Lección CONTRASENAS"),
                vm.uiState.value.dailyLesson,
            )
        }
    }
}
