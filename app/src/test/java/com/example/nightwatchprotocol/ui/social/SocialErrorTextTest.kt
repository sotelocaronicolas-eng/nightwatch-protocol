package com.example.nightwatchprotocol.ui.social

import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialRejection
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every error and every reason has its own message in `strings_social.xml`: a raw code of the server is never shown. */
class SocialErrorTextTest {

    private val allErrors: List<SocialError> = listOf(
        SocialError.Offline, SocialError.NoSession, SocialError.NotEnabled, SocialError.RateLimited,
        SocialError.InvalidInput, SocialError.PermissionDenied, SocialError.Unknown,
    ) + SocialRejection.entries.map { SocialError.Rejected(it) }

    private fun stringsFile(): File = listOf(
        File("src/main/res/values/strings_social.xml"),
        File("app/src/main/res/values/strings_social.xml"),
    ).first { it.isFile }

    /** name -> text of every `<string>` of the file. */
    private fun strings(): Map<String, String> =
        Regex("""<string name="([a-z_]+)"[^>]*>(.*?)</string>""").findAll(stringsFile().readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun snake(name: String) = name.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()

    private val expectedNames: Set<String> =
        listOf("Offline", "NoSession", "NotEnabled", "RateLimited", "InvalidInput", "PermissionDenied", "Unknown")
            .map { "social_error_${snake(it)}" }.toSet() +
            SocialRejection.entries.map { "social_rejected_${snake(it.name)}" }

    @Test
    fun everyError_hasADifferentMessage() {
        val ids = allErrors.map { it.messageRes() }

        assertTrue(ids.all { it != 0 })
        assertEquals("hay dos errores con el mismo texto", ids.size, ids.toSet().size)
    }

    @Test
    fun theStringsFile_hasExactlyTheMessagesOfTheErrors() {
        assertEquals(expectedNames, strings().keys)
    }

    @Test
    fun everyMessage_isReadableSpanish_notACode() {
        val codes = SocialRejection.entries.mapNotNull { it.code } +
            listOf("no_session", "social_not_enabled", "bad_action", "bad_query", "rate_limited")

        strings().forEach { (name, text) ->
            assertTrue("$name está vacío", text.isNotBlank())
            assertTrue("$name debe empezar en mayúscula", text.first().isUpperCase())
            assertTrue("$name debe terminar en punto", text.trimEnd().endsWith("."))
            codes.forEach { code -> assertFalse("$name muestra el código $code", text.contains(code)) }
        }
    }

    @Test
    fun theRejectionsAreTheOnesTheScriptCanAnswer() {
        val fromTheScript = setOf(
            "not_found", "invalid_target", "already_friends", "request_exists", "request_incoming", "request_gone", "cooldown",
            "too_many_pending", "friends_limit", "pair_exists", "pair_gone", "challenge_pending", "challenge_gone",
            "already_in_battle", "rival_busy", "battle_over", "not_your_turn", "stale_turn", "bad_option", "ulti_not_charged",
            "no_ulti", "bad_command", "already_resolved", "bank_empty",
        )

        assertEquals(fromTheScript, SocialRejection.entries.mapNotNull { it.code }.toSet())
        // The two that come from Postgres itself (profile insert) have no server code.
        assertEquals(setOf(SocialRejection.UsernameTaken, SocialRejection.InvalidUsername), SocialRejection.entries.filter { it.code == null }.toSet())
    }
}
