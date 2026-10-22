package com.example.nightwatchprotocol.ui.social

import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.social.FakeFighterRepository
import com.example.nightwatchprotocol.data.social.FakeFriendsRepository
import com.example.nightwatchprotocol.data.social.FakePairStreakRepository
import com.example.nightwatchprotocol.data.social.FakeSocialProfileRepository
import com.example.nightwatchprotocol.data.social.Friend
import com.example.nightwatchprotocol.data.social.FighterSummary
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialProfile
import com.example.nightwatchprotocol.data.social.SocialRejection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SocialHomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profiles = FakeSocialProfileRepository(SocialProfile("valeNightwatch", "NIGHT-7F3K2A", hideProgress = false))
    private val friends = FakeFriendsRepository()
    private val pairs = FakePairStreakRepository()
    private val fighter = FakeFighterRepository()

    // The view models are created INSIDE each test: the dispatcher rule is applied after the fields are built.
    private fun viewModel() = SocialHomeViewModel(profiles, friends, pairs, fighter)

    private fun SocialHomeViewModel.state() = uiState.value

    @Test
    fun nothingIsReadUntilTheTabAsks() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(SocialHomeStatus.Loading, vm.state().status)
        assertEquals(0, profiles.getCalls)
    }

    @Test
    fun withoutAUsername_socialIsNotEnabled_andNoTileIsRead() = runTest {
        profiles.profile = null
        val vm = viewModel()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(SocialHomeStatus.NeedsUsername, vm.state().status)
        assertEquals(0, friends.calls)
        assertEquals(0, pairs.calls)
        assertEquals(0, fighter.calls)
    }

    @Test
    fun withAUsername_theThreeTilesShowWhatTheServerSaid() = runTest {
        friends.friends += Friend("mateo", "NIGHT-9H4M5P", 0, false)
        friends.friends += Friend("sofia", "NIGHT-AAAAAA", 2, true)
        pairs.pairs += FakePairStreakRepository.activePair("mateo", "NIGHT-9H4M5P", 4)
        pairs.pairs += FakePairStreakRepository.activePair("sofia", "NIGHT-AAAAAA", 2)
        fighter.summary = FighterSummary(120, 2, 200, wins = 5, losses = 2, draws = 1, battleStreak = 1, history = emptyList())
        val vm = viewModel()

        vm.refresh()
        advanceUntilIdle()

        val state = vm.state()
        assertEquals(SocialHomeStatus.Ready, state.status)
        assertEquals(4, state.pairStreakDays)
        assertEquals(2, state.friendsCount)
        assertEquals(5, state.wins)
        assertEquals(2, state.losses)
        assertEquals(1, state.draws)
        assertNull(state.tilesError)
    }

    @Test
    fun theBigNumber_isTheStreakOfThePairWithMostDays_andZeroWithoutPairs() = runTest {
        val vm = viewModel()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(0, vm.state().pairStreakDays)
    }

    @Test
    fun aFailedProfileRead_isAnErrorWithRetry_andRetryRecovers() = runTest {
        profiles.failure = SocialError.Offline
        val vm = viewModel()

        vm.refresh()
        advanceUntilIdle()
        assertEquals(SocialHomeStatus.Failed(SocialError.Offline), vm.state().status)

        profiles.failure = null
        vm.refresh()
        advanceUntilIdle()
        assertEquals(SocialHomeStatus.Ready, vm.state().status)
    }

    @Test
    fun aTileThatFails_keepsTheOthers_andReportsItsError() = runTest {
        friends.failure = SocialError.RateLimited
        fighter.summary = FighterSummary(0, 1, 100, wins = 3, losses = 1, draws = 0, battleStreak = 1, history = emptyList())
        val vm = viewModel()

        vm.refresh()
        advanceUntilIdle()

        val state = vm.state()
        assertEquals(SocialHomeStatus.Ready, state.status)
        assertNull(state.friendsCount)
        assertEquals(3, state.wins)
        assertEquals(SocialError.RateLimited, state.tilesError)
    }

    @Test
    fun aRefreshThatFails_keepsTheLastKnownNumbers() = runTest {
        friends.friends += Friend("mateo", "NIGHT-9H4M5P", 0, false)
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, vm.state().friendsCount)

        friends.failure = SocialError.Offline
        vm.refresh()
        advanceUntilIdle()

        assertEquals(1, vm.state().friendsCount)
        assertEquals(SocialError.Offline, vm.state().tilesError)
    }

    @Test
    fun ifTheServerSaysSocialIsNotEnabled_theUsernameScreenReturns() = runTest {
        pairs.failure = SocialError.NotEnabled
        val vm = viewModel()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(SocialHomeStatus.NeedsUsername, vm.state().status)
    }

    @Test
    fun aReadAlreadyRunning_isNotDuplicated() = runTest {
        val vm = viewModel()

        vm.refresh()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(1, profiles.getCalls)
    }

    // ---- the username ----

    @Test
    fun theUsernameIsLimitedToTwentyCharacters_andTypingClearsTheError() = runTest {
        profiles.profile = null
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()
        vm.onUsernameChanged("ab")
        vm.onCreateUsername()
        assertEquals(SocialError.Rejected(SocialRejection.InvalidUsername), vm.state().usernameError)

        vm.onUsernameChanged("a".repeat(30))

        assertEquals(20, vm.state().username.length)
        assertNull(vm.state().usernameError)
    }

    @Test
    fun anInvalidUsername_isRejectedWithoutCallingTheServer() = runTest {
        profiles.profile = null
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.onUsernameChanged("con espacio")
        vm.onCreateUsername()
        advanceUntilIdle()

        assertEquals(SocialError.Rejected(SocialRejection.InvalidUsername), vm.state().usernameError)
        assertEquals(0, profiles.createCalls)
        assertFalse(vm.state().creating)
    }

    @Test
    fun aTakenUsername_showsItsMessage_andKeepsTheForm() = runTest {
        profiles.profile = null
        profiles.takenUsernames += "valeNightwatch"
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.onUsernameChanged("VALEnightwatch")
        vm.onCreateUsername()
        advanceUntilIdle()

        assertEquals(SocialError.Rejected(SocialRejection.UsernameTaken), vm.state().usernameError)
        assertEquals(SocialHomeStatus.NeedsUsername, vm.state().status)
        assertFalse(vm.state().creating)
    }

    @Test
    fun creatingTheUsername_enablesSocial_andLoadsTheTiles() = runTest {
        profiles.profile = null
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.onUsernameChanged("  valeNightwatch ")
        vm.onCreateUsername()
        advanceUntilIdle()

        assertEquals(SocialHomeStatus.Ready, vm.state().status)
        assertEquals("valeNightwatch", profiles.profile!!.username)
        assertEquals(1, friends.calls)
        assertTrue(vm.state().usernameError == null && !vm.state().creating)
    }

    @Test
    fun aProgressHiddenBeforeSocialExisted_staysHiddenInTheNewProfile() = runTest {
        profiles.profile = null
        val preferences = com.example.nightwatchprotocol.data.preferences.UserPreferences.inMemory()
        preferences.set(com.example.nightwatchprotocol.data.preferences.UserSetting.HIDE_PUBLIC_PROGRESS, true)
        val vm = SocialHomeViewModel(profiles, friends, pairs, fighter, preferences)
        vm.refresh()
        advanceUntilIdle()

        vm.onUsernameChanged("valeNightwatch")
        vm.onCreateUsername()
        advanceUntilIdle()

        assertTrue(profiles.profile!!.hideProgress)
        assertEquals(1, profiles.hideCalls)
    }

    @Test
    fun withoutAHiddenProgress_theNewProfileIsLeftAsTheServerCreatedIt() = runTest {
        profiles.profile = null
        val preferences = com.example.nightwatchprotocol.data.preferences.UserPreferences.inMemory()
        val vm = SocialHomeViewModel(profiles, friends, pairs, fighter, preferences)
        vm.refresh()
        advanceUntilIdle()

        vm.onUsernameChanged("valeNightwatch")
        vm.onCreateUsername()
        advanceUntilIdle()

        assertFalse(profiles.profile!!.hideProgress)
        assertEquals(0, profiles.hideCalls)
    }

    @Test
    fun aSecondTapWhileCreating_isIgnored() = runTest {
        profiles.profile = null
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()
        vm.onUsernameChanged("valeNightwatch")

        vm.onCreateUsername()
        vm.onCreateUsername()
        advanceUntilIdle()

        assertEquals(1, profiles.createCalls)
    }

    @Test
    fun aFailedCreation_showsTheErrorOfTheCall() = runTest {
        profiles.profile = null
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()
        profiles.failure = SocialError.Offline
        vm.onUsernameChanged("valeNightwatch")

        vm.onCreateUsername()
        advanceUntilIdle()

        assertEquals(SocialError.Offline, vm.state().usernameError)
        assertEquals(SocialHomeStatus.NeedsUsername, vm.state().status)
    }
}
