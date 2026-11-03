package com.example.nightwatchprotocol.ui.social

import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.social.BATTLE_ID
import com.example.nightwatchprotocol.data.social.BattlePoller
import com.example.nightwatchprotocol.data.social.BattleScenarios
import com.example.nightwatchprotocol.data.social.ChallengeStatus
import com.example.nightwatchprotocol.data.social.ChallengesPoller
import com.example.nightwatchprotocol.data.social.ChallengesView
import com.example.nightwatchprotocol.data.social.FakeBattleLobbyRepository
import com.example.nightwatchprotocol.data.social.FakeBattleRepository
import com.example.nightwatchprotocol.data.social.FakeFighterRepository
import com.example.nightwatchprotocol.data.social.FakeFriendsRepository
import com.example.nightwatchprotocol.data.social.FighterSummary
import com.example.nightwatchprotocol.data.social.QuickMatchPoller
import com.example.nightwatchprotocol.data.social.SentChallenge
import com.example.nightwatchprotocol.data.social.ServerCountdown
import com.example.nightwatchprotocol.data.social.SocialResult
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * R-01 (Módulo 5, Etapa 7): changing the theme, the language or the font size recreates the Activity. The Activity goes through
 * `ON_STOP` (the screens call `onStop`) and a new one through `ON_START` (`onStart`), while the view model survives. These tests replay
 * that sequence with the fakes and check what the person would lose: the battle, a sent challenge or the place in the queue.
 * The real Activity is not here (it needs a device); the manual list is in `docs/superpowers/reports/etapa-7-verificacion.md`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConfigurationChangeTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun TestScope.dispatcher() = StandardTestDispatcher(testScheduler)

    // ---- the decision of LeaveScreenEffect ----

    @Test
    fun leaveEffect_whileTheActivityIsRecreated_doesNotCountAsLeaving() {
        assertFalse(leftForGood(true))
    }

    @Test
    fun leaveEffect_whenTheActivityIsNotRecreated_countsAsLeaving() {
        assertTrue(leftForGood(false))
    }

    @Test
    fun leaveEffect_withoutAnActivity_countsAsLeaving() {
        assertTrue(leftForGood(null))
    }

    // ---- arena: the heartbeat resumes and nothing is given up ----

    @Test
    fun arena_recreation_resumesTheSyncAndSendsNoAction() = runTest {
        val repository = FakeBattleRepository(listOf(SocialResult.Success(BattleScenarios.state(turnNumber = 3, version = 3))))
        val vm = ArenaViewModel(
            SavedStateHandle(mapOf(ArenaViewModel.KEY_BATTLE_ID to BATTLE_ID)),
            repository,
            BattlePoller(repository, intervalMillis = 2_500, dispatcher = dispatcher()),
        )
        vm.onStart()
        runCurrent()
        val before = repository.syncCalls

        // Recreation: old Activity stops, new one starts (a few milliseconds apart).
        vm.onStop()
        vm.onStart()
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue("the poll must keep asking after the recreation", repository.syncCalls > before)
        assertTrue("recreating must not play or give up anything: ${repository.actions}", repository.actions.isEmpty())
        vm.onStop()
    }

    // ---- lobby: a sent challenge survives the recreation; only leaving for good cancels it ----

    @Test
    fun lobby_recreation_doesNotCancelASentChallenge_butLeavingDoes() = runTest {
        val lobby = FakeBattleLobbyRepository()
        val sent = SentChallenge("mateo", "NIGHT-9H4M5P", ChallengeStatus.Pending, ServerCountdown(110.0, 0L), null)
        lobby.challengesResult = SocialResult.Success(ChallengesView(null, emptyList(), sent))
        val vm = BattlesViewModel(
            lobbyRepository = lobby,
            friendsRepository = FakeFriendsRepository(),
            fighterRepository = FakeFighterRepository(FighterSummary(0, 1, 100, wins = 0, losses = 0, draws = 0, battleStreak = 0, history = emptyList())),
            challengesPoller = ChallengesPoller(lobby, intervalMillis = 7_500, dispatcher = dispatcher()),
            leaveScope = CoroutineScope(SupervisorJob() + dispatcher()),
        )
        vm.onStart()
        runCurrent()
        assertTrue(vm.uiState.value.waiting != null)

        // Recreation: the screen calls onStop and onStart, but LeaveScreenEffect skips onLeft.
        vm.onStop()
        vm.onStart()
        runCurrent()

        assertEquals(0, lobby.cancelChallengeCalls)
        assertTrue(vm.uiState.value.waiting != null)

        vm.onLeft()
        runCurrent()
        assertEquals(1, lobby.cancelChallengeCalls)
        vm.onStop()
    }

    // ---- quick search: FINDING (reported, not fixed) ----

    /**
     * Documents today's behavior: `QuickSearchScreen` wires `ON_STOP` to `onStop`, which takes the person out of the queue, and the new
     * Activity joins again from zero. The search is not lost, but the waiting time restarts. A change of language is only possible from
     * the system settings while this screen is open, so it is reported and not corrected in this stage.
     */
    @Test
    fun quickSearch_recreation_leavesTheQueueAndJoinsAgain() = runTest {
        val lobby = FakeBattleLobbyRepository()
        val vm = QuickSearchViewModel(
            lobbyRepository = lobby,
            poller = QuickMatchPoller(lobby, intervalMillis = 2_500, dispatcher = dispatcher()),
            leaveScope = CoroutineScope(SupervisorJob() + dispatcher()),
        )
        vm.onStart()
        runCurrent()
        assertEquals(1, lobby.joinCalls)

        vm.onStop()
        vm.onStart()
        runCurrent()

        assertEquals("it leaves the queue once", 1, lobby.cancelCalls)
        assertEquals("and joins again", 2, lobby.joinCalls)
        vm.onLeft()
        runCurrent()
    }
}
