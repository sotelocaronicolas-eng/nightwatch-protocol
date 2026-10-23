package com.example.nightwatchprotocol.data.social

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The polling rule of 0008: `battle_sync` every 2 to 3 seconds while a battle is open, also on the person's own turn and during
 * the ulti, because over 60 seconds without a call the server gives the battle as lost. Time is the coroutines test clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SocialPollingTest {

    private fun TestScope.dispatcher() = StandardTestDispatcher(testScheduler)

    /** For flows that never end by themselves: it runs in the background and is cancelled when the test ends. */
    private fun <T> TestScope.collect(flow: Flow<T>, into: MutableList<T>) = backgroundScope.launch { flow.toList(into) }

    /** For flows that must end by themselves: the test waits for them (`advanceUntilIdle` does not run the background scope). */
    private fun <T> TestScope.collectFinite(flow: Flow<T>, into: MutableList<T>) = launch { flow.toList(into) }

    private val open = BattleScenarios.state()
    private val finished = BattleScenarios.state(
        status = BattleStatus.Finished, turn = null,
        result = BattleResultView(BattleOutcome.Win, 50, 100, 50, 1, emptyList()),
    )

    // ---- the battle ----

    @Test
    fun battle_isSyncedAtOnce_thenEveryInterval() = runTest {
        val repository = FakeBattleRepository(listOf(SocialResult.Success(open)))
        val poller = BattlePoller(repository, intervalMillis = 2_500, dispatcher = dispatcher())

        collect(poller.observe(BATTLE_ID), mutableListOf())
        runCurrent()
        assertEquals(1, repository.syncCalls)
        advanceTimeBy(2_499)
        runCurrent()
        assertEquals(1, repository.syncCalls)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, repository.syncCalls)
        advanceTimeBy(7_500)
        runCurrent()
        assertEquals(5, repository.syncCalls)
    }

    @Test
    fun battle_neverLeavesMoreThanThreeSecondsBetweenCalls_inAWholeMinuteAndMore() = runTest {
        val repository = FakeBattleRepository(listOf(SocialResult.Success(open)))
        val times = mutableListOf<Long>()
        repository.onSync = { times += testScheduler.currentTime }
        val poller = BattlePoller(repository, dispatcher = dispatcher())

        collect(poller.observe(BATTLE_ID), mutableListOf())
        advanceTimeBy(125_000)
        runCurrent()

        assertTrue("faltan llamadas: ${times.size}", times.size >= 50)
        times.zipWithNext { a, b -> assertTrue("hueco de ${b - a} ms", b - a <= MAX_BATTLE_POLL_INTERVAL_MILLIS) }
        // In every minute there are at least 20 calls: far from the 60 seconds after which the server gives the battle as lost.
        assertTrue(times.count { it < 60_000 } >= 20)
    }

    @Test
    fun battle_keepsCallingOnTheOwnTurn_andDuringTheUlti() = runTest {
        val ownTurn = SocialResult.Success(BattleScenarios.state(turn = BattleTurn(true, TurnPhase.Question, BattleScenarios.countdown(55.0))))
        val ulti = SocialResult.Success(
            BattleScenarios.state(
                turn = BattleTurn(true, TurnPhase.Ulti, BattleScenarios.countdown(110.0)),
                ulti = UltiView(UltiRole.Attacker, "Bloquear el SSH expuesto", 3, UltiAttemptResult.Pending),
            ),
        )
        val repository = FakeBattleRepository(listOf(ownTurn, ownTurn, ulti))
        val poller = BattlePoller(repository, intervalMillis = 2_500, dispatcher = dispatcher())
        val seen = mutableListOf<SocialResult<BattleState>>()

        collect(poller.observe(BATTLE_ID), seen)
        advanceTimeBy(30_000)
        runCurrent()

        // 13 calls in 30 s (at 0, 2.5 s, ... 30 s): the heartbeat does not stop on the own turn nor in the ulti.
        assertEquals(13, repository.syncCalls)
        assertEquals(13, seen.size)
        assertTrue((seen.last() as SocialResult.Success).value.ulti != null)
    }

    @Test
    fun battle_endsAfterEmittingTheFinalState() = runTest {
        val repository = FakeBattleRepository(listOf(SocialResult.Success(open), SocialResult.Success(open), SocialResult.Success(finished)))
        val poller = BattlePoller(repository, dispatcher = dispatcher())
        val seen = mutableListOf<SocialResult<BattleState>>()

        val job = collectFinite(poller.observe(BATTLE_ID), seen)
        advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertEquals(3, seen.size)
        assertFalse((seen.last() as SocialResult.Success).value.isOpen)
        assertEquals(3, repository.syncCalls)
    }

    @Test
    fun battle_aFailedCallIsRetried_becauseTheServerWaitsSixtySeconds() = runTest {
        val repository = FakeBattleRepository(
            listOf(SocialResult.Failure(SocialError.Offline), SocialResult.Failure(SocialError.RateLimited), SocialResult.Success(open), SocialResult.Success(finished)),
        )
        val poller = BattlePoller(repository, dispatcher = dispatcher())
        val seen = mutableListOf<SocialResult<BattleState>>()

        collectFinite(poller.observe(BATTLE_ID), seen)
        advanceUntilIdle()

        assertEquals(listOf(true, true, false, false), seen.map { it is SocialResult.Failure })
        assertEquals(4, repository.syncCalls)
    }

    @Test
    fun battle_stopsWhenAskingAgainIsPointless() = runTest {
        listOf(SocialError.NoSession, SocialError.NotEnabled, SocialError.Rejected(SocialRejection.NotFound)).forEach { error ->
            val repository = FakeBattleRepository(listOf(SocialResult.Failure(error)))
            val seen = mutableListOf<SocialResult<BattleState>>()

            val job = collectFinite(BattlePoller(repository, dispatcher = dispatcher()).observe(BATTLE_ID), seen)
            advanceUntilIdle()

            assertTrue("$error no detuvo el sondeo", job.isCompleted)
            assertEquals(1, repository.syncCalls)
            assertEquals(listOf(SocialResult.Failure(error)), seen)
        }
    }

    @Test
    fun battle_stopsCallingWhenItsCollectorIsCancelled() = runTest {
        val repository = FakeBattleRepository(listOf(SocialResult.Success(open)))
        val poller = BattlePoller(repository, dispatcher = dispatcher())

        val job = collect(poller.observe(BATTLE_ID), mutableListOf())
        advanceTimeBy(5_000)
        val before = repository.syncCalls
        job.cancel()
        advanceTimeBy(60_000)

        assertTrue(before >= 2)
        assertEquals(before, repository.syncCalls)
    }

    @Test
    fun battle_theIntervalIsInjectable_butNeverAboveThreeSeconds() = runTest {
        val repository = FakeBattleRepository(listOf(SocialResult.Success(open)))
        val fast = BattlePoller(repository, intervalMillis = 100, dispatcher = dispatcher())

        collect(fast.observe(BATTLE_ID), mutableListOf())
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(11, repository.syncCalls)

        BattlePoller(repository, intervalMillis = MAX_BATTLE_POLL_INTERVAL_MILLIS)
        assertThrows(IllegalArgumentException::class.java) { BattlePoller(repository, intervalMillis = 3_001) }
        assertThrows(IllegalArgumentException::class.java) { BattlePoller(repository, intervalMillis = 60_000) }
        assertThrows(IllegalArgumentException::class.java) { BattlePoller(repository, intervalMillis = 0) }
    }

    @Test
    fun battle_theDefaultIntervalIsInsideTwoToThreeSeconds() {
        assertTrue(BATTLE_POLL_INTERVAL_MILLIS in 2_000..3_000)
        assertTrue(LOBBY_POLL_INTERVAL_MILLIS in 2_000..3_000)
    }

    // ---- scenarios of the fake: what the screens will have to handle ----

    @Test
    fun scenario_aRivalThatDoesNotAnswer_endsInATimeoutAndMyTurn() = runTest {
        val repository = FakeBattleRepository(BattleScenarios.rivalDoesNotAnswer())
        val seen = mutableListOf<SocialResult<BattleState>>()

        collect(BattlePoller(repository, dispatcher = dispatcher()).observe(BATTLE_ID), seen)
        advanceTimeBy(10_000)
        runCurrent()

        val states = seen.map { (it as SocialResult.Success).value }
        assertFalse(states[0].turn!!.isMine)
        assertEquals(TurnOutcome.Timeout, states.last().lastTurn!!.outcome)
        assertFalse(states.last().lastTurn!!.byMe)
        assertTrue(states.last().turn!!.isMine)
    }

    @Test
    fun scenario_lostByDisconnection_endsThePolling() = runTest {
        val repository = FakeBattleRepository(BattleScenarios.lostByDisconnection())
        val seen = mutableListOf<SocialResult<BattleState>>()

        val job = collectFinite(BattlePoller(repository, dispatcher = dispatcher()).observe(BATTLE_ID), seen)
        advanceUntilIdle()

        assertTrue(job.isCompleted)
        val last =(seen.last() as SocialResult.Success).value
        assertEquals(BattleEndReason.ForfeitDisconnect, last.endReason)
        assertEquals(BattleOutcome.Loss, last.result!!.outcome)
    }

    // ---- the search for a rival ----

    @Test
    fun quickMatch_joinsOnce_thenPollsEveryInterval_untilMatched() = runTest {
        val lobby = FakeBattleLobbyRepository()
        lobby.quickPollScript += listOf(
            SocialResult.Success(QuickMatchStatus.Waiting(2, false)),
            SocialResult.Success(QuickMatchStatus.Waiting(5, false)),
            SocialResult.Success(QuickMatchStatus.Matched(BATTLE_ID)),
        )
        val seen = mutableListOf<SocialResult<QuickMatchStatus>>()

        val job = collectFinite(QuickMatchPoller(lobby, intervalMillis = 2_500, dispatcher = dispatcher()).search(), seen)
        runCurrent()
        assertEquals(1, lobby.joinCalls)
        assertEquals(0, lobby.pollCalls)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(1, lobby.pollCalls)
        advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertEquals(1, lobby.joinCalls)
        assertEquals(3, lobby.pollCalls)
        assertEquals(SocialResult.Success(QuickMatchStatus.Matched(BATTLE_ID)), seen.last())
        assertEquals(0, lobby.cancelCalls)
    }

    @Test
    fun quickMatch_showsTheServersSuggestionToChallengeAFriend_andKeepsLooking() = runTest {
        val lobby = FakeBattleLobbyRepository()
        lobby.quickPollScript += SocialResult.Success(QuickMatchStatus.Waiting(61, suggestFriend = true))
        val seen = mutableListOf<SocialResult<QuickMatchStatus>>()

        val job = collect(QuickMatchPoller(lobby, dispatcher = dispatcher()).search(), seen)
        advanceTimeBy(20_000)
        runCurrent()

        assertFalse(job.isCompleted)
        assertTrue(lobby.pollCalls >= 8)
        assertEquals(QuickMatchStatus.Waiting(61, true), (seen.last() as SocialResult.Success).value)
    }

    @Test
    fun quickMatch_ifJoiningFails_thereIsNoPolling() = runTest {
        val lobby = FakeBattleLobbyRepository()
        lobby.quickJoinResult = SocialResult.Failure(SocialError.RateLimited)
        val seen = mutableListOf<SocialResult<QuickMatchStatus>>()

        val job = collectFinite(QuickMatchPoller(lobby, dispatcher = dispatcher()).search(), seen)
        advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertEquals(listOf<SocialResult<QuickMatchStatus>>(SocialResult.Failure(SocialError.RateLimited)), seen)
        assertEquals(0, lobby.pollCalls)
    }

    @Test
    fun quickMatch_ifAlreadyMatchedOrInABattle_itEndsAtOnce() = runTest {
        listOf(QuickMatchStatus.Matched(BATTLE_ID), QuickMatchStatus.InBattle(BATTLE_ID)).forEach { status ->
            val lobby = FakeBattleLobbyRepository()
            lobby.quickJoinResult = SocialResult.Success(status)

            val job = collectFinite(QuickMatchPoller(lobby, dispatcher = dispatcher()).search(), mutableListOf())
            advanceUntilIdle()

            assertTrue(job.isCompleted)
            assertEquals(0, lobby.pollCalls)
        }
    }

    @Test
    fun quickMatch_aFailedPollIsRetried_andALeftQueueEndsIt() = runTest {
        val lobby = FakeBattleLobbyRepository()
        lobby.quickPollScript += listOf(
            SocialResult.Failure(SocialError.Offline),
            SocialResult.Success(QuickMatchStatus.Waiting(8, false)),
            SocialResult.Success(QuickMatchStatus.Idle),
        )
        val seen = mutableListOf<SocialResult<QuickMatchStatus>>()

        val job = collectFinite(QuickMatchPoller(lobby, dispatcher = dispatcher()).search(), seen)
        advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertEquals(4, seen.size)
        assertEquals(SocialResult.Success(QuickMatchStatus.Idle), seen.last())
    }

    @Test
    fun quickMatch_theIntervalCannotPassThreeSeconds() {
        assertThrows(IllegalArgumentException::class.java) { QuickMatchPoller(FakeBattleLobbyRepository(), intervalMillis = 3_001) }
    }

    // ---- the challenges ----

    @Test
    fun challenges_areLookedAtEveryIntervalUntilCancelled() = runTest {
        val lobby = FakeBattleLobbyRepository()
        val seen = mutableListOf<SocialResult<ChallengesView>>()

        val job = collect(ChallengesPoller(lobby, intervalMillis = 7_500, dispatcher = dispatcher()).observe(), seen)
        advanceTimeBy(30_000)
        runCurrent()

        assertEquals(5, lobby.challengesCalls)
        job.cancel()
        advanceTimeBy(60_000)
        assertEquals(5, lobby.challengesCalls)
    }

    @Test
    fun challenges_stopWhenTheSessionIsGone_andTheIntervalHasALimit() = runTest {
        val lobby = FakeBattleLobbyRepository()
        lobby.failure = SocialError.NoSession

        val job = collectFinite(ChallengesPoller(lobby, dispatcher = dispatcher()).observe(), mutableListOf())
        advanceUntilIdle()

        assertTrue(job.isCompleted)
        assertEquals(1, lobby.challengesCalls)
        assertThrows(IllegalArgumentException::class.java) { ChallengesPoller(lobby, intervalMillis = 10_001) }
    }
}
