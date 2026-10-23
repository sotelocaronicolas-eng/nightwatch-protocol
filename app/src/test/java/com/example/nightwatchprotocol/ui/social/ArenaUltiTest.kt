package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.SavedStateHandle
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.social.BATTLE_ID
import com.example.nightwatchprotocol.data.social.BattlePoller
import com.example.nightwatchprotocol.data.social.BattleScenarios
import com.example.nightwatchprotocol.data.social.BattleState
import com.example.nightwatchprotocol.data.social.BattleTurn
import com.example.nightwatchprotocol.data.social.FakeBattleRepository
import com.example.nightwatchprotocol.data.social.LastTurn
import com.example.nightwatchprotocol.data.social.LastUlti
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.data.social.TurnOutcome
import com.example.nightwatchprotocol.data.social.TurnPhase
import com.example.nightwatchprotocol.data.social.UltiAttemptResult
import com.example.nightwatchprotocol.data.social.UltiRole
import com.example.nightwatchprotocol.data.social.UltiView
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The ulti of the arena: the choice, the terminal challenge (attacker and defender) and its result. Every number is the server's. */
@OptIn(ExperimentalCoroutinesApi::class)
class ArenaUltiTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun TestScope.arena(repository: FakeBattleRepository) = ArenaViewModel(
        SavedStateHandle(mapOf(ArenaViewModel.KEY_BATTLE_ID to BATTLE_ID)),
        repository,
        BattlePoller(repository, intervalMillis = 2_500, dispatcher = StandardTestDispatcher(testScheduler)),
    )

    private fun ok(state: BattleState) = SocialResult.Success(state)

    private fun chargedQuestion(turn: Int = 9, version: Int = turn) = BattleScenarios.state(turnNumber = turn, version = version, myUltiCharged = true)

    private fun ultiState(
        role: UltiRole,
        attempts: Int = 3,
        mine: UltiAttemptResult = UltiAttemptResult.Pending,
        turn: Int = 10,
        version: Int = turn,
    ) = BattleScenarios.state(
        turnNumber = turn, version = version, myUltiCharged = role == UltiRole.Attacker,
        turn = BattleTurn(isMine = role == UltiRole.Attacker, phase = TurnPhase.Ulti, timeLeft = BattleScenarios.countdown(118.0)),
        ulti = UltiView(role, "Bloquear el SSH expuesto", attempts, mine),
    )

    private suspend fun TestScope.startAt(vm: ArenaViewModel) {
        vm.onStart()
        runCurrent()
    }

    // ---- the choice ----

    @Test
    fun withTheUltiCharged_myQuestionTurnStartsWithTheChoice() = runTest {
        val repository = FakeBattleRepository(listOf(ok(chargedQuestion())))
        val vm = arena(repository)

        startAt(vm)

        assertEquals(ArenaPhase.UltiChoice, vm.uiState.value.phase)
        vm.onStop()
    }

    @Test
    fun saveForLater_showsTheQuestion_sendsNothing_andKeepsTheUltiCharged() = runTest {
        val repository = FakeBattleRepository(listOf(ok(chargedQuestion())))
        val vm = arena(repository)
        startAt(vm)

        vm.onSaveUlti()

        assertEquals(ArenaPhase.MyQuestion, vm.uiState.value.phase)
        assertTrue(repository.actions.isEmpty())
        assertTrue(vm.uiState.value.battle!!.myUltiCharged)
        // The poll that reads the same turn does not bring the choice back.
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(ArenaPhase.MyQuestion, vm.uiState.value.phase)
        vm.onStop()
    }

    @Test
    fun theChoiceComesBackOnTheNextTurnWhileTheUltiIsStillCharged() = runTest {
        val repository = FakeBattleRepository(listOf(ok(chargedQuestion(turn = 9)), ok(chargedQuestion(turn = 11, version = 11))))
        val vm = arena(repository)
        startAt(vm)
        vm.onSaveUlti()

        advanceTimeBy(2_500)
        runCurrent()

        assertEquals(ArenaPhase.UltiChoice, vm.uiState.value.phase)
        vm.onStop()
    }

    @Test
    fun useUltiNow_callsTheServer_andTheTurnBecomesTheChallenge() = runTest {
        val repository = FakeBattleRepository(listOf(ok(chargedQuestion()), ok(ultiState(UltiRole.Attacker, version = 10))))
        val vm = arena(repository)
        startAt(vm)

        vm.onUseUlti()
        runCurrent()

        assertEquals(listOf("use_ulti"), repository.actions)
        assertEquals(ArenaPhase.Ulti, vm.uiState.value.phase)
        vm.onStop()
    }

    @Test
    fun aSecondTapOnUseUlti_isIgnored() = runTest {
        val repository = FakeBattleRepository(listOf(ok(chargedQuestion()), ok(ultiState(UltiRole.Attacker, version = 10))))
        val vm = arena(repository)
        startAt(vm)

        vm.onUseUlti()
        vm.onUseUlti()
        runCurrent()

        assertEquals(listOf("use_ulti"), repository.actions)
        vm.onStop()
    }

    @Test
    fun aFailedUseUlti_staysOnTheChoice_withTheError() = runTest {
        val repository = FakeBattleRepository(listOf(ok(chargedQuestion())))
        val vm = arena(repository)
        startAt(vm)
        repository.failure = SocialError.Offline

        vm.onUseUlti()
        runCurrent()
        repository.failure = null

        assertEquals(SocialError.Offline, vm.uiState.value.error)
        assertEquals(ArenaPhase.UltiChoice, vm.uiState.value.phase)
        assertFalse(vm.uiState.value.submitting)
        vm.onStop()
    }

    // ---- the challenge ----

    @Test
    fun theChallengeShowsTheObjectiveAndTheAttempts_butTheStateHasNoCommandToShow() = runTest {
        val repository = FakeBattleRepository(listOf(ok(ultiState(UltiRole.Attacker))))
        val vm = arena(repository)

        startAt(vm)

        val ulti = vm.uiState.value.battle!!.ulti!!
        assertEquals("Bloquear el SSH expuesto", ulti.objective)
        assertEquals(3, ulti.attemptsLeft)
        // UltiView has no command field at all (decision O10): the player types it from memory.
        assertTrue(UltiView::class.java.declaredFields.none { it.name.contains("command", ignoreCase = true) })
        vm.onStop()
    }

    @Test
    fun theBattleKeepsBeingReadEvery2500ms_duringTheUlti() = runTest {
        val repository = FakeBattleRepository(listOf(ok(ultiState(UltiRole.Attacker))))
        val vm = arena(repository)
        startAt(vm)

        advanceTimeBy(7_500)
        runCurrent()

        assertEquals(4, repository.syncCalls)
        vm.onStop()
    }

    @Test
    fun sendingACommand_sendsItTrimmed_andClearsTheField() = runTest {
        val repository = FakeBattleRepository(listOf(ok(ultiState(UltiRole.Attacker)), ok(ultiState(UltiRole.Attacker, mine = UltiAttemptResult.Solved, version = 11))))
        val vm = arena(repository)
        startAt(vm)

        vm.onCommandChanged("  ufw deny from 198.51.100.9  ")
        vm.onSubmitCommand()
        runCurrent()

        assertEquals(listOf("ulti_submit:ufw deny from 198.51.100.9"), repository.actions)
        assertEquals("", vm.uiState.value.command)
        assertFalse(vm.uiState.value.wrongCommand)
        vm.onStop()
    }

    @Test
    fun aWrongCommand_usesAnAttempt_andTheChallengeGoesOn() = runTest {
        val repository = FakeBattleRepository(
            listOf(ok(ultiState(UltiRole.Attacker)), ok(ultiState(UltiRole.Attacker, attempts = 2, version = 11))),
        )
        val vm = arena(repository)
        startAt(vm)

        vm.onCommandChanged("ufw allow all")
        vm.onSubmitCommand()
        runCurrent()

        assertTrue(vm.uiState.value.wrongCommand)
        assertEquals(2, vm.uiState.value.battle!!.ulti!!.attemptsLeft)
        assertEquals(ArenaPhase.Ulti, vm.uiState.value.phase)

        vm.onCommandChanged("x")
        assertFalse(vm.uiState.value.wrongCommand)
        vm.onStop()
    }

    @Test
    fun aBlankCommand_isNotSent() = runTest {
        val repository = FakeBattleRepository(listOf(ok(ultiState(UltiRole.Attacker))))
        val vm = arena(repository)
        startAt(vm)

        vm.onCommandChanged("   ")
        vm.onSubmitCommand()
        runCurrent()

        assertTrue(repository.actions.isEmpty())
        vm.onStop()
    }

    @Test
    fun aSecondSendWhileTheFirstIsInFlight_isIgnored() = runTest {
        val repository = FakeBattleRepository(listOf(ok(ultiState(UltiRole.Attacker)), ok(ultiState(UltiRole.Attacker, attempts = 2, version = 11))))
        val vm = arena(repository)
        startAt(vm)

        vm.onCommandChanged("ufw deny")
        vm.onSubmitCommand()
        vm.onSubmitCommand()
        runCurrent()

        assertEquals(1, repository.actions.size)
        vm.onStop()
    }

    @Test
    fun withTheChallengeSolvedOrTheAttemptsUsedUp_nothingMoreIsSent() = runTest {
        val solved = FakeBattleRepository(listOf(ok(ultiState(UltiRole.Attacker, mine = UltiAttemptResult.Solved))))
        val vmSolved = arena(solved)
        startAt(vmSolved)
        vmSolved.onCommandChanged("ufw deny")
        vmSolved.onSubmitCommand()
        runCurrent()
        assertTrue(solved.actions.isEmpty())
        vmSolved.onStop()

        val failed = FakeBattleRepository(listOf(ok(ultiState(UltiRole.Attacker, attempts = 0, mine = UltiAttemptResult.Failed))))
        val vmFailed = arena(failed)
        startAt(vmFailed)
        vmFailed.onCommandChanged("ufw deny")
        vmFailed.onSubmitCommand()
        runCurrent()
        assertTrue(failed.actions.isEmpty())
        vmFailed.onStop()
    }

    @Test
    fun anOfflineSend_keepsTheCommandToTryAgain() = runTest {
        val repository = FakeBattleRepository(listOf(ok(ultiState(UltiRole.Attacker))))
        val vm = arena(repository)
        startAt(vm)
        vm.onCommandChanged("ufw deny")
        repository.failure = SocialError.Offline

        vm.onSubmitCommand()
        runCurrent()
        repository.failure = null

        assertEquals("ufw deny", vm.uiState.value.command)
        assertEquals(SocialError.Offline, vm.uiState.value.error)
        assertFalse(vm.uiState.value.submitting)
        vm.onStop()
    }

    @Test
    fun theDefender_getsTheirOwnChallenge_andCanSendACommand() = runTest {
        val repository = FakeBattleRepository(
            listOf(ok(ultiState(UltiRole.Defender)), ok(ultiState(UltiRole.Defender, mine = UltiAttemptResult.Solved, version = 11))),
        )
        val vm = arena(repository)
        startAt(vm)
        assertEquals(ArenaPhase.Ulti, vm.uiState.value.phase)
        assertEquals(UltiRole.Defender, vm.uiState.value.battle!!.ulti!!.role)

        vm.onCommandChanged("fail2ban-client status")
        vm.onSubmitCommand()
        runCurrent()

        assertEquals(listOf("ulti_submit:fail2ban-client status"), repository.actions)
        assertEquals(UltiAttemptResult.Solved, vm.uiState.value.battle!!.ulti!!.myResult)
        vm.onStop()
    }

    @Test
    fun leavingTheChallengeFieldAfterTheUltiEnds_clearsWhatWasTyped() = runTest {
        val repository = FakeBattleRepository(
            listOf(ok(ultiState(UltiRole.Attacker)), BattleScenarios.ultiWithShield()[1]),
        )
        val vm = arena(repository)
        startAt(vm)
        vm.onCommandChanged("ufw deny")

        advanceTimeBy(2_500)
        runCurrent()

        assertEquals("", vm.uiState.value.command)
        vm.onStop()
    }

    // ---- the result ----

    @Test
    fun attackerSuccessWithTheDefendersShield_showsTheServersDamage() = runTest {
        val states = BattleScenarios.ultiWithShield()
        val repository = FakeBattleRepository(states)
        val vm = arena(repository)
        startAt(vm)
        assertEquals(ArenaPhase.Ulti, vm.uiState.value.phase)

        advanceTimeBy(2_500)
        runCurrent()

        val result = vm.uiState.value.ultiResult!!
        assertEquals(ArenaPhase.UltiResult, vm.uiState.value.phase)
        assertTrue(result.byMe)
        assertEquals(23, result.damage)
        assertTrue(result.shield)
        assertTrue(result.attackerSolved)
        assertEquals(17, vm.uiState.value.battle!!.rivalHp)
        assertNull(vm.uiState.value.feedback)

        vm.onContinueUlti()
        assertEquals(ArenaPhase.RivalTurn, vm.uiState.value.phase)
        vm.onStop()
    }

    @Test
    fun attackerFailure_isZeroDamage() = runTest {
        val after = BattleScenarios.state(
            turnNumber = 11, version = 11,
            turn = BattleTurn(false, TurnPhase.Question, BattleScenarios.countdown(60.0)),
            lastTurn = LastTurn(10, byMe = true, outcome = TurnOutcome.Ulti, damage = 0),
            lastUlti = LastUlti(shield = false, attackerResult = UltiAttemptResult.Failed, defenderResult = UltiAttemptResult.Pending),
        )
        val repository = FakeBattleRepository(listOf(ok(ultiState(UltiRole.Attacker)), ok(after)))
        val vm = arena(repository)
        startAt(vm)

        advanceTimeBy(2_500)
        runCurrent()

        val result = vm.uiState.value.ultiResult!!
        assertEquals(0, result.damage)
        assertFalse(result.attackerSolved)
        assertFalse(vm.uiState.value.battle!!.myUltiCharged)
        vm.onStop()
    }

    @Test
    fun defenderWithTheShield_seesTheReducedDamage_whileTheirOwnQuestionAlreadyRuns() = runTest {
        val after = BattleScenarios.state(
            turnNumber = 11, version = 11, myHp = 77,
            lastTurn = LastTurn(10, byMe = false, outcome = TurnOutcome.Ulti, damage = 23),
            lastUlti = LastUlti(shield = true, attackerResult = UltiAttemptResult.Solved, defenderResult = UltiAttemptResult.Solved),
        )
        val repository = FakeBattleRepository(listOf(ok(ultiState(UltiRole.Defender)), ok(after)))
        val vm = arena(repository)
        startAt(vm)

        advanceTimeBy(2_500)
        runCurrent()

        val result = vm.uiState.value.ultiResult!!
        assertFalse(result.byMe)
        assertEquals(23, result.damage)
        assertTrue(result.shield)
        assertEquals(77, vm.uiState.value.battle!!.myHp)
        assertEquals(ArenaPhase.UltiResult, vm.uiState.value.phase)

        vm.onContinueUlti()
        assertEquals(ArenaPhase.MyQuestion, vm.uiState.value.phase)
        vm.onStop()
    }

    @Test
    fun defenderWithoutTheShield_receivesTheFullDamage() = runTest {
        val after = BattleScenarios.state(
            turnNumber = 11, version = 11, myHp = 55,
            lastTurn = LastTurn(10, byMe = false, outcome = TurnOutcome.Ulti, damage = 45),
            lastUlti = LastUlti(shield = false, attackerResult = UltiAttemptResult.Solved, defenderResult = UltiAttemptResult.Failed),
        )
        val repository = FakeBattleRepository(listOf(ok(ultiState(UltiRole.Defender)), ok(after)))
        val vm = arena(repository)
        startAt(vm)

        advanceTimeBy(2_500)
        runCurrent()

        val result = vm.uiState.value.ultiResult!!
        assertEquals(45, result.damage)
        assertFalse(result.shield)
        assertFalse(result.defenderSolved)
        vm.onStop()
    }

    @Test
    fun anUltiThatEndedBeforeTheScreenOpened_isNotReplayed() = runTest {
        val after = BattleScenarios.state(
            turnNumber = 11,
            lastTurn = LastTurn(10, byMe = false, outcome = TurnOutcome.Ulti, damage = 45),
            lastUlti = LastUlti(shield = false, attackerResult = UltiAttemptResult.Solved, defenderResult = UltiAttemptResult.Failed),
        )
        val repository = FakeBattleRepository(listOf(ok(after)))
        val vm = arena(repository)

        startAt(vm)

        assertNull(vm.uiState.value.ultiResult)
        assertEquals(ArenaPhase.MyQuestion, vm.uiState.value.phase)
        vm.onStop()
    }

    @Test
    fun theResultOfMyUlti_isReplacedByMyNextQuestion_twoTurnsLater() = runTest {
        val resolved = BattleScenarios.ultiWithShield()[1]
        val next = BattleScenarios.state(
            turnNumber = 12, version = 12,
            lastTurn = LastTurn(11, byMe = false, outcome = TurnOutcome.Incorrect, damage = 0),
        )
        val repository = FakeBattleRepository(listOf(ok(ultiState(UltiRole.Attacker)), resolved, ok(next)))
        val vm = arena(repository)
        startAt(vm)
        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(ArenaPhase.UltiResult, vm.uiState.value.phase)

        advanceTimeBy(2_500)
        runCurrent()

        assertEquals(ArenaPhase.MyQuestion, vm.uiState.value.phase)
        vm.onStop()
    }
}
