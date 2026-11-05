package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.social.BATTLE_ID
import com.example.nightwatchprotocol.data.social.BATTLE_MY_QUESTION_TURN
import com.example.nightwatchprotocol.data.social.BattleStatus
import com.example.nightwatchprotocol.data.social.RecordingSocialRpc
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialResult
import com.example.nightwatchprotocol.data.social.TestMonotonicClock
import com.example.nightwatchprotocol.data.social.json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `p_lang` of 0013: sent with English only, and a server without 0013 never breaks the battle. */
class BattleLanguageTest {
    private val rpc = RecordingSocialRpc()
    private val clock = TestMonotonicClock()
    private var language: String? = "en"
    private val battles = SupabaseBattleRepository(rpc, clock) { language }

    private fun paramsOf(index: Int) = rpc.calls[index].params.toString()

    @Test
    fun english_sendsPLangInSyncAndInEveryPlayAction() = runTest {
        rpc.answerJson(BATTLE_MY_QUESTION_TURN)

        battles.sync(BATTLE_ID)
        battles.answer(BATTLE_ID, 3, 'B')
        battles.useUlti(BATTLE_ID)
        battles.submitUltiCommand(BATTLE_ID, "crack 123456")
        battles.abandon(BATTLE_ID)

        assertEquals("""{"p_battle":"$BATTLE_ID","p_lang":"en"}""", paramsOf(0))
        assertEquals("""{"p_battle":"$BATTLE_ID","p_action":"answer","p_turn":3,"p_arg":"B","p_lang":"en"}""", paramsOf(1))
        assertEquals("""{"p_battle":"$BATTLE_ID","p_action":"use_ulti","p_lang":"en"}""", paramsOf(2))
        assertEquals("""{"p_battle":"$BATTLE_ID","p_action":"ulti_submit","p_arg":"crack 123456","p_lang":"en"}""", paramsOf(3))
        assertEquals("""{"p_battle":"$BATTLE_ID","p_action":"abandon","p_lang":"en"}""", paramsOf(4))
    }

    @Test
    fun spanishOrNoLanguage_sendsExactlyWhatV230Sent() = runTest {
        rpc.answerJson(BATTLE_MY_QUESTION_TURN)

        language = "es"
        battles.sync(BATTLE_ID)
        language = null
        battles.answer(BATTLE_ID, 3, 'B')

        assertEquals("""{"p_battle":"$BATTLE_ID"}""", paramsOf(0))
        assertEquals("""{"p_battle":"$BATTLE_ID","p_action":"answer","p_turn":3,"p_arg":"B"}""", paramsOf(1))
    }

    @Test
    fun theLanguageIsReadOnEveryCall_soChangingItIsImmediate() = runTest {
        rpc.answerJson(BATTLE_MY_QUESTION_TURN)

        battles.sync(BATTLE_ID)
        language = "es"
        battles.sync(BATTLE_ID)

        assertTrue("p_lang" in paramsOf(0))
        assertTrue("p_lang" !in paramsOf(1))
    }

    @Test
    fun serverWithout0013_theCallIsRepeatedWithoutPLang_andTheBattleGoesOn() = runTest {
        rpc.answer(
            SocialResult.Failure(SocialError.Unavailable), // PGRST202: the function has no p_lang yet
            SocialResult.Success(json(BATTLE_MY_QUESTION_TURN)),
        )

        val result = battles.sync(BATTLE_ID)

        assertEquals(BattleStatus.Active, (result as SocialResult.Success).value.status)
        assertEquals(2, rpc.calls.size)
        assertTrue("p_lang" in paramsOf(0))
        assertEquals("""{"p_battle":"$BATTLE_ID"}""", paramsOf(1))
    }

    @Test
    fun serverWithout0013_afterTheFirstFailure_pLangIsNeverSentAgain() = runTest {
        rpc.answer(
            SocialResult.Failure(SocialError.Unavailable),
            SocialResult.Success(json(BATTLE_MY_QUESTION_TURN)),
        )

        battles.sync(BATTLE_ID) // fails with p_lang, repeats without
        battles.answer(BATTLE_ID, 2, 'A')
        battles.sync(BATTLE_ID)

        assertEquals(4, rpc.calls.size)
        rpc.calls.drop(2).forEach { assertTrue(it.params.toString(), "p_lang" !in it.params.toString()) }
    }

    @Test
    fun otherFailures_areNotMistakenForAnOldServer() = runTest {
        rpc.answer(SocialResult.Failure(SocialError.Offline))

        val result = battles.sync(BATTLE_ID)

        assertEquals(SocialError.Offline, (result as SocialResult.Failure).error)
        assertEquals(1, rpc.calls.size)
        rpc.answer(SocialResult.Success(json(BATTLE_MY_QUESTION_TURN)))
        battles.sync(BATTLE_ID)
        assertTrue("p_lang" in paramsOf(1)) // still tries English: the server was not shown to be old
    }
}
