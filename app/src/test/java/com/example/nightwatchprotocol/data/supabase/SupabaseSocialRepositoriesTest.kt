package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.social.BATTLE_ID
import com.example.nightwatchprotocol.data.social.BATTLE_MY_QUESTION_TURN
import com.example.nightwatchprotocol.data.social.BattleStatus
import com.example.nightwatchprotocol.data.social.CHALLENGES
import com.example.nightwatchprotocol.data.social.FRIENDS_LIST
import com.example.nightwatchprotocol.data.social.FRIEND_REQUESTS
import com.example.nightwatchprotocol.data.social.PAIR_OVERVIEW
import com.example.nightwatchprotocol.data.social.QuickMatchStatus
import com.example.nightwatchprotocol.data.social.RecordingSocialRpc
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialRejection
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.data.social.TestMonotonicClock
import com.example.nightwatchprotocol.data.social.errorBody
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The repositories call the functions of 0008 with the exact names and parameters, never send a user id, and turn every answer
 * into a typed result. The server is simulated by a [RecordingSocialRpc] (no network).
 */
class SupabaseSocialRepositoriesTest {
    private val rpc = RecordingSocialRpc()
    private val clock = TestMonotonicClock()
    private val friends = SupabaseFriendsRepository(rpc)
    private val pairs = SupabasePairStreakRepository(rpc)
    private val lobby = SupabaseBattleLobbyRepository(rpc, clock)
    private val battles = SupabaseBattleRepository(rpc, clock)

    private fun lastCall() = rpc.calls.last()

    private fun <T> SocialResult<T>.error(): SocialError = (this as SocialResult.Failure).error

    private fun <T> SocialResult<T>.value(): T = (this as SocialResult.Success).value

    private fun assertCalled(function: String, params: String) {
        assertEquals(function, lastCall().function)
        assertEquals(params, lastCall().params.toString())
    }

    // ---- social_friends ----

    @Test
    fun friends_list_callsSocialFriendsWithTheListAction() = runTest {
        rpc.answerJson(FRIENDS_LIST)

        val result = friends.list()

        assertCalled("social_friends", """{"p_action":"list"}""")
        assertEquals(2, result.value().size)
    }

    @Test
    fun friends_requests_search_invite_andTheRest_useTheirActions() = runTest {
        rpc.answerJson(FRIEND_REQUESTS)
        friends.requests()
        assertCalled("social_friends", """{"p_action":"requests"}""")

        rpc.answerJson("""{"status":"ok","found":true,"username":"vale","friend_code":"NIGHT-7F3K2A","relation":"none"}""")
        friends.search("  vale ")
        assertCalled("social_friends", """{"p_action":"search","p_arg":"vale"}""")

        rpc.answerJson("""{"status":"sent"}""")
        assertEquals(Unit, friends.invite("NIGHT-7F3K2A").value())
        assertCalled("social_friends", """{"p_action":"invite","p_arg":"NIGHT-7F3K2A"}""")

        rpc.answerJson("""{"status":"accepted"}""")
        friends.accept("NIGHT-AAAAAA")
        assertCalled("social_friends", """{"p_action":"accept","p_arg":"NIGHT-AAAAAA"}""")

        rpc.answerJson("""{"status":"declined"}""")
        friends.decline("NIGHT-AAAAAA")
        assertCalled("social_friends", """{"p_action":"decline","p_arg":"NIGHT-AAAAAA"}""")

        rpc.answerJson("""{"status":"cancelled"}""")
        friends.cancel("NIGHT-BBBBBB")
        assertCalled("social_friends", """{"p_action":"cancel","p_arg":"NIGHT-BBBBBB"}""")

        rpc.answerJson("""{"status":"removed"}""")
        friends.remove("NIGHT-BBBBBB")
        assertCalled("social_friends", """{"p_action":"remove","p_arg":"NIGHT-BBBBBB"}""")
    }

    @Test
    fun friends_aBusinessError_comesBackAsARejection_notAnException() = runTest {
        rpc.answerJson(errorBody("cooldown"))

        assertEquals(SocialError.Rejected(SocialRejection.Cooldown), friends.invite("valeNightwatch").error())
    }

    @Test
    fun friends_anAnswerWithTheWrongStatus_isNotASuccess() = runTest {
        rpc.answerJson("""{"status":"accepted"}""")

        assertEquals(SocialError.Unknown, friends.invite("valeNightwatch").error())
    }

    @Test
    fun friends_aFailedCall_isTheSameFailure() = runTest {
        rpc.answer(SocialResult.Failure(SocialError.RateLimited))

        assertEquals(SocialError.RateLimited, friends.search("valeNightwatch").error())
        assertEquals(SocialError.RateLimited, friends.list().error())
    }

