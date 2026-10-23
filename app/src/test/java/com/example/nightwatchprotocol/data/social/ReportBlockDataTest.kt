package com.example.nightwatchprotocol.data.social

import com.example.nightwatchprotocol.data.supabase.SupabaseFriendsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The wire format of report, block, unblock and blocks (`0011_reportes_bloqueos.sql`), without a network. */
class ReportBlockDataTest {

    /** Records which of the two entry points a call used, to prove the new actions go through [SocialRpcClient.callNewAction]. */
    private class Rpc(private val answer: SocialResult<JsonElement>) : SocialRpcClient {
        val normal = mutableListOf<RecordingSocialRpc.Call>()
        val newActions = mutableListOf<RecordingSocialRpc.Call>()

        override suspend fun call(function: String, params: JsonObject): SocialResult<JsonElement> {
            normal += RecordingSocialRpc.Call(function, params)
            return answer
        }

        override suspend fun callNewAction(function: String, params: JsonObject): SocialResult<JsonElement> {
            newActions += RecordingSocialRpc.Call(function, params)
            return answer
        }
    }

    private fun arg(call: RecordingSocialRpc.Call) = (call.params["p_arg"] as JsonPrimitive).content

    private fun action(call: RecordingSocialRpc.Call) = (call.params["p_action"] as JsonPrimitive).content

    // ---- the argument of report ----

    @Test
    fun reportArg_hasThreePartsAlways_evenWithoutANote() {
        assertEquals("mateo|harassment|", reportArg("mateo", ReportReason.Harassment, ""))
        assertEquals("mateo|other|hola", reportArg("  mateo ", ReportReason.Other, "  hola  "))
    }

    @Test
    fun reportArg_usesTheServerCodeOfEachReason() {
        assertEquals(
            listOf("inappropriate_photo", "offensive_name", "harassment", "other"),
            ReportReason.entries.map { it.code },
        )
    }

    @Test
    fun reportArg_keepsBarsInTheNote() {
        assertEquals("mateo|other|a|b|c", reportArg("mateo", ReportReason.Other, "a|b|c"))
    }

    // ---- the repository ----

    @Test
    fun report_callsTheNewActionWithTheArgument_andReadsTheAck() = runTest {
        val rpc = Rpc(SocialResult.Success(json("""{"status":"reported"}""")))

        val result = SupabaseFriendsRepository(rpc).report("mateo", ReportReason.Harassment, "me insulta")

        assertTrue(result is SocialResult.Success)
        assertTrue(rpc.normal.isEmpty())
        val call = rpc.newActions.single()
        assertEquals(SOCIAL_FRIENDS_RPC, call.function)
        assertEquals("report", action(call))
        assertEquals("mateo|harassment|me insulta", arg(call))
    }

    @Test
    fun report_withANoteOver200_isNotSent() = runTest {
        val rpc = Rpc(SocialResult.Success(json("""{"status":"reported"}""")))

        val result = SupabaseFriendsRepository(rpc).report("mateo", ReportReason.Other, "x".repeat(REPORT_NOTE_MAX_LENGTH + 1))

        assertEquals(SocialError.InvalidInput, (result as SocialResult.Failure).error)
        assertTrue(rpc.newActions.isEmpty())
    }

    @Test
    fun report_acceptsANoteOfExactly200_andATargetThatIsTooShortIsNotSent() = runTest {
        val rpc = Rpc(SocialResult.Success(json("""{"status":"reported"}""")))
        val repository = SupabaseFriendsRepository(rpc)

        assertTrue(repository.report("mateo", ReportReason.Other, "x".repeat(REPORT_NOTE_MAX_LENGTH)) is SocialResult.Success)
        assertEquals(SocialError.InvalidInput, (repository.report("ab", ReportReason.Other, "") as SocialResult.Failure).error)
        assertEquals(1, rpc.newActions.size)
    }

    @Test
    fun blockAndUnblock_sendTheNameOrCode_andExpectTheirOwnStatus() = runTest {
        val blockRpc = Rpc(SocialResult.Success(json("""{"status":"blocked"}""")))
        val unblockRpc = Rpc(SocialResult.Success(json("""{"status":"unblocked"}""")))

        assertTrue(SupabaseFriendsRepository(blockRpc).block(" mateo ") is SocialResult.Success)
        assertTrue(SupabaseFriendsRepository(unblockRpc).unblock("NIGHT-9H4M5P") is SocialResult.Success)

        assertEquals("block" to "mateo", action(blockRpc.newActions.single()) to arg(blockRpc.newActions.single()))
        assertEquals("unblock" to "NIGHT-9H4M5P", action(unblockRpc.newActions.single()) to arg(unblockRpc.newActions.single()))
    }

    @Test
    fun anAnswerWithAnotherStatus_isNotTakenAsDone() = runTest {
        val rpc = Rpc(SocialResult.Success(json("""{"status":"sent"}""")))

        val result = SupabaseFriendsRepository(rpc).block("mateo")

        assertEquals(SocialError.Unknown, (result as SocialResult.Failure).error)
    }

    @Test
    fun blocks_readsTheBlockedPeople_withTheirPhotoKeys() = runTest {
        val rpc = Rpc(
            SocialResult.Success(
                json(
                    """{"blocked":[
                      {"username":"mateo","friend_code":"NIGHT-9H4M5P","avatar_key":"11111111-1111-4111-8111-111111111111","avatar_version":2},
                      {"username":"sofia","friend_code":"NIGHT-AAAAAA","avatar_key":null,"avatar_version":null}
                    ]}""",
                ),
            ),
        )

        val people = (SupabaseFriendsRepository(rpc).blocks() as SocialResult.Success).value

        assertEquals(
            listOf(
                PersonRef("mateo", "NIGHT-9H4M5P", "11111111-1111-4111-8111-111111111111", 2),
                PersonRef("sofia", "NIGHT-AAAAAA", null, null),
            ),
            people,
        )
        assertEquals("blocks", action(rpc.newActions.single()))
    }

    @Test
    fun anUnreadableBlocksList_isUnknown() {
        assertEquals(SocialError.Unknown, (parseBlocks(json("""{"blocked":"no es lista"}""")) as SocialResult.Failure).error)
    }

    @Test
    fun theFailureOfTheCallPassesThrough_asUnavailable() = runTest {
        val rpc = Rpc(SocialResult.Failure(SocialError.Unavailable))
        val repository = SupabaseFriendsRepository(rpc)

        assertEquals(SocialError.Unavailable, (repository.report("mateo", ReportReason.Other, "") as SocialResult.Failure).error)
        assertEquals(SocialError.Unavailable, (repository.block("mateo") as SocialResult.Failure).error)
        assertEquals(SocialError.Unavailable, (repository.unblock("mateo") as SocialResult.Failure).error)
        assertEquals(SocialError.Unavailable, (repository.blocks() as SocialResult.Failure).error)
    }

    // ---- the error codes ----

    @Test
    fun badReport_isInvalidInput_andBadActionStaysUnknownForTheOldActions() {
        assertEquals(SocialError.InvalidInput, socialErrorFromCode("bad_report"))
        assertEquals(SocialError.Unknown, socialErrorFromCode("bad_action"))
    }

    @Test
    fun theBusinessAnswerNotFound_ofTheNewActions_isARejection() {
        val result = parseAck(json(errorBody("not_found")), "blocked")

        assertEquals(SocialError.Rejected(SocialRejection.NotFound), (result as SocialResult.Failure).error)
    }
}
