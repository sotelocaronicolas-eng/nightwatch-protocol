package com.example.nightwatchprotocol.data.social

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fakes answer like the server does (same rejections) and cover the scenarios the screens of the next stages will need. */
class SocialFakesTest {

    private fun <T> SocialResult<T>.value(): T = (this as SocialResult.Success).value

    private fun <T> SocialResult<T>.error(): SocialError = (this as SocialResult.Failure).error

    // ---- profile ----

    @Test
    fun profile_isNullUntilCreated_andTheUsernameRulesAreTheServers() = runTest {
        val repository = FakeSocialProfileRepository()
        assertNull(repository.getMyProfile().value())

        assertEquals(SocialError.Rejected(SocialRejection.InvalidUsername), repository.createProfile("ab").error())
        repository.takenUsernames += "Vale_NS"
        assertEquals(SocialError.Rejected(SocialRejection.UsernameTaken), repository.createProfile("vale_ns").error())

        val created = repository.createProfile("valeNightwatch").value()
        assertEquals("valeNightwatch", created.username)
        assertFalse(created.hideProgress)
        assertEquals(created, repository.getMyProfile().value())
        repository.setHideProgress(true)
        assertTrue(repository.getMyProfile().value()!!.hideProgress)
    }

    @Test
    fun profile_hidingProgressWithoutAProfile_isNotEnabled() = runTest {
        assertEquals(SocialError.NotEnabled, FakeSocialProfileRepository().setHideProgress(true).error())
    }

    // ---- friends: "amigo aceptado" ----

    @Test
    fun friends_invitedPersonAccepts_becomesAFriend() = runTest {
        val repository = FakeFriendsRepository()
        repository.addPerson("valeNightwatch", "NIGHT-7F3K2A")

        assertEquals(FriendRelation.None, repository.search("valeNightwatch").value().relation)
        repository.invite("valeNightwatch").value()
        assertEquals(FriendRelation.Sent, repository.search("NIGHT-7F3K2A").value().relation)
        assertEquals(SocialError.Rejected(SocialRejection.RequestExists), repository.invite("valeNightwatch").error())

        repository.otherAccepts("NIGHT-7F3K2A")

        assertEquals(listOf("valeNightwatch"), repository.list().value().map { it.username })
        assertTrue(repository.requests().value().sent.isEmpty())
        assertEquals(FriendRelation.Friends, repository.search("valeNightwatch").value().relation)
        assertEquals(SocialError.Rejected(SocialRejection.AlreadyFriends), repository.invite("valeNightwatch").error())
    }

    @Test
    fun friends_aReceivedRequest_canBeAcceptedOrDeclined_andAGoneOneIsRejected() = runTest {
        val repository = FakeFriendsRepository()
        repository.received += PersonRef("camila", "NIGHT-AAAAAA")
        repository.received += PersonRef("jorge", "NIGHT-BBBBBB")

        repository.accept("NIGHT-AAAAAA").value()
        repository.decline("NIGHT-BBBBBB").value()

        assertEquals(listOf("camila"), repository.list().value().map { it.username })
        assertEquals(SocialError.Rejected(SocialRejection.RequestGone), repository.accept("NIGHT-AAAAAA").error())
    }

    @Test
    fun friends_anUnknownPerson_isNotFound_sameAsTheServer() = runTest {
        val repository = FakeFriendsRepository()

        assertEquals(SocialError.Rejected(SocialRejection.NotFound), repository.search("nadie_por_aqui").error())
        assertEquals(SocialError.Rejected(SocialRejection.NotFound), repository.invite("nadie_por_aqui").error())
        assertEquals(SocialError.InvalidInput, repository.search("ab").error())
    }

    @Test
    fun friends_profileOfAFriend_andAFailureOverride() = runTest {
        val repository = FakeFriendsRepository()
        repository.friends += Friend("valeNightwatch", "NIGHT-7F3K2A", 4, trainedToday = true)

        assertEquals(4, repository.profile("NIGHT-7F3K2A").value().streakDays)
        assertEquals(SocialError.Rejected(SocialRejection.NotFound), repository.profile("NIGHT-ZZZZZZ").error())
        repository.failure = SocialError.Offline
        assertEquals(SocialError.Offline, repository.list().error())
    }

    // ---- pairs ----

    @Test
    fun pairs_theTopStreakIsTheLargestCurrentOne() = runTest {
        val repository = FakePairStreakRepository()
        repository.pairs += FakePairStreakRepository.activePair("vale", "NIGHT-7F3K2A", 2)
        repository.pairs += FakePairStreakRepository.activePair("mateo", "NIGHT-9H4M5P", 5, PairState.AtRisk)

        assertEquals(5, repository.overview().value().topCurrentDays)
        assertEquals(PairState.AtRisk, repository.detail("NIGHT-9H4M5P").value().metrics!!.state)
        assertEquals(SocialError.Rejected(SocialRejection.PairGone), repository.detail("NIGHT-ZZZZZZ").error())
    }