    @Test
    fun friends_aTextOutsideTheServersBounds_isRejectedBeforeTheNetwork() = runTest {
        assertEquals(SocialError.InvalidInput, friends.search("ab").error())
        assertEquals(SocialError.InvalidInput, friends.invite("   ").error())
        assertEquals(SocialError.InvalidInput, friends.search("x".repeat(41)).error())
        assertTrue(rpc.calls.isEmpty())
    }

    // ---- social_pairs ----

    @Test
    fun pairs_useTheirActions_andReadTheOverview() = runTest {
        rpc.answerJson(PAIR_OVERVIEW)
        assertEquals(2, pairs.overview().value().topCurrentDays)
        assertCalled("social_pairs", """{"p_action":"overview"}""")

        pairs.detail("NIGHT-7F3K2A")
        assertCalled("social_pairs", """{"p_action":"detail","p_arg":"NIGHT-7F3K2A"}""")

        rpc.answerJson("""{"status":"sent"}""")
        pairs.invite("NIGHT-7F3K2A")
        assertCalled("social_pairs", """{"p_action":"invite","p_arg":"NIGHT-7F3K2A"}""")

        rpc.answerJson("""{"status":"active"}""")
        pairs.accept("NIGHT-7F3K2A")
        assertCalled("social_pairs", """{"p_action":"accept","p_arg":"NIGHT-7F3K2A"}""")

        rpc.answerJson("""{"status":"declined"}""")
        pairs.decline("NIGHT-7F3K2A")
        assertCalled("social_pairs", """{"p_action":"decline","p_arg":"NIGHT-7F3K2A"}""")

        rpc.answerJson("""{"status":"cancelled"}""")
        pairs.cancel("NIGHT-7F3K2A")
        assertCalled("social_pairs", """{"p_action":"cancel","p_arg":"NIGHT-7F3K2A"}""")

        rpc.answerJson("""{"status":"ended"}""")
        pairs.end("NIGHT-7F3K2A")
        assertCalled("social_pairs", """{"p_action":"end","p_arg":"NIGHT-7F3K2A"}""")
    }

    @Test
    fun pairs_inviteANonFriend_isTheSameNotFoundAsAnUnknownPerson() = runTest {
        rpc.answerJson(errorBody("not_found"))

        assertEquals(SocialError.Rejected(SocialRejection.NotFound), pairs.invite("alguien_que_no_existe").error())
    }

    // ---- battle_lobby ----

    @Test
    fun lobby_quickMatchActions_andTheirAnswers() = runTest {
        rpc.answerJson("""{"status":"waiting","waited_seconds":0,"suggest_friend":false}""")
        assertEquals(QuickMatchStatus.Waiting(0, false), lobby.quickJoin().value())
        assertCalled("battle_lobby", """{"p_action":"quick_join"}""")

        rpc.answerJson("""{"status":"matched","battle_id":"$BATTLE_ID"}""")
        assertEquals(QuickMatchStatus.Matched(BATTLE_ID), lobby.quickPoll().value())
        assertCalled("battle_lobby", """{"p_action":"quick_poll"}""")

        rpc.answerJson("""{"status":"cancelled"}""")
        assertEquals(QuickMatchStatus.Cancelled, lobby.quickCancel().value())
        assertCalled("battle_lobby", """{"p_action":"quick_cancel"}""")
    }

    @Test
    fun lobby_challengeActions_andTheirAnswers() = runTest {
        rpc.answerJson("""{"status":"sent","seconds_left":120}""")
        assertEquals(Unit, lobby.sendChallenge("NIGHT-9H4M5P").value())
        assertCalled("battle_lobby", """{"p_action":"challenge_send","p_arg":"NIGHT-9H4M5P"}""")

        rpc.answerJson("""{"status":"matched","battle_id":"$BATTLE_ID"}""")
        assertEquals(BATTLE_ID, lobby.acceptChallenge("NIGHT-AAAAAA").value())
        assertCalled("battle_lobby", """{"p_action":"challenge_accept","p_arg":"NIGHT-AAAAAA"}""")

        rpc.answerJson("""{"status":"declined"}""")
        lobby.declineChallenge("NIGHT-AAAAAA")
        assertCalled("battle_lobby", """{"p_action":"challenge_decline","p_arg":"NIGHT-AAAAAA"}""")

        rpc.answerJson("""{"status":"cancelled"}""")
        lobby.cancelChallenge()
        assertCalled("battle_lobby", """{"p_action":"challenge_cancel"}""")

        rpc.answerJson(CHALLENGES)
        assertEquals("camila", lobby.challenges().value().received[0].username)
        assertCalled("battle_lobby", """{"p_action":"challenges"}""")
    }

