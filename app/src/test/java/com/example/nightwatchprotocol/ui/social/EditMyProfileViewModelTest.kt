package com.example.nightwatchprotocol.ui.social

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.learning.Streak
import com.example.nightwatchprotocol.data.learning.ProgressSummary
import com.example.nightwatchprotocol.data.social.FakeFighterRepository
import com.example.nightwatchprotocol.data.social.FakeSocialProfileRepository
import com.example.nightwatchprotocol.data.social.ProfileDisplaySettings
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** "Editar tu perfil": a local draft until "GUARDAR CAMBIOS", dropped by "CANCELAR", kept when saving fails. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditMyProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeSocialProfileRepository(SocialProfile("Juan", "NIGHT-JUAN22", hideProgress = false))
    private val fighter = FakeFighterRepository()

    private fun viewModel() = EditMyProfileViewModel(
        MyProfileLoader(profiles, fighter, { ProgressSummary(Streak.None, emptyList()) }),
        profiles,
    )

    private suspend fun kotlinx.coroutines.test.TestScope.loadedViewModel(): EditMyProfileViewModel {
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        return vm
    }

    @Test
    fun theDraftStartsFromWhatTheServerHas() = runTest {
        profiles.profile = profiles.profile!!.copy(display = ProfileDisplaySettings(profileBg = 3, showStats = false))

        val vm = loadedViewModel()

        assertTrue(vm.uiState.value.loaded)
        assertEquals(ProfileDisplaySettings(profileBg = 3, showStats = false), vm.uiState.value.draft)
    }

    @Test
    fun theEyes_changeOnlyTheDraft_neverTheServer() = runTest {
        val vm = loadedViewModel()

        vm.onToggle(ProfileSection.Badges)
        vm.onToggle(ProfileSection.History)

        assertEquals(ProfileDisplaySettings(showBadges = false, showHistory = false), vm.uiState.value.draft)
        assertEquals(ProfileDisplaySettings(), profiles.profile!!.display)
        assertEquals(0, profiles.displayCalls)
    }

    @Test
    fun anEyeTappedTwice_isBackWhereItWas() = runTest {
        val vm = loadedViewModel()

        vm.onToggle(ProfileSection.Stats)
        vm.onToggle(ProfileSection.Stats)

        assertEquals(ProfileDisplaySettings(), vm.uiState.value.draft)
    }

    @Test
    fun choosingABackground_changesTheDraft_andClosesThePicker() = runTest {
        val vm = loadedViewModel()
        vm.onPickerOpened()
        assertTrue(vm.uiState.value.pickerOpen)

        vm.onBackgroundSelected(4)

        assertEquals(4, vm.uiState.value.draft.profileBg)
        assertFalse(vm.uiState.value.pickerOpen)
        assertEquals(0, profiles.displayCalls)
    }

    @Test
    fun anUnknownBackground_isNoBackground() = runTest {
        val vm = loadedViewModel()

        vm.onBackgroundSelected(9)

        assertEquals(0, vm.uiState.value.draft.profileBg)
    }

    @Test
    fun save_sendsTheWholeDraft_andReportsSaved() = runTest {
        val vm = loadedViewModel()
        vm.onBackgroundSelected(2)
        vm.onToggle(ProfileSection.Stats)

        vm.events.test {
            vm.onSaveClicked()
            advanceUntilIdle()
            assertEquals(EditMyProfileEvent.Saved, awaitItem())
        }

        assertEquals(ProfileDisplaySettings(profileBg = 2, showStats = false), profiles.profile!!.display)
        assertEquals(1, profiles.displayCalls)
        assertFalse(vm.uiState.value.saving)
    }

    @Test
    fun aSecondTapWhileSaving_doesNotSaveTwice() = runTest {
        val vm = loadedViewModel()

        vm.onSaveClicked()
        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(1, profiles.displayCalls)
    }

    @Test
    fun aFailedSave_keepsTheDraft_showsTheError_andCanRetry() = runTest {
        val vm = loadedViewModel()
        vm.onBackgroundSelected(5)
        vm.onToggle(ProfileSection.History)
        profiles.failure = SocialError.Offline

        vm.onSaveClicked()
        advanceUntilIdle()

        val failed = vm.uiState.value
        assertEquals(SocialError.Offline, failed.saveError)
        assertEquals(ProfileDisplaySettings(profileBg = 5, showHistory = false), failed.draft)
        assertFalse(failed.saving)

        profiles.failure = null
        vm.events.test {
            vm.onSaveClicked()
            advanceUntilIdle()
            assertEquals(EditMyProfileEvent.Saved, awaitItem())
        }
        assertEquals(ProfileDisplaySettings(profileBg = 5, showHistory = false), profiles.profile!!.display)
    }

    @Test
    fun aServerWithout0012_failsAsUnavailable_andKeepsTheDraft() = runTest {
        val vm = loadedViewModel()
        vm.onBackgroundSelected(1)
        profiles.failure = SocialError.Unavailable

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(SocialError.Unavailable, vm.uiState.value.saveError)
        assertEquals(1, vm.uiState.value.draft.profileBg)
    }

    @Test
    fun cancel_savesNothing_andReportsDiscarded() = runTest {
        val vm = loadedViewModel()
        vm.onBackgroundSelected(3)
        vm.onToggle(ProfileSection.Badges)

        vm.events.test {
            vm.onCancelClicked()
            advanceUntilIdle()
            assertEquals(EditMyProfileEvent.Discarded, awaitItem())
        }

        assertEquals(0, profiles.displayCalls)
        assertEquals(ProfileDisplaySettings(), profiles.profile!!.display)
    }

    @Test
    fun comingBackToTheFront_doesNotOverwriteTheDraft() = runTest {
        val vm = loadedViewModel()
        vm.onToggle(ProfileSection.Stats)

        vm.load()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.draft.showStats)
    }

    @Test
    fun aFailedLoad_canBeRetried_andStartsTheDraftThen() = runTest {
        profiles.failure = SocialError.Offline
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.loaded)
        assertEquals(SocialError.Offline, vm.uiState.value.loadError)

        profiles.failure = null
        vm.load()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.loaded)
        assertEquals(ProfileDisplaySettings(), vm.uiState.value.draft)
    }

    @Test
    fun nothingIsSaved_beforeTheProfileIsLoaded() = runTest {
        profiles.failure = SocialError.Offline
        val vm = viewModel()
        vm.load()
        advanceUntilIdle()

        vm.onSaveClicked()
        advanceUntilIdle()

        assertEquals(0, profiles.displayCalls)
    }
}
