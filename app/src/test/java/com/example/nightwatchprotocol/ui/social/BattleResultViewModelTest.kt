package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.learning.FakeLearningContentRepository
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.social.BATTLE_ID
import com.example.nightwatchprotocol.data.social.BattleEndReason
import com.example.nightwatchprotocol.data.social.BattleMode
import com.example.nightwatchprotocol.data.social.BattleOutcome
import com.example.nightwatchprotocol.data.social.BattleResultView
import com.example.nightwatchprotocol.data.social.BattleScenarios
import com.example.nightwatchprotocol.data.social.BattleState
import com.example.nightwatchprotocol.data.social.BattleStatus
import com.example.nightwatchprotocol.data.social.FakeBattleLobbyRepository
import com.example.nightwatchprotocol.data.social.FakeBattleRepository
import com.example.nightwatchprotocol.data.social.FakeFighterRepository
import com.example.nightwatchprotocol.data.social.FakeFriendsRepository
import com.example.nightwatchprotocol.data.social.FighterSummary
import com.example.nightwatchprotocol.data.social.Friend
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialResult
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
class BattleResultViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fighter = FakeFighterRepository(
        FighterSummary(xp = 150, level = 2, nextLevelXp = 300, wins = 6, losses = 2, draws = 0, battleStreak = 3, history = emptyList()),
    )
    private val friends = FakeFriendsRepository().apply { friends += Friend("mateo_ns", "NIGHT-9H4M5P", 0, false) }
    private val lobby = FakeBattleLobbyRepository()
    private val content = FakeLearningContentRepository()

    private fun viewModel(vararg states: SocialResult<BattleState>) = BattleResultViewModel(
        SavedStateHandle(mapOf(BattleResultViewModel.KEY_BATTLE_ID to BATTLE_ID)),
        FakeBattleRepository(states.toList()),
        fighter,
        friends,
        lobby,
        content,
    )

    private fun finished(
        outcome: BattleOutcome,
        reason: BattleEndReason = BattleEndReason.HpZero,
        mode: BattleMode = BattleMode.Quick,
        xp: Int = 50,
        weak: List<LearningTopic> = emptyList(),
        hpLeft: Int = 80,
    ) = SocialResult.Success(
        BattleScenarios.state(
            status = BattleStatus.Finished, turn = null, endReason = reason, myHp = hpLeft,
            result = BattleResultView(outcome, xp, hpLeft, xpTotal = 150, level = 2, weakTopics = weak),
        ).copy(mode = mode),
    )

    @Test
    fun victory_showsTheServersResult_andTheOwnTotals() = runTest {
        val vm = viewModel(finished(BattleOutcome.Win))

        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(EndKind.Victory, state.kind)
        assertEquals("mateo_ns", state.rival)
        assertEquals(80, state.hpLeft)
        assertEquals(50, state.xp)
        assertEquals(150, state.xpTotal)
        assertEquals(2, state.level)
        assertEquals(300, state.nextLevelXp)
        assertEquals(6, state.wins)
        assertEquals(2, state.losses)
        assertEquals(3, state.battleStreak)
        assertFalse(state.loading)
    }

    @Test
    fun defeat_listsTheWeakTopicsWithTheirTitles() = runTest {
        val vm = viewModel(finished(BattleOutcome.Loss, xp = 10, weak = listOf(LearningTopic.CONTRASENAS, LearningTopic.PHISHING), hpLeft = 0))

        vm.load()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(EndKind.Defeat, state.kind)
        assertEquals(listOf("Lección CONTRASENAS", "Lección PHISHING"), state.weakTopics)
        assertEquals(0, state.hpLeft)
        assertEquals(10, state.xp)
    }

    @Test
    fun aLossByDisconnection_keepsTheReason_forTheClearNotice() = runTest {
        val vm = viewModel(finished(BattleOutcome.Loss, reason = BattleEndReason.ForfeitDisconnect, xp = 0))

        vm.load()
        advanceUntilIdle()

        assertEquals(EndKind.Defeat, vm.uiState.value.kind)
        assertEquals(BattleEndReason.ForfeitDisconnect, vm.uiState.value.reason)
        assertEquals(0, vm.uiState.value.xp)
    }

    @Test
    fun aLossByLeaving_keepsTheAbandonReason() = runTest {
        val vm = viewModel(finished(BattleOutcome.Loss, reason = BattleEndReason.Abandon, xp = 0))

        vm.load()
        advanceUntilIdle()

        assertEquals(BattleEndReason.Abandon, vm.uiState.value.reason)
    }

    @Test
    fun aDraw_isItsOwnEnd() = runTest {
        val vm = viewModel(finished(BattleOutcome.Draw, reason = BattleEndReason.TurnLimit, xp = 25))

        vm.load()
        advanceUntilIdle()

        assertEquals(EndKind.Draw, vm.uiState.value.kind)
        assertEquals(25, vm.uiState.value.xp)
    }

    @Test
    fun aCancelledBattle_hasNoResultAndNoXp() = runTest {
        val cancelled = BattleScenarios.state(status = BattleStatus.Cancelled, turn = null, endReason = BattleEndReason.BothDisconnected)
        val vm = viewModel(SocialResult.Success(cancelled))

        vm.load()
        advanceUntilIdle()

        assertEquals(EndKind.Cancelled, vm.uiState.value.kind)
        assertEquals(0, vm.uiState.value.xp)
        assertTrue(vm.uiState.value.weakTopics.isEmpty())
    }

    @Test
    fun aBattleThatIsStillActive_isAnErrorToRetry() = runTest {
        val vm = viewModel(SocialResult.Success(BattleScenarios.state()))

        vm.load()
        advanceUntilIdle()

        assertEquals(SocialError.Unknown, vm.uiState.value.error)
        assertNull(vm.uiState.value.kind)
    }

    @Test
    fun aFailedRead_isAnError_andRetryLoadsIt() = runTest {
        val vm = viewModel(SocialResult.Failure(SocialError.Offline), finished(BattleOutcome.Win))

        vm.load()
        advanceUntilIdle()
        assertEquals(SocialError.Offline, vm.uiState.value.error)

        vm.load()
        advanceUntilIdle()
        assertEquals(EndKind.Victory, vm.uiState.value.kind)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun readingTwiceWithoutAnError_readsOnce() = runTest {
        val repository = FakeBattleRepository(listOf(finished(BattleOutcome.Win)))
        val vm = BattleResultViewModel(
            SavedStateHandle(mapOf(BattleResultViewModel.KEY_BATTLE_ID to BATTLE_ID)), repository, fighter, friends, lobby, content,
        )

        vm.load()
        vm.load()
        advanceUntilIdle()

        assertEquals(1, repository.syncCalls)
    }

    @Test
    fun ifTheTotalsCannotBeRead_theResultStillShows() = runTest {
        fighter.failure = SocialError.Offline
        val vm = viewModel(finished(BattleOutcome.Win))

        vm.load()
        advanceUntilIdle()

        assertEquals(EndKind.Victory, vm.uiState.value.kind)
        assertNull(vm.uiState.value.wins)
        assertEquals(150, vm.uiState.value.xpTotal)
    }

    // ---- rematch ----

    @Test
    fun rematch_isOfferedAfterAFriendBattle_whileTheyAreStillAFriend() = runTest {
        val vm = viewModel(finished(BattleOutcome.Loss, mode = BattleMode.Friend))

        vm.load()
        advanceUntilIdle()

        assertEquals("NIGHT-9H4M5P", vm.uiState.value.rematchFriendCode)
    }

    @Test
    fun rematch_goesAwayWhenTheRivalIsBlockedFromTheResult() = runTest {
        val vm = viewModel(finished(BattleOutcome.Loss, mode = BattleMode.Friend))
        vm.load()
        advanceUntilIdle()

        vm.onRivalBlocked()

        assertNull(vm.uiState.value.rematchFriendCode)
    }

    @Test
    fun rematch_isNotOfferedAfterAQuickMatch() = runTest {
        val vm = viewModel(finished(BattleOutcome.Win, mode = BattleMode.Quick))

        vm.load()
        advanceUntilIdle()

        assertNull(vm.uiState.value.rematchFriendCode)
    }

    @Test
    fun rematch_isNotOfferedIfTheRivalIsNotAFriendAnyMore() = runTest {
        friends.friends.clear()
        val vm = viewModel(finished(BattleOutcome.Win, mode = BattleMode.Friend))

        vm.load()
        advanceUntilIdle()

        assertNull(vm.uiState.value.rematchFriendCode)
    }

    @Test
    fun rematch_sendsAChallengeToTheSameFriend_andGoesToTheLobby() = runTest {
        val vm = viewModel(finished(BattleOutcome.Win, mode = BattleMode.Friend))
        vm.load()
        advanceUntilIdle()

        vm.events.test {
            vm.onRematch()
            vm.onRematch()
            advanceUntilIdle()

            assertEquals(BattleResultEvent.RematchSent, awaitItem())
        }
        assertEquals(listOf("NIGHT-9H4M5P"), lobby.sentChallengesTo)
    }

    @Test
    fun rematch_withARivalBusy_showsTheMessage_andStays() = runTest {
        lobby.sendChallengeResult = SocialResult.Failure(SocialError.RateLimited)
        val vm = viewModel(finished(BattleOutcome.Win, mode = BattleMode.Friend))
        vm.load()
        advanceUntilIdle()

        vm.events.test {
            vm.onRematch()
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(SocialError.RateLimited, vm.uiState.value.actionError)
        assertFalse(vm.uiState.value.rematching)
    }
}
