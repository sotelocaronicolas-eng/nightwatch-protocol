package com.example.nightwatchprotocol.data.social

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The social profile of `0012_perfil_social.sql`: the background, the eyes, the totals and how an older server looks. */
class ProfileDisplayDataTest {

    private fun <T> SocialResult<T>.value(): T = (this as SocialResult.Success).value

    private fun profileJson(extra: String) = """
        {"username":"valeNightwatch","friend_code":"NIGHT-7F3K2A","streak":2,"trained_today":true,"hidden":false,
         "progress":null,$extra}
    """.trimIndent()

    @Test
    fun friendProfile_with0012_readsTheBackgroundTheEyesAndTheStats() {
        val profile = parseFriendProfile(
            json(
                profileJson(
                    """
                    "history":[{"result":"win","finished_at":"2026-10-21T15:30:00+00:00"}],
                    "profile_bg":3,"show_badges":false,"show_stats":true,"show_history":true,
                    "stats":{"played":18,"wins":9,"losses":6,"draws":3}
                    """,
                ),
            ),
        ).value()

        assertTrue(profile.extrasAvailable)
        assertEquals(ProfileDisplaySettings(profileBg = 3, showBadges = false), profile.display)
        assertEquals(BattleStats(played = 18, wins = 9, losses = 6, draws = 3), profile.stats)
        assertEquals(1, profile.history.size)
    }

    @Test
    fun friendProfile_omittedSections_areAbsentKeys_notEmptyData() {
        val profile = parseFriendProfile(
            json(profileJson(""""profile_bg":0,"show_badges":true,"show_stats":false,"show_history":false""")),
        ).value()

        assertTrue(profile.extrasAvailable)
        assertFalse(profile.display.showStats)
        assertFalse(profile.display.showHistory)
        assertNull(profile.stats)
        assertEquals(emptyList<FriendBattleEntry>(), profile.history)
    }

    @Test
    fun friendProfile_fromAServerWithout0012_usesTheDefaults_andSaysSo() {
        val profile = parseFriendProfile(json(FRIEND_PROFILE_VISIBLE)).value()

        assertFalse(profile.extrasAvailable)
        assertEquals(ProfileDisplaySettings(), profile.display)
        assertNull(profile.stats)
        // What the old server did send is still read.
        assertEquals(2, profile.history.size)
    }

    @Test
    fun friendProfile_anUnknownBackground_isNoBackground() {
        val profile = parseFriendProfile(json(profileJson(""""history":[],"profile_bg":9"""))).value()

        assertEquals(0, profile.display.profileBg)
        assertTrue(profile.extrasAvailable)
    }

    @Test
    fun friendProfile_unreadableStats_doNotFailTheProfile() {
        val profile = parseFriendProfile(
            json(profileJson(""""history":[],"profile_bg":1,"stats":{"played":"muchas"}""")),
        ).value()

        assertNull(profile.stats)
        assertEquals("valeNightwatch", profile.username)
    }

    @Test
    fun normalizeProfileBg_keepsZeroToFive_andSendsTheRestToZero() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5), (0..5).map { normalizeProfileBg(it) })
        assertEquals(0, normalizeProfileBg(6))
        assertEquals(0, normalizeProfileBg(-1))
        assertEquals(0, normalizeProfileBg(null))
    }

    @Test
    fun winrate_isWinsOverPlayedRounded_andNeverDividesByZero() {
        assertNull(BattleStats(0, 0, 0, 0).winratePercent)
        assertEquals(50, BattleStats(18, 9, 6, 3).winratePercent)
        assertEquals(67, BattleStats(3, 2, 1, 0).winratePercent)
        assertEquals(33, BattleStats(3, 1, 2, 0).winratePercent)
        assertEquals(100, BattleStats(1, 1, 0, 0).winratePercent)
        assertEquals(0, BattleStats(4, 0, 3, 1).winratePercent)
    }

    @Test
    fun fighterSummary_givesTheOwnTotals() {
        val summary = FighterSummary(xp = 0, level = 1, nextLevelXp = 100, wins = 4, losses = 2, draws = 1, battleStreak = 0, history = emptyList())

        assertEquals(BattleStats(7, 4, 2, 1), summary.toBattleStats())
    }

    @Test
    fun fakeProfileRepository_savesTheFourChoicesTogether() = runTest {
        val repository = FakeSocialProfileRepository(SocialProfile("valeNightwatch", "NIGHT-7F3K2A", hideProgress = false))
        val choices = ProfileDisplaySettings(profileBg = 2, showBadges = false, showStats = true, showHistory = false)

        assertTrue(repository.updateDisplaySettings(choices) is SocialResult.Success)

        assertEquals(choices, repository.profile!!.display)
        assertEquals(1, repository.displayCalls)
    }

    @Test
    fun fakeProfileRepository_aServerWithout0012_failsAsUnavailable() = runTest {
        val repository = FakeSocialProfileRepository(SocialProfile("valeNightwatch", "NIGHT-7F3K2A", hideProgress = false))
        repository.failure = SocialError.Unavailable

        val result = repository.updateDisplaySettings(ProfileDisplaySettings(profileBg = 1))

        assertEquals(SocialError.Unavailable, (result as SocialResult.Failure).error)
        assertEquals(ProfileDisplaySettings(), repository.profile!!.display)
    }
}
