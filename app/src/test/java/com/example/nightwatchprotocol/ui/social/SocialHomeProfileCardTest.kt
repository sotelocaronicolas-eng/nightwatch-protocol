package com.example.nightwatchprotocol.ui.social

import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.social.FakeFighterRepository
import com.example.nightwatchprotocol.data.social.FakeFriendsRepository
import com.example.nightwatchprotocol.data.social.FakePairStreakRepository
import com.example.nightwatchprotocol.data.social.FakeSocialProfileRepository
import com.example.nightwatchprotocol.data.social.FighterSummary
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** The "Tu Perfil" card of Social - Inicio: the combatant level ("LVL n") and the photo of the person. */
@OptIn(ExperimentalCoroutinesApi::class)
class SocialHomeProfileCardTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val key = "6f1c1c40-2c1b-4d43-9a54-2b7a3f6f9a10"
    private val profiles = FakeSocialProfileRepository(SocialProfile("Juan", "NIGHT-JUAN22", hideProgress = false, avatarKey = key, avatarVersion = 3))
    private val fighter = FakeFighterRepository(FighterSummary(250, 3, 400, 1, 0, 0, 1, emptyList()))

    private fun viewModel() = SocialHomeViewModel(profiles, FakeFriendsRepository(), FakePairStreakRepository(), fighter)

    @Test
    fun theCard_getsTheLevelAndThePhotoOfThePerson() = runTest {
        val vm = viewModel()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(3, vm.uiState.value.level)
        assertEquals(key, vm.uiState.value.avatarKey)
        assertEquals(3, vm.uiState.value.avatarVersion)
    }

    @Test
    fun aFailedBattleRead_leavesTheLevelUnknown_butStillShowsThePhoto() = runTest {
        fighter.failure = SocialError.Offline
        val vm = viewModel()

        vm.refresh()
        advanceUntilIdle()

        assertNull(vm.uiState.value.level)
        assertEquals(key, vm.uiState.value.avatarKey)
    }
}
