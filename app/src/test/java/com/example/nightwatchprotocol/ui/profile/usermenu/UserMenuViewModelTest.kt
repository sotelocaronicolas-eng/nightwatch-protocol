package com.example.nightwatchprotocol.ui.profile.usermenu

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.account.AccountProfile
import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.avatar.AvatarDisplay
import com.example.nightwatchprotocol.data.avatar.AvatarRepository
import com.example.nightwatchprotocol.data.avatar.FakeAvatarCache
import com.example.nightwatchprotocol.data.device.DeviceIdStore
import com.example.nightwatchprotocol.data.device.DeviceSessionManager
import com.example.nightwatchprotocol.data.device.FakeDeviceInfoProvider
import com.example.nightwatchprotocol.data.device.FakeDeviceRepository
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.FakeLearningProgressRepository
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.learning.ProgressSummaryProvider
import com.example.nightwatchprotocol.data.learning.StreakState
import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import com.example.nightwatchprotocol.data.profile.RemoteProfile
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.encoding.Base64
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserMenuViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val account = FakeAccountRepository()
    private val profile = FakeProfileRepository()
    private val avatars = AvatarRepository(profile, FakeAvatarCache(), UnconfinedTestDispatcher())

    private val devices = FakeDeviceRepository()
    private val sessionManager = DeviceSessionManager(
        auth,
        devices,
        DeviceIdStore.inMemory { "device-me" },
        FakeDeviceInfoProvider(),
        avatars,
    )

    private val progress = FakeLearningProgressRepository()
    private val now = Instant.parse("2026-10-18T15:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun viewModel() = UserMenuViewModel(auth, account, avatars, sessionManager, ProgressSummaryProvider(progress, clock))

    @Test
    fun showsNameAndInitial_andTheStreakIsLoadingUntilItIsRead() = runTest {
        account.profile = AccountProfile("Nicolás Sotelo", "n@example.com", null, hasPassword = true)
        val vm = viewModel()
        assertEquals(StreakState.Loading, vm.uiState.value.streak)
        advanceUntilIdle()

        assertEquals("Nicolás Sotelo", vm.uiState.value.displayName)
        assertEquals(AvatarDisplay.Initial("N"), vm.uiState.value.avatar)
        assertEquals(StreakState.Days(0), vm.uiState.value.streak)
    }

    @Test
    fun theStreak_isTheRealCurrentOne_aliveWithYesterdayAlone() = runTest {
        progress.lessonTimes[LearningTopic.CONTRASENAS] = now.minusSeconds(86_400L * 2)
        progress.labTimes[LearningTopic.CONTRASENAS] = now.minusSeconds(86_400L)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(StreakState.Days(2), vm.uiState.value.streak)
    }

    @Test
    fun aFailedRead_saysNotAvailable_andRefreshRecovers() = runTest {
        progress.readFailure = FailureKind.Unknown
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(StreakState.Unavailable, vm.uiState.value.streak)

        progress.readFailure = null
        progress.lessonTimes[LearningTopic.CONTRASENAS] = now
        vm.refreshStreak()
        advanceUntilIdle()

        assertEquals(StreakState.Days(1), vm.uiState.value.streak)
    }

    @Test
    fun aFailedLaterRead_keepsTheLastKnownStreak() = runTest {
        progress.lessonTimes[LearningTopic.CONTRASENAS] = now
        val vm = viewModel()
        advanceUntilIdle()

        progress.readFailure = FailureKind.Offline
        vm.refreshStreak()
        advanceUntilIdle()

        assertEquals(StreakState.Days(1), vm.uiState.value.streak)
    }

    @Test
    fun avatarFromTheServer_replacesTheInitial() = runTest {
        val photo = byteArrayOf(1, 2, 3)
        profile.getProfileResult = Result.success(RemoteProfile(avatarBase64 = Base64.Default.encode(photo)))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(AvatarDisplay.Photo(photo), vm.uiState.value.avatar)
    }

    @Test
    fun signOut_signsOutClearsTheAvatarAndEmitsTheEvent() = runTest {
        val photo = byteArrayOf(1, 2, 3)
        profile.getProfileResult = Result.success(RemoteProfile(avatarBase64 = Base64.Default.encode(photo)))
        val vm = viewModel()
        advanceUntilIdle()

        vm.events.test {
            vm.onSignOutClicked()
            assertEquals(UserMenuEvent.SignedOut, awaitItem())
        }

        assertEquals(1, auth.signOutCallCount)
        assertNull(avatars.avatar.value)
    }
}
