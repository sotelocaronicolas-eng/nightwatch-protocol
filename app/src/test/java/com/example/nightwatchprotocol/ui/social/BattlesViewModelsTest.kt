package com.example.nightwatchprotocol.ui.social

import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.social.BATTLE_ID
import com.example.nightwatchprotocol.data.social.ChallengeStatus
import com.example.nightwatchprotocol.data.social.ChallengesPoller
import com.example.nightwatchprotocol.data.social.ChallengesView
import com.example.nightwatchprotocol.data.social.FakeBattleLobbyRepository
import com.example.nightwatchprotocol.data.social.FakeFighterRepository
import com.example.nightwatchprotocol.data.social.FakeFriendsRepository
import com.example.nightwatchprotocol.data.social.FighterSummary
import com.example.nightwatchprotocol.data.social.Friend
import com.example.nightwatchprotocol.data.social.QuickMatchPoller
import com.example.nightwatchprotocol.data.social.QuickMatchStatus
import com.example.nightwatchprotocol.data.social.ReceivedChallenge
import com.example.nightwatchprotocol.data.social.SentChallenge
import com.example.nightwatchprotocol.data.social.ServerCountdown
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialRejection
import com.example.nightwatchprotocol.data.social.SocialResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BattlesViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val lobby = FakeBattleLobbyRepository()
    private val friends = FakeFriendsRepository().apply {
        friends += Friend("valentina", "NIGHT-VVVVVV", 12, true)
        friends += Friend("mateo", "NIGHT-9H4M5P", 0, false)
    }
    private val fighter = FakeFighterRepository(FighterSummary(120, 2, 200, wins = 5, losses = 2, draws = 0, battleStreak = 1, history = emptyList()))

    private fun TestScope.dispatcher() = StandardTestDispatcher(testScheduler)

    /** Outlives the view model, like in the app. */
    private fun TestScope.leaveScope() = CoroutineScope(SupervisorJob() + dispatcher())

    private fun TestScope.lobbyViewModel() = BattlesViewModel(
        lobbyRepository = lobby,
        friendsRepository = friends,
        fighterRepository = fighter,
        challengesPoller = ChallengesPoller(lobby, intervalMillis = 7_500, dispatcher = dispatcher()),
        leaveScope = leaveScope(),
    )

    private fun TestScope.searchViewModel() = QuickSearchViewModel(
        lobbyRepository = lobby,
        poller = QuickMatchPoller(lobby, intervalMillis = 2_500, dispatcher = dispatcher()),
        leaveScope = leaveScope(),
    )

    private fun received(name: String = "camila", code: String = "NIGHT-AAAAAA") =
        ReceivedChallenge(name, code, ServerCountdown(95.0, 0L))

    private fun waiting(status: ChallengeStatus = ChallengeStatus.Pending, battleId: String? = null) =
        SentChallenge("mateo", "NIGHT-9H4M5P", status, ServerCountdown(110.0, 0L), battleId)

    // ---- lobby ----

    @Test
    fun lobby_onStart_readsTheScore_theFriends_andTheChallenges() = runTest {
        lobby.challengesResult = SocialResult.Success(ChallengesView(null, listOf(received()), null))
        val vm = lobbyViewModel()

        vm.onStart()
        runCurrent()

        val state = vm.uiState.value
        assertEquals(5, state.wins)
        assertEquals(2, state.losses)
        assertEquals(2, state.friends!!.size)
        assertEquals(listOf("camila"), state.received.map { it.username })
        assertFalse(state.loading)
        vm.onStop()
    }

    @Test
    fun lobby_theChallengesAreReadEvery7500ms_whileTheScreenIsVisible_andNoMoreAfterOnStop() = runTest {
        val vm = lobbyViewModel()

        vm.onStart()
        runCurrent()
        assertEquals(1, lobby.challengesCalls)
        advanceTimeBy(7_500)
        runCurrent()
        assertEquals(2, lobby.challengesCalls)

        vm.onStop()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(2, lobby.challengesCalls)
    }

    @Test
    fun lobby_aSecondOnStart_doesNotStartASecondPoller() = runTest {
        val vm = lobbyViewModel()

        vm.onStart()
        vm.onStart()
        runCurrent()
        advanceTimeBy(7_500)
        runCurrent()

        assertEquals(2, lobby.challengesCalls)
        vm.onStop()
    }

    @Test
    fun lobby_aFailedPoll_keepsTheLastChallenges_andShowsTheError() = runTest {
        lobby.challengesResult = SocialResult.Success(ChallengesView(null, listOf(received()), null))
        val vm = lobbyViewModel()
        vm.onStart()
        runCurrent()

        lobby.failure = SocialError.Offline
        advanceTimeBy(7_500)
        runCurrent()

        assertEquals(1, vm.uiState.value.received.size)
        assertEquals(SocialError.Offline, vm.uiState.value.loadError)
        vm.onStop()
    }

    @Test
    fun lobby_acceptingAChallenge_opensTheBattle() = runTest {
        lobby.challengesResult = SocialResult.Success(ChallengesView(null, listOf(received()), null))
        val vm = lobbyViewModel()
        vm.onStart()
        runCurrent()

        vm.events.test {
            vm.onAccept("NIGHT-AAAAAA")
            runCurrent()

            assertEquals(BattlesEvent.OpenBattle(BATTLE_ID), awaitItem())
        }
        assertEquals(1, lobby.acceptCalls)
        vm.onStop()
    }

    @Test
    fun lobby_acceptingAChallengeThatExpired_showsTheMessage_andStaysInTheLobby() = runTest {
        lobby.acceptChallengeResult = SocialResult.Failure(SocialError.Rejected(SocialRejection.ChallengeGone))
        lobby.challengesResult = SocialResult.Success(ChallengesView(null, listOf(received()), null))
        val vm = lobbyViewModel()
        vm.onStart()
        runCurrent()

        vm.events.test {
            vm.onAccept("NIGHT-AAAAAA")
            runCurrent()
            expectNoEvents()
        }
        assertEquals(SocialError.Rejected(SocialRejection.ChallengeGone), vm.uiState.value.actionError)
        assertTrue(vm.uiState.value.busy.isEmpty())
        vm.onStop()
    }

    @Test
    fun lobby_aSecondTapOnAccept_isIgnored() = runTest {
        lobby.challengesResult = SocialResult.Success(ChallengesView(null, listOf(received()), null))
        val vm = lobbyViewModel()
        vm.onStart()
        runCurrent()

        vm.onAccept("NIGHT-AAAAAA")
        vm.onAccept("NIGHT-AAAAAA")
        runCurrent()

        assertEquals(1, lobby.acceptCalls)
        vm.onStop()
    }

    @Test
    fun lobby_declining_callsTheServer_andReadsAgain() = runTest {
        lobby.challengesResult = SocialResult.Success(ChallengesView(null, listOf(received()), null))
        val vm = lobbyViewModel()
        vm.onStart()
        runCurrent()
        val before = lobby.challengesCalls

        vm.onDecline("NIGHT-AAAAAA")
        runCurrent()

        assertEquals(1, lobby.declineCalls)
        assertEquals(before + 1, lobby.challengesCalls)
        vm.onStop()
    }

    @Test
    fun lobby_challengingAFriend_sendsItOnce_andShowsTheWait() = runTest {
        val vm = lobbyViewModel()
        vm.onStart()
        runCurrent()
        lobby.challengesResult = SocialResult.Success(ChallengesView(null, emptyList(), waiting()))

        vm.onChallenge("NIGHT-9H4M5P")
        runCurrent()

        assertEquals(listOf("NIGHT-9H4M5P"), lobby.sentChallengesTo)
        assertEquals("mateo", vm.uiState.value.waiting!!.username)
        vm.onStop()
    }

    @Test
    fun lobby_whileAChallengeIsWaiting_noOtherChallengeIsSent() = runTest {
        lobby.challengesResult = SocialResult.Success(ChallengesView(null, emptyList(), waiting()))
        val vm = lobbyViewModel()
        vm.onStart()
        runCurrent()

        vm.onChallenge("NIGHT-VVVVVV")
        runCurrent()

        assertTrue(lobby.sentChallengesTo.isEmpty())
        vm.onStop()
    }

    @Test
    fun lobby_aRateLimitedChallenge_showsItsMessage() = runTest {
        lobby.sendChallengeResult = SocialResult.Failure(SocialError.RateLimited)
        val vm = lobbyViewModel()
        vm.onStart()
        runCurrent()

        vm.onChallenge("NIGHT-VVVVVV")
        runCurrent()

        assertEquals(SocialError.RateLimited, vm.uiState.value.actionError)
        vm.onStop()
    }

    @Test
    fun lobby_cancellingTheWait_callsTheServer() = runTest {
        lobby.challengesResult = SocialResult.Success(ChallengesView(null, emptyList(), waiting()))
        val vm = lobbyViewModel()
        vm.onStart()
        runCurrent()

        lobby.challengesResult = SocialResult.Success(ChallengesView(null, emptyList(), null))
        vm.onCancelWaiting()
        runCurrent()

        assertEquals(1, lobby.cancelChallengeCalls)
        assertNull(vm.uiState.value.waiting)
        vm.onStop()
    }

    @Test
    fun lobby_leavingTheScreenWhileWaiting_cancelsTheChallenge() = runTest {
        lobby.challengesResult = SocialResult.Success(ChallengesView(null, emptyList(), waiting()))
        val vm = lobbyViewModel()
        vm.onStart()
        runCurrent()

        vm.onStop()
        vm.onLeft()
        runCurrent()

        assertEquals(1, lobby.cancelChallengeCalls)
    }

    @Test
    fun lobby_leavingTheScreenWithNothingWaiting_cancelsNothing() = runTest {
        val vm = lobbyViewModel()
        vm.onStart()
        runCurrent()

        vm.onStop()
        vm.onLeft()
        runCurrent()

        assertEquals(0, lobby.cancelChallengeCalls)
    }

    @Test
    fun lobby_whenTheFriendAccepts_theBattleOpens_onlyOnce() = runTest {
        lobby.challengesResult = SocialResult.Success(
            ChallengesView(BATTLE_ID, emptyList(), waiting(ChallengeStatus.Accepted, BATTLE_ID)),
        )
        val vm = lobbyViewModel()

        vm.events.test {
            vm.onStart()
            runCurrent()
            assertEquals(BattlesEvent.OpenBattle(BATTLE_ID), awaitItem())

            advanceTimeBy(15_000)
            runCurrent()
            expectNoEvents()
        }
        vm.onStop()
    }

    @Test
    fun lobby_aDeclinedChallenge_isNotWaitingAnyMore() = runTest {
        lobby.challengesResult = SocialResult.Success(ChallengesView(null, emptyList(), waiting(ChallengeStatus.Declined)))
        val vm = lobbyViewModel()
        vm.onStart()
        runCurrent()

        assertNull(vm.uiState.value.waiting)
        assertEquals(ChallengeStatus.Declined, vm.uiState.value.sent!!.status)
        vm.onStop()
    }

    // ---- quick search ----

    @Test
    fun search_joinsTheQueue_andShowsTheSearch() = runTest {
        val vm = searchViewModel()

        vm.onStart()
        runCurrent()

        assertEquals(1, lobby.joinCalls)
        assertTrue(vm.uiState.value.status is QuickSearchStatus.Searching)
        vm.onLeft()
    }

    @Test
    fun search_after60Seconds_theServerSuggestsAFriend_andTheSearchGoesOn() = runTest {
        lobby.quickPollScript += SocialResult.Success(QuickMatchStatus.Waiting(30, suggestFriend = false))
        lobby.quickPollScript += SocialResult.Success(QuickMatchStatus.Waiting(62, suggestFriend = true))
        val vm = searchViewModel()

        vm.onStart()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(QuickSearchStatus.Searching(62, suggestFriend = true), vm.uiState.value.status)
        advanceTimeBy(5_000)
        runCurrent()
        assertTrue(lobby.pollCalls >= 3)
        vm.onLeft()
    }

    @Test
    fun search_aMatch_opensTheBattleAtOnce_withoutLeavingTheQueue() = runTest {
        lobby.quickPollScript += SocialResult.Success(QuickMatchStatus.Matched(BATTLE_ID))
        val vm = searchViewModel()

        vm.events.test {
            vm.onStart()
            runCurrent()
            advanceTimeBy(2_500)
            runCurrent()

            assertEquals(QuickSearchEvent.OpenBattle(BATTLE_ID), awaitItem())
        }
        vm.onLeft()
        runCurrent()
        assertEquals(0, lobby.cancelCalls)
    }

    @Test
    fun search_beingInABattleAlready_opensIt() = runTest {
        lobby.quickJoinResult = SocialResult.Success(QuickMatchStatus.InBattle(BATTLE_ID))
        val vm = searchViewModel()

        vm.events.test {
            vm.onStart()
            runCurrent()

            assertEquals(QuickSearchEvent.OpenBattle(BATTLE_ID), awaitItem())
        }
    }

    @Test
    fun search_cancelling_leavesTheQueue_andGoesBack() = runTest {
        val vm = searchViewModel()
        vm.onStart()
        runCurrent()

        vm.events.test {
            vm.onCancel()
            runCurrent()

            assertEquals(QuickSearchEvent.Cancelled, awaitItem())
        }
        assertEquals(1, lobby.cancelCalls)
        val polls = lobby.pollCalls
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(polls, lobby.pollCalls)
    }

    @Test
    fun search_leavingTheScreen_leavesTheQueue_andStopsPolling() = runTest {
        val vm = searchViewModel()
        vm.onStart()
        runCurrent()

        vm.onLeft()
        runCurrent()
        val polls = lobby.pollCalls
        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(1, lobby.cancelCalls)
        assertEquals(polls, lobby.pollCalls)
    }

    @Test
    fun search_theAppGoingToTheBackground_leavesTheQueue_andOffersToSearchAgain() = runTest {
        val vm = searchViewModel()
        vm.onStart()
        runCurrent()

        vm.onStop()
        runCurrent()

        assertEquals(QuickSearchStatus.Stopped, vm.uiState.value.status)
        assertEquals(1, lobby.cancelCalls)

        vm.onStart()
        runCurrent()
        assertEquals(2, lobby.joinCalls)
        assertTrue(vm.uiState.value.status is QuickSearchStatus.Searching)
        vm.onLeft()
    }

    @Test
    fun search_aSecondOnStartWhileSearching_doesNotJoinTwice() = runTest {
        val vm = searchViewModel()

        vm.onStart()
        vm.onStart()
        runCurrent()

        assertEquals(1, lobby.joinCalls)
        vm.onLeft()
    }

    @Test
    fun search_aFailedJoin_isAnError_andLeavingDoesNotCancelAQueueItNeverEntered() = runTest {
        lobby.failure = SocialError.Offline
        val vm = searchViewModel()

        vm.onStart()
        advanceUntilIdle()

        assertEquals(QuickSearchStatus.Failed(SocialError.Offline), vm.uiState.value.status)
        vm.onLeft()
        advanceUntilIdle()
        assertEquals(0, lobby.cancelCalls)
    }
}
