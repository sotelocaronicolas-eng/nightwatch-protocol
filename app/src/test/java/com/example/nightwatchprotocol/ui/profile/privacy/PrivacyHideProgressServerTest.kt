package com.example.nightwatchprotocol.ui.profile.privacy

import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.biometric.FakeBiometricCapabilities
import com.example.nightwatchprotocol.data.device.FakeDeviceRepository
import com.example.nightwatchprotocol.data.preferences.UserPreferences
import com.example.nightwatchprotocol.data.preferences.UserSetting
import com.example.nightwatchprotocol.data.profile.DataStoreProfileExtrasCache
import com.example.nightwatchprotocol.data.profile.FakeDataExportWriter
import com.example.nightwatchprotocol.data.profile.FakeProfileRepository
import com.example.nightwatchprotocol.data.profile.ProfileDataExporter
import com.example.nightwatchprotocol.data.profile.ProfileExtrasRepository
import com.example.nightwatchprotocol.data.quiz.FakeQuizResultRepository
import com.example.nightwatchprotocol.data.social.FakeSocialProfileRepository
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** "Ocultar mi progreso público" with Social enabled: the value lives in `social_profile.hide_progress` (module 4, Part 2). */
@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyHideProgressServerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profile = FakeProfileRepository()
    private val preferences = UserPreferences.inMemory()
    private val social = FakeSocialProfileRepository(SocialProfile("valeNightwatch", "NIGHT-7F3K2A", hideProgress = false))

    private fun viewModel(socialRepository: FakeSocialProfileRepository? = social) = PrivacySecurityViewModel(
        FakeAuthRepository(),
        profile,
        preferences,
        FakeBiometricCapabilities(),
        ProfileDataExporter(
            FakeAccountRepository(),
            profile,
            FakeQuizResultRepository(),
            ProfileExtrasRepository(profile, DataStoreProfileExtrasCache.inMemory()),
            clock = { 0L },
        ),
        FakeDataExportWriter(),
        FakeDeviceRepository(),
        socialRepository,
    )

    @Test
    fun withSocial_theSwitchShowsTheValueOfTheServer_notTheLocalOne() = runTest {
        preferences.set(UserSetting.HIDE_PUBLIC_PROGRESS, false)
        social.profile = social.profile!!.copy(hideProgress = true)
        val vm = viewModel()

        advanceUntilIdle()

        assertTrue(vm.uiState.value.hidePublicProgress)
    }

    @Test
    fun withSocial_turningItOn_isSavedInTheServer_andFollowedLocally() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onHidePublicProgressChanged(true)
        advanceUntilIdle()

        assertTrue(social.profile!!.hideProgress)
        assertEquals(1, social.hideCalls)
        assertTrue(vm.uiState.value.hidePublicProgress)
        assertTrue(preferences.settings.first()[UserSetting.HIDE_PUBLIC_PROGRESS])
        assertNull(vm.uiState.value.hideProgressError)
    }

    @Test
    fun withSocial_turningItBackOff_isSavedInTheServer() = runTest {
        social.profile = social.profile!!.copy(hideProgress = true)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onHidePublicProgressChanged(false)
        advanceUntilIdle()

        assertFalse(social.profile!!.hideProgress)
        assertFalse(vm.uiState.value.hidePublicProgress)
    }

    @Test
    fun ifTheServerDoesNotTakeTheChange_nothingChanges_andTheErrorIsShown() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        social.failure = SocialError.Offline

        vm.onHidePublicProgressChanged(true)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.hidePublicProgress)
        assertEquals(SocialError.Offline, vm.uiState.value.hideProgressError)
        assertFalse(preferences.settings.first()[UserSetting.HIDE_PUBLIC_PROGRESS])
    }

    @Test
    fun withoutAUsername_itStaysLocal_andTheServerIsNotWritten() = runTest {
        social.profile = null
        val vm = viewModel()
        advanceUntilIdle()

        vm.onHidePublicProgressChanged(true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hidePublicProgress)
        assertTrue(preferences.settings.first()[UserSetting.HIDE_PUBLIC_PROGRESS])
        assertEquals(0, social.hideCalls)
    }

    @Test
    fun withoutTheSocialRepository_itIsTheOldLocalBehavior() = runTest {
        val vm = viewModel(socialRepository = null)
        advanceUntilIdle()

        vm.onHidePublicProgressChanged(true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.hidePublicProgress)
        assertTrue(preferences.settings.first()[UserSetting.HIDE_PUBLIC_PROGRESS])
    }
}