    @Test
    fun lobby_challengingANonFriend_isNotFound_andAnExpiredChallengeIsGone() = runTest {
        rpc.answerJson(errorBody("not_found"))
        assertEquals(SocialError.Rejected(SocialRejection.NotFound), lobby.sendChallenge("NIGHT-9H4M5P").error())

        rpc.answerJson(errorBody("challenge_gone"))
        assertEquals(SocialError.Rejected(SocialRejection.ChallengeGone), lobby.acceptChallenge("NIGHT-AAAAAA").error())
    }

    // ---- battle_sync and battle_play ----

    @Test
    fun battle_sync_callsBattleSyncWithOnlyTheBattleId() = runTest {
        rpc.answerJson(BATTLE_MY_QUESTION_TURN)

        val state = battles.sync(BATTLE_ID).value()

        assertCalled("battle_sync", """{"p_battle":"$BATTLE_ID"}""")
        assertEquals(BattleStatus.Active, state.status)
    }

    @Test
    fun battle_actions_callBattlePlayWithTheirParameters() = runTest {
        rpc.answerJson(BATTLE_MY_QUESTION_TURN)

        battles.answer(BATTLE_ID, turnNumber = 3, option = 'B')
        assertCalled("battle_play", """{"p_battle":"$BATTLE_ID","p_action":"answer","p_turn":3,"p_arg":"B"}""")

        battles.useUlti(BATTLE_ID)
        assertCalled("battle_play", """{"p_battle":"$BATTLE_ID","p_action":"use_ulti"}""")

        battles.submitUltiCommand(BATTLE_ID, "crack 123456")
        assertCalled("battle_play", """{"p_battle":"$BATTLE_ID","p_action":"ulti_submit","p_arg":"crack 123456"}""")

        battles.abandon(BATTLE_ID)
        assertCalled("battle_play", """{"p_battle":"$BATTLE_ID","p_action":"abandon"}""")
    }

    @Test
    fun battle_theServerRejections_comeBackAsRejections() = runTest {
        rpc.answerJson(errorBody("stale_turn"))
        assertEquals(SocialError.Rejected(SocialRejection.StaleTurn), battles.answer(BATTLE_ID, 2, 'A').error())

        rpc.answer(SocialResult.Failure(SocialError.Rejected(SocialRejection.BattleOver)))
        assertEquals(SocialError.Rejected(SocialRejection.BattleOver), battles.sync(BATTLE_ID).error())
    }

    @Test
    fun battle_inputsTheServerWouldRejectAnyway_neverReachTheNetwork() = runTest {
        assertEquals(SocialError.InvalidInput, battles.answer(BATTLE_ID, 1, 'E').error())
        assertEquals(SocialError.InvalidInput, battles.answer(BATTLE_ID, 1, 'a').error())
        assertEquals(SocialError.InvalidInput, battles.submitUltiCommand(BATTLE_ID, "   ").error())
        assertEquals(SocialError.InvalidInput, battles.submitUltiCommand(BATTLE_ID, "x".repeat(201)).error())
        assertEquals(SocialError.InvalidInput, battles.sync("no-es-un-uuid").error())
        assertEquals(SocialError.InvalidInput, battles.abandon("tampoco").error())
        assertTrue(rpc.calls.isEmpty())
    }

    // ---- nothing identifies the person in what is sent ----

    @Test
    fun noCallEverCarriesAUserId() = runTest {
        rpc.answerJson("""{"status":"sent"}""")
        friends.list(); friends.requests(); friends.search("valeNightwatch"); friends.invite("valeNightwatch")
        pairs.overview(); pairs.invite("valeNightwatch")
        lobby.quickJoin(); lobby.quickPoll(); lobby.sendChallenge("NIGHT-9H4M5P"); lobby.challenges()
        battles.sync(BATTLE_ID); battles.answer(BATTLE_ID, 1, 'A'); battles.abandon(BATTLE_ID)

        val allowed = setOf("p_action", "p_arg", "p_battle", "p_turn")
        rpc.calls.forEach { call ->
            assertTrue("${call.function} envía ${call.params.keys}", call.params.keys.all { it in allowed })
        }
        assertEquals(
            setOf("social_friends", "social_pairs", "battle_lobby", "battle_sync", "battle_play"),
            rpc.calls.map { it.function }.toSet(),
        )
    }

    @Test
    fun theAnswerToAStateIsStampedWithTheMonotonicClock() = runTest {
        clock.nanos = 9_000_000_000L
        rpc.answerJson(BATTLE_MY_QUESTION_TURN)

        val state = battles.sync(BATTLE_ID).value()

        assertEquals(9_000_000_000L, state.turn!!.timeLeft.receivedAtNanos)
        clock.advanceMillis(7_400)
        assertEquals(50_000L, state.turn!!.timeLeft.remainingMillis(clock.nanoTime()))
    }
}