    @Test
    fun pairs_aNonFriendCannotBeInvited_andAnInviteGoesPendingThenActive() = runTest {
        val repository = FakePairStreakRepository()
        repository.friends["NIGHT-7F3K2A"] = "vale"

        assertEquals(SocialError.Rejected(SocialRejection.NotFound), repository.invite("desconocido").error())
        repository.invite("vale").value()
        assertEquals(PairStatus.PendingSent, repository.overview().value().pairs[0].status)
        assertEquals(SocialError.Rejected(SocialRejection.PairExists), repository.invite("vale").error())
    }

    // ---- lobby ----

    @Test
    fun lobby_aSearchThatReachesSixtySeconds_suggestsAFriend_andACancelIsCounted() = runTest {
        val lobby = FakeBattleLobbyRepository()

        assertEquals(QuickMatchStatus.Waiting(0, false), lobby.quickJoin().value())
        repeat(19) { lobby.quickPoll() }
        assertEquals(QuickMatchStatus.Waiting(60, true), lobby.quickPoll().value())
        assertEquals(QuickMatchStatus.Cancelled, lobby.quickCancel().value())
        assertEquals(1, lobby.cancelCalls)
    }

    // ---- battle scenarios ----

    private suspend fun BattleRepository.states(count: Int) = List(count) { sync(BATTLE_ID).value() }

    @Test
    fun scenario_aRivalThatDoesNotAnswer_theTurnPassesWithATimeout() = runTest {
        val states = FakeBattleRepository(BattleScenarios.rivalDoesNotAnswer()).states(3)

        assertFalse(states[0].turn!!.isMine)
        assertNull(states[0].question)
        assertTrue(states[1].turn!!.timeLeft.secondsAtReceipt < 5.0)
        assertEquals(TurnOutcome.Timeout, states[2].lastTurn!!.outcome)
        assertTrue(states[2].turn!!.isMine)
    }

    @Test
    fun scenario_myTurnExpires_theRivalPlaysAndTheTimeoutIsMine() = runTest {
        val states = FakeBattleRepository(BattleScenarios.myTurnExpires()).states(2)

        assertTrue(states[0].turn!!.isMine)
        assertFalse(states[1].turn!!.isMine)
        assertTrue(states[1].lastTurn!!.byMe)
        assertEquals(TurnOutcome.Timeout, states[1].lastTurn!!.outcome)
        assertEquals(states[0].myHp, states[1].myHp)
    }

    @Test
    fun scenario_ultiWithShield_hitsForTwentyThree() = runTest {
        val states = FakeBattleRepository(BattleScenarios.ultiWithShield()).states(2)

        assertEquals(TurnPhase.Ulti, states[0].turn!!.phase)
        assertEquals(UltiRole.Attacker, states[0].ulti!!.role)
        assertEquals(23, states[1].lastTurn!!.damage)
        assertTrue(states[1].lastUlti!!.shield)
        assertEquals(40 - 23, states[1].rivalHp)
    }

    @Test
    fun scenario_lostByDisconnection_andWonByTheRivalsDisconnection() = runTest {
        val lost = FakeBattleRepository(BattleScenarios.lostByDisconnection()).states(2)
        val won = FakeBattleRepository(BattleScenarios.wonBecauseRivalDisconnected()).states(2)

        assertEquals(BattleOutcome.Loss, lost[1].result!!.outcome)
        assertEquals(0, lost[1].result!!.xp)
        assertEquals(BattleEndReason.ForfeitDisconnect, lost[1].endReason)
        assertTrue(won[0].rivalSecondsSinceSeen >= 40.0)
        assertEquals(BattleOutcome.Win, won[1].result!!.outcome)
    }

    @Test
    fun theFakeBattle_recordsTheActionsItReceives() = runTest {
        val battle = FakeBattleRepository(listOf(SocialResult.Success(BattleScenarios.state())))

        battle.answer(BATTLE_ID, 3, 'B')
        battle.useUlti(BATTLE_ID)
        battle.submitUltiCommand(BATTLE_ID, "crack 123456")
        battle.abandon(BATTLE_ID)

        assertEquals(listOf("answer:3:B", "use_ulti", "ulti_submit:crack 123456", "abandon"), battle.actions)
    }

    @Test
    fun theFakeBattleLobby_canScriptAChallengeBecomingABattle() = runTest {
        val lobby = FakeBattleLobbyRepository()

        assertEquals(BATTLE_ID, lobby.acceptChallenge("NIGHT-AAAAAA").value())
        lobby.sendChallenge("NIGHT-9H4M5P").value()
        assertEquals(listOf("NIGHT-9H4M5P"), lobby.sentChallengesTo)
        lobby.failure = SocialError.RateLimited
        assertEquals(SocialError.RateLimited, lobby.challenges().error())
    }
}
