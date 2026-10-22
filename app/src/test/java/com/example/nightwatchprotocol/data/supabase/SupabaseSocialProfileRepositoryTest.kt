package com.example.nightwatchprotocol.data.supabase

import com.example.nightwatchprotocol.data.social.BattleOutcome
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialRejection
import com.example.nightwatchprotocol.data.social.SocialResult
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseSocialProfileRepositoryTest {
    // The serializer Postgrest uses: encodeDefaults is OFF.
    private val json = Json { ignoreUnknownKeys = true }

    // ---- what is sent: only the columns `authenticated` may write (0008) ----

    @Test
    fun profileInsert_sendsOnlyUserIdAndUsername() {
        val encoded = json.encodeToJsonElement(SocialProfileInsert.serializer(), SocialProfileInsert("u1", "valeNightwatch")).jsonObject

        assertEquals(setOf("user_id", "username"), encoded.keys)
        assertEquals("valeNightwatch", encoded.getValue("username").jsonPrimitive.content)
    }

    @Test
    fun hideProgressUpdate_sendsOnlyThatColumn_evenWhenFalse() {
        val shown = json.encodeToJsonElement(HideProgressUpdate.serializer(), HideProgressUpdate(false)).jsonObject
        val hidden = json.encodeToJsonElement(HideProgressUpdate.serializer(), HideProgressUpdate(true)).jsonObject

        assertEquals(setOf("hide_progress"), shown.keys)
        assertEquals("false", shown.getValue("hide_progress").jsonPrimitive.content)
        assertEquals("true", hidden.getValue("hide_progress").jsonPrimitive.content)
    }

    // ---- what is read ----

    @Test
    fun profileRow_becomesTheProfile_withoutTheAuthId() {
        val row = json.decodeFromString<SocialProfileRow>(
            """{"user_id":"u1","username":"valeNightwatch","friend_code":"NIGHT-7F3K2A","hide_progress":true,"created_at":"2026-10-21T10:00:00+00:00"}""",
        )

        val profile = row.toDomain()

        assertEquals("valeNightwatch", profile.username)
        assertEquals("NIGHT-7F3K2A", profile.friendCode)
        assertTrue(profile.hideProgress)
        assertFalse(profile.toString().contains("u1"))
    }

    // ---- the username rule (the server checks it again) ----

    @Test
    fun usernameRule_isThreeToTwentyLettersDigitsOrUnderscore() {
        assertTrue(isValidUsername("valeNightwatch"))
        assertTrue(isValidUsername("abc"))
        assertTrue(isValidUsername("A_1_b_2_c_3_d_4_e_5_"))
        assertFalse(isValidUsername("ab"))
        assertFalse(isValidUsername("a".repeat(21)))
        assertFalse(isValidUsername("con espacio"))
        assertFalse(isValidUsername("tilde_ñ"))
        assertFalse(isValidUsername("guion-medio"))
        assertFalse(isValidUsername(""))
    }

    // ---- no session: stops before any request ----

    @Test
    fun withoutASession_everyCall_failsAsNoSession_beforeAnyRequest() = runTest {
        val repository = SupabaseSocialProfileRepository(unusedPostgrest(), currentUserId = { null })
        val fighter = SupabaseFighterRepository(unusedPostgrest(), currentUserId = { null })

        assertEquals(SocialResult.Failure(SocialError.NoSession), repository.getMyProfile())
        assertEquals(SocialResult.Failure(SocialError.NoSession), repository.createProfile("valeNightwatch"))
        assertEquals(SocialResult.Failure(SocialError.NoSession), repository.setHideProgress(true))
        assertEquals(SocialResult.Failure(SocialError.NoSession), fighter.getSummary())
    }

    @Test
    fun anInvalidUsername_isRejectedBeforeTheNetwork() = runTest {
        val repository = SupabaseSocialProfileRepository(unusedPostgrest(), currentUserId = { "u1" })

        val expected = SocialResult.Failure(SocialError.Rejected(SocialRejection.InvalidUsername))
        assertEquals(expected, repository.createProfile("ab"))
        assertEquals(expected, repository.createProfile("con espacio"))
    }

    // ---- the fighter summary: only adds up what the server decided ----

    private fun result(id: String, outcome: String, xp: Int, finishedAt: String?) =
        BattleResultRow(id, "u1", outcome, xp, 50, "mateo_ns", finishedAt)

    private val curve = (1..20).map { LevelCurveRow(it, (it - 1) * 100) }

    @Test
    fun summary_addsUpXpAndScore_andFindsTheLevelInTheCurve() {
        val rows = listOf(
            result("b3", "win", 50, "2026-10-21T15:00:00+00:00"),
            result("b2", "loss", 10, "2026-10-21T14:00:00+00:00"),
            result("b1", "win", 50, "2026-10-21T13:00:00+00:00"),
        )

        val summary = buildFighterSummary(rows, curve)

        assertEquals(110, summary.xp)
        assertEquals(2, summary.level)
        assertEquals(200, summary.nextLevelXp)
        assertEquals(2, summary.wins)
        assertEquals(1, summary.losses)
        assertEquals(0, summary.draws)
        assertEquals(listOf("b3", "b2", "b1"), summary.history.map { it.battleId })
        assertEquals(Instant.parse("2026-10-21T15:00:00Z"), summary.history[0].finishedAt)
        assertEquals("mateo_ns", summary.history[0].opponentUsername)
    }

    @Test
    fun summary_levelFollowsTheCurve_andStopsAtTwenty() {
        assertEquals(1, buildFighterSummary(emptyList(), curve).level)
        assertEquals(1, buildFighterSummary(listOf(result("b", "win", 99, null)), curve).level)
        assertEquals(2, buildFighterSummary(listOf(result("b", "win", 100, null)), curve).level)
        val top = buildFighterSummary(listOf(result("b", "win", 5_000, null)), curve)
        assertEquals(20, top.level)
        assertNull(top.nextLevelXp)
    }

    @Test
    fun summary_withoutACurve_isLevelOne() {
        val summary = buildFighterSummary(listOf(result("b", "win", 500, null)), emptyList())

        assertEquals(1, summary.level)
        assertNull(summary.nextLevelXp)
    }

    @Test
    fun summary_battleStreak_countsWinsFromTheLatest_aDrawNeitherBreaksNorAdds() {
        // Newest first: win, draw, win, loss, win -> the streak is 2 (the draw is skipped, the loss stops it).
        val rows = listOf(
            result("5", "win", 50, null),
            result("4", "draw", 25, null),
            result("3", "win", 50, null),
            result("2", "loss", 10, null),
            result("1", "win", 50, null),
        )

        assertEquals(2, buildFighterSummary(rows, curve).battleStreak)
        assertEquals(0, buildFighterSummary(listOf(result("2", "loss", 10, null), result("1", "win", 50, null)), curve).battleStreak)
        assertEquals(1, buildFighterSummary(listOf(result("2", "draw", 25, null), result("1", "win", 50, null)), curve).battleStreak)
    }

    @Test
    fun summary_dropsAnUnknownResult_andKeepsAnUnreadableDateOutOfTheEntry() {
        val rows = listOf(result("b2", "victoria_rara", 999, null), result("b1", "win", 50, "no es fecha"))

        val summary = buildFighterSummary(rows, curve)

        assertEquals(50, summary.xp)
        assertEquals(1, summary.history.size)
        assertEquals(BattleOutcome.Win, summary.history[0].outcome)
        assertNull(summary.history[0].finishedAt)
    }

    @Test
    fun summary_keepsOnlyTheLatestBattlesInTheHistory_butCountsThemAll() {
        val rows = (1..60).map { result("b$it", "win", 50, null) }

        val summary = buildFighterSummary(rows, curve)

        assertEquals(FIGHTER_HISTORY_LIMIT, summary.history.size)
        assertEquals(60, summary.wins)
        assertEquals(3_000, summary.xp)
    }
}
