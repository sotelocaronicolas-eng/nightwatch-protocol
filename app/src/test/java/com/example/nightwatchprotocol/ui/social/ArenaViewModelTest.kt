package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.social.BATTLE_ID
import com.example.nightwatchprotocol.data.social.BattleEndReason
import com.example.nightwatchprotocol.data.social.BattleOutcome
import com.example.nightwatchprotocol.data.social.BattlePoller
import com.example.nightwatchprotocol.data.social.BattleResultView
import com.example.nightwatchprotocol.data.social.BattleScenarios
import com.example.nightwatchprotocol.data.social.BattleState
import com.example.nightwatchprotocol.data.social.BattleStatus
import com.example.nightwatchprotocol.data.social.BattleTurn
import com.example.nightwatchprotocol.data.social.FakeBattleRepository
import com.example.nightwatchprotocol.data.social.LastTurn
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialRejection
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.data.social.TurnOutcome
import com.example.nightwatchprotocol.data.social.TurnPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The arena with the stage-3 fakes: every state is what the server would send, and the view model only shows it. The poll runs
 * every 2.5 s of the coroutines clock; a test that polls ends with [ArenaViewModel.onStop].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArenaViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun TestScope.arena(repository: FakeBattleRepository) = ArenaViewModel(
        SavedStateHandle(mapOf(ArenaViewModel.KEY_BATTLE_ID to BATTLE_ID)),
        repository,
        BattlePoller(repository, intervalMillis = 2_500, dispatcher = StandardTestDispatcher(testScheduler)),
    )

    private fun mine(turn: Int = 3, version: Int = turn, lastTurn: LastTurn? = null, myHp: Int = 100, rivalHp: Int = 100) =
        BattleScenarios.state(turnNumber = turn, version = version, lastTurn = lastTurn, myHp = myHp, rivalHp = rivalHp)

    private fun rivals(turn: Int, version: Int = turn, lastTurn: LastTurn? = null, myHp: Int = 100, rivalHp: Int = 100, seen: Double = 0.5) =
        BattleScenarios.state(
            turnNumber = turn, version = version, lastTurn = lastTurn, myHp = myHp, rivalHp = rivalHp, rivalSecondsSinceSeen = seen,
            turn = BattleTurn(false, TurnPhase.Question, BattleScenarios.countdown(60.0)),
        )

    private fun finished(outcome: BattleOutcome = BattleOutcome.Win, reason: BattleEndReason = BattleEndReason.HpZero, version: Int = 20) =
        BattleScenarios.state(
            status = BattleStatus.Finished, turn = null, version = version, endReason = reason,
            result = BattleResultView(outcome, 50, 80, 150, 2, emptyList()),
        )

    private fun ok(state: BattleState) = SocialResult.Success(state)

    // ---- reading ----

    @Test
    fun theFirstStateOfMyTurn_isTheQuestion() = runTest {
        val repository = FakeBattleRepository(listOf(ok(mine())))
        val vm = arena(repository)

        vm.onStart()
        runCurrent()

        assertEquals(ArenaPhase.MyQuestion, vm.uiState.value.phase)
        assertEquals("¿Cuál de estas contraseñas es más segura?", vm.uiState.value.battle!!.question!!.text)
        vm.onStop()
    }

    @Test
    fun theRivalsTurn_hasNoQuestion_andIsTheWaitScreen() = runTest {
        val repository = FakeBattleRepository(listOf(ok(rivals(4))))
        val vm = arena(repository)

        vm.onStart()
        runCurrent()

        assertEquals(ArenaPhase.RivalTurn, vm.uiState.value.phase)
        assertNull(vm.uiState.value.battle!!.question)
        vm.onStop()
    }

    @Test
    fun theBattleIsSyncedEvery2500ms_onMyTurnToo_andNeverAfterOnStop() = runTest {
        val repository = FakeBattleRepository(listOf(ok(mine())))
        val vm = arena(repository)

        vm.onStart()
        runCurrent()
        advanceTimeBy(7_500)
        runCurrent()
        assertEquals(4, repository.syncCalls)

        vm.onStop()
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(4, repository.syncCalls)
    }

    @Test
    fun aSecondOnStart_doesNotStartASecondPoll() = runTest {
        val repository = FakeBattleRepository(listOf(ok(mine())))
        val vm = arena(repository)

        vm.onStart()
        vm.onStart()
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()

        assertEquals(2, repository.syncCalls)
        vm.onStop()
    }

    @Test
    fun anOldStateThatArrivesLate_doesNotUndoANewerOne() = runTest {
        val repository = FakeBattleRepository(listOf(ok(mine(turn = 5, version = 9)), ok(mine(turn = 3, version = 4))))
        val vm = arena(repository)

        vm.onStart()
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()

        assertEquals(5, vm.uiState.value.battle!!.turnNumber)
        vm.onStop()
    }

    @Test
    fun aFailedPoll_keepsTheBattle_showsTheError_andTheNextPollClearsIt() = runTest {
        val repository = FakeBattleRepository(listOf(ok(mine())))
        val vm = arena(repository)
        vm.onStart()
        runCurrent()

        repository.failure = SocialError.Offline
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(SocialError.Offline, vm.uiState.value.error)
        assertNotNull(vm.uiState.value.battle)

        repository.failure = null
        advanceTimeBy(2_500)
        runCurrent()
        assertNull(vm.uiState.value.error)
        vm.onStop()
    }

    // ---- answering ----

    @Test
    fun tappingAnOption_sendsItWithTheTurnItWasDrawnFor() = runTest {
        val repository = FakeBattleRepository(
            listOf(
                ok(mine(turn = 3)),
                ok(rivals(4, version = 4, lastTurn = LastTurn(3, byMe = true, outcome = TurnOutcome.Correct, damage = 20), rivalHp = 80)),
            ),
        )
        val vm = arena(repository)
        vm.onStart()
        runCurrent()

        vm.onOptionSelected('C')
        runCurrent()

        assertEquals(listOf("answer:3:C"), repository.actions)
        vm.onStop()
    }

    @Test
    fun aCorrectAnswer_showsTheFeedbackWithTheDamage_andTheTappedOption() = runTest {
        val repository = FakeBattleRepository(
            listOf(
                ok(mine(turn = 3)),
                ok(rivals(4, version = 4, lastTurn = LastTurn(3, true, TurnOutcome.Correct, 20), rivalHp = 80)),
            ),
        )
        val vm = arena(repository)
        vm.onStart()
        runCurrent()

        vm.onOptionSelected('C')
        runCurrent()

        val state = vm.uiState.value
        assertEquals(ArenaPhase.Feedback, state.phase)
        assertEquals(TurnFeedback(3, 'C', TurnOutcome.Correct, 20), state.feedback)
        assertEquals("¿Cuál de estas contraseñas es más segura?", state.answered!!.text)
        assertEquals(80, state.battle!!.rivalHp)
        assertFalse(state.submitting)
        vm.onStop()
    }

    @Test
    fun anIncorrectAnswer_showsNoDamage() = runTest {
        val repository = FakeBattleRepository(
            listOf(
                ok(mine(turn = 3)),
                ok(rivals(4, version = 4, lastTurn = LastTurn(3, true, TurnOutcome.Incorrect, 0))),
            ),
        )
        val vm = arena(repository)
        vm.onStart()
        runCurrent()

        vm.onOptionSelected('B')
        runCurrent()

        assertEquals(TurnFeedback(3, 'B', TurnOutcome.Incorrect, 0), vm.uiState.value.feedback)
        vm.onStop()
    }

    @Test
    fun theFeedbackStaysUntilNextTurn_thenTheWaitScreenShows() = runTest {
        val repository = FakeBattleRepository(
            listOf(
                ok(mine(turn = 3)),
                ok(rivals(4, version = 4, lastTurn = LastTurn(3, true, TurnOutcome.Incorrect, 0))),
                ok(rivals(4, version = 4, lastTurn = LastTurn(3, true, TurnOutcome.Incorrect, 0))),
            ),
        )
        val vm = arena(repository)
        vm.onStart()
        runCurrent()
        vm.onOptionSelected('B')
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()
        // The same last turn read again by the poll does not bring the feedback back or replace it.
        assertEquals(ArenaPhase.Feedback, vm.uiState.value.phase)

        vm.onNextTurn()

        assertEquals(ArenaPhase.RivalTurn, vm.uiState.value.phase)
        assertNull(vm.uiState.value.answered)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(ArenaPhase.RivalTurn, vm.uiState.value.phase)
        vm.onStop()
    }

    @Test
    fun aSecondTapWhileTheAnswerIsInFlight_isIgnored() = runTest {
        val repository = FakeBattleRepository(
            listOf(ok(mine(turn = 3)), ok(rivals(4, version = 4, lastTurn = LastTurn(3, true, TurnOutcome.Correct, 20)))),
        )
        val vm = arena(repository)
        vm.onStart()
        runCurrent()

        vm.onOptionSelected('C')
        vm.onOptionSelected('A')
        runCurrent()

        assertEquals(listOf("answer:3:C"), repository.actions)
        vm.onStop()
    }

    @Test
    fun anAnswerThatFailsOffline_leavesTheQuestionToTryAgain() = runTest {
        val repository = FakeBattleRepository(listOf(ok(mine(turn = 3))))
        val vm = arena(repository)
        vm.onStart()
        runCurrent()

        repository.failure = SocialError.Offline
        vm.onOptionSelected('C')
        runCurrent()
        repository.failure = null

        val state = vm.uiState.value
        assertEquals(SocialError.Offline, state.error)
        assertNull(state.chosen)
        assertFalse(state.submitting)
        assertEquals(ArenaPhase.MyQuestion, state.phase)
        vm.onStop()
    }

    @Test
    fun anAnswerForATurnThatPassed_isRejected_andTheNextPollShowsWhatHappened() = runTest {
        val repository = FakeBattleRepository(listOf(ok(mine(turn = 3))))
        val vm = arena(repository)
        vm.onStart()
        runCurrent()

        repository.failure = SocialError.Rejected(SocialRejection.StaleTurn)
        vm.onOptionSelected('C')
        runCurrent()
        assertEquals(SocialError.Rejected(SocialRejection.StaleTurn), vm.uiState.value.error)
        repository.failure = null
        repository.enqueue(
            ok(rivals(5, version = 6, lastTurn = LastTurn(4, byMe = true, outcome = TurnOutcome.Timeout, damage = 0))),
        )
        // The script answers the state it still had, then the new one.
        advanceTimeBy(5_000)
        runCurrent()

        assertNull(vm.uiState.value.error)
        assertEquals(TurnOutcome.Timeout, vm.uiState.value.feedback!!.outcome)
        assertNull(vm.uiState.value.feedback!!.chosen)
        vm.onStop()
    }

    // ---- the rival's moves and the timeout of my own turn ----

    @Test
    fun whenMyMinuteRunsOut_theFeedbackSaysTimeout_withoutAChosenOption() = runTest {
        val states = BattleScenarios.myTurnExpires()
        val repository = FakeBattleRepository(states)
        val vm = arena(repository)

        vm.onStart()
        runCurrent()
        assertEquals(ArenaPhase.MyQuestion, vm.uiState.value.phase)
        advanceTimeBy(2_500)
        runCurrent()

        assertEquals(ArenaPhase.Feedback, vm.uiState.value.phase)
        assertEquals(TurnOutcome.Timeout, vm.uiState.value.feedback!!.outcome)
        assertNull(vm.uiState.value.feedback!!.chosen)
        vm.onStop()
    }

    @Test
    fun whenTheRivalHitsMe_theirMoveIsShownAboveMyNextQuestion() = runTest {
        val repository = FakeBattleRepository(
            listOf(
                ok(rivals(4, version = 4)),
                ok(mine(turn = 5, version = 5, lastTurn = LastTurn(4, byMe = false, outcome = TurnOutcome.Correct, damage = 20), myHp = 80)),
            ),
        )
        val vm = arena(repository)
        vm.onStart()
        runCurrent()
        advanceTimeBy(2_500)
        runCurrent()

        assertEquals(ArenaPhase.MyQuestion, vm.uiState.value.phase)
        assertEquals(LastTurn(4, false, TurnOutcome.Correct, 20), vm.uiState.value.rivalMove)
        assertEquals(80, vm.uiState.value.battle!!.myHp)
        vm.onStop()
    }

    @Test
    fun theRivalsOldMove_alreadyOverWhenTheScreenOpens_isNotReplayed() = runTest {
        // The first state of this visit already carries the last turn of the rival (the person was away).
        val repository = FakeBattleRepository(
            listOf(ok(mine(turn = 5, lastTurn = LastTurn(4, byMe = false, outcome = TurnOutcome.Correct, damage = 20)))),
        )
        val vm = arena(repository)

        vm.onStart()
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()

        assertNull(vm.uiState.value.feedback)
        assertNull(vm.uiState.value.rivalMove)
        vm.onStop()
    }

    @Test
    fun aNewQuestionOfMineTwoTurnsLater_replacesTheFeedback() = runTest {
        val repository = FakeBattleRepository(
            listOf(
                ok(mine(turn = 3)),
                ok(rivals(4, version = 4, lastTurn = LastTurn(3, true, TurnOutcome.Correct, 20))),
                ok(mine(turn = 5, version = 5, lastTurn = LastTurn(4, false, TurnOutcome.Incorrect, 0))),
            ),
        )
        val vm = arena(repository)
        vm.onStart()
        runCurrent()
        vm.onOptionSelected('C')
        runCurrent()
        assertEquals(ArenaPhase.Feedback, vm.uiState.value.phase)

        advanceTimeBy(2_500)
        runCurrent()

        assertEquals(ArenaPhase.MyQuestion, vm.uiState.value.phase)
        vm.onStop()
    }

    @Test
    fun theRivalsCountdown_comesFromTheServersSeconds() = runTest {
        val repository = FakeBattleRepository(listOf(ok(rivals(4))))
        val vm = arena(repository)

        vm.onStart()
        runCurrent()

        val turn = vm.uiState.value.battle!!.turn!!
        assertFalse(turn.isMine)
        assertEquals(60, turn.timeLeft.remainingSeconds(turn.timeLeft.receivedAtNanos))
        vm.onStop()
    }

    // ---- the end ----

    @Test
    fun whenTheBattleIsOver_theResultOpens_once() = runTest {
        val repository = FakeBattleRepository(listOf(ok(mine()), ok(finished())))
        val vm = arena(repository)

        vm.events.test {
            vm.onStart()
            runCurrent()
            advanceTimeBy(2_500)
            runCurrent()

            assertEquals(ArenaEvent.Finished, awaitItem())
            advanceTimeBy(10_000)
            runCurrent()
            expectNoEvents()
        }
        assertEquals(ArenaPhase.Finished, vm.uiState.value.phase)
    }

    @Test
    fun comingBackAfterOverAMinute_theFirstReadSaysTheBattleWasLost() = runTest {
        val repository = FakeBattleRepository(BattleScenarios.lostByDisconnection())
        val vm = arena(repository)
        vm.onStart()
        runCurrent()
        vm.onStop()

        // The app was away for over a minute; the next read is the server's verdict.
        vm.events.test {
            vm.onStart()
            runCurrent()
            assertEquals(ArenaEvent.Finished, awaitItem())
        }
        assertEquals(BattleEndReason.ForfeitDisconnect, vm.uiState.value.battle!!.endReason)
        assertEquals(BattleOutcome.Loss, vm.uiState.value.battle!!.result!!.outcome)
    }

    @Test
    fun theRivalDisconnecting_endsWithMyWin() = runTest {
        val repository = FakeBattleRepository(BattleScenarios.wonBecauseRivalDisconnected())
        val vm = arena(repository)

        vm.events.test {
            vm.onStart()
            runCurrent()
            assertEquals(40.0, vm.uiState.value.battle!!.rivalSecondsSinceSeen, 0.001)
            advanceTimeBy(2_500)
            runCurrent()

            assertEquals(ArenaEvent.Finished, awaitItem())
        }
        assertEquals(BattleOutcome.Win, vm.uiState.value.battle!!.result!!.outcome)
    }

    // ---- leaving ----

    @Test
    fun leaving_asksFirst_andOnlyThenAbandons() = runTest {
        val repository = FakeBattleRepository(listOf(ok(mine()), ok(finished(BattleOutcome.Loss, BattleEndReason.Abandon))))
        val vm = arena(repository)
        vm.onStart()
        runCurrent()

        vm.onLeaveClicked()
        assertTrue(vm.uiState.value.confirmingLeave)
        assertTrue(repository.actions.isEmpty())

        vm.events.test {
            vm.onLeaveConfirmed()
            runCurrent()

            assertEquals(listOf("abandon"), repository.actions)
            assertEquals(ArenaEvent.Finished, awaitItem())
        }
        vm.onStop()
    }

    @Test
    fun keepPlaying_abandonsNothing() = runTest {
        val repository = FakeBattleRepository(listOf(ok(mine())))
        val vm = arena(repository)
        vm.onStart()
        runCurrent()

        vm.onLeaveClicked()
        vm.onLeaveDismissed()
        runCurrent()

        assertFalse(vm.uiState.value.confirmingLeave)
        assertTrue(repository.actions.isEmpty())
        vm.onStop()
    }

    @Test
    fun aFailedAbandon_staysInTheBattle_withTheError() = runTest {
        val repository = FakeBattleRepository(listOf(ok(mine())))
        val vm = arena(repository)
        vm.onStart()
        runCurrent()
        repository.failure = SocialError.Offline

        vm.onLeaveClicked()
        vm.onLeaveConfirmed()
        runCurrent()

        assertEquals(SocialError.Offline, vm.uiState.value.error)
        assertFalse(vm.uiState.value.leaving)
        assertEquals(BattleStatus.Active, vm.uiState.value.battle!!.status)
        repository.failure = null
        vm.onStop()
    }
}
