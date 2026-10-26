package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.SavedStateHandle
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.learning.BadgeFacts
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.social.BattleOutcome
import com.example.nightwatchprotocol.data.social.BattleStats
import com.example.nightwatchprotocol.data.social.FakeBattleLobbyRepository
import com.example.nightwatchprotocol.data.social.FakeFriendsRepository
import com.example.nightwatchprotocol.data.social.FakePairStreakRepository
import com.example.nightwatchprotocol.data.social.Friend
import com.example.nightwatchprotocol.data.social.FriendBattleEntry
import com.example.nightwatchprotocol.data.social.FriendProfile
import com.example.nightwatchprotocol.data.social.FriendProgress
import com.example.nightwatchprotocol.data.social.ProfileDisplaySettings
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** "Perfil amigo" with the fields of `0012`: the background, the eyes, the stats and the order of the sections. */
@OptIn(ExperimentalCoroutinesApi::class)
class FriendProfileSectionsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val friends = FakeFriendsRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC)
    private val code = "NIGHT-VVVVVV"

    private fun viewModel() = FriendProfileViewModel(
        SavedStateHandle(mapOf(FriendProfileViewModel.KEY_FRIEND_CODE to code)),
        friends,
        FakePairStreakRepository(),
        FakeBattleLobbyRepository(),
        clock,
    )

    private val facts = BadgeFacts(
        viewedLessons = setOf(LearningTopic.CONTRASENAS),
        completedLabs = setOf(LearningTopic.CONTRASENAS),
        examPassed = false,
        examPerfect = false,
        bestStreak = 2,
    )
    private val progress = FriendProgress(fighterLevel = 4, viewedCount = 1, labsCount = 1, badgeFacts = facts)

    private fun battles(count: Int) = List(count) { FriendBattleEntry(BattleOutcome.Win, Instant.parse("2026-10-19T10:00:00Z")) }

    private fun addFriend(
        display: ProfileDisplaySettings = ProfileDisplaySettings(),
        stats: BattleStats? = BattleStats(15, 10, 4, 1),
        history: List<FriendBattleEntry> = battles(3),
        hidden: Boolean = false,
        progress: FriendProgress? = this.progress,
        extrasAvailable: Boolean = true,
    ) {
        friends.friends += Friend("valentina", code, 2, trainedToday = true)
        friends.profiles[code] = FriendProfile(
            username = "valentina",
            friendCode = code,
            streakDays = 2,
            trainedToday = true,
            hidden = hidden,
            // What the server sends: nothing when hidden; the counters always; the badge facts only with the eye open.
            progress = progress?.takeUnless { hidden }?.let { if (display.showBadges) it else it.copy(badgeFacts = null) },
            history = history,
            display = display,
            stats = stats.takeIf { display.showStats },
            extrasAvailable = extrasAvailable,
        )
    }

    private suspend fun kotlinx.coroutines.test.TestScope.loaded(): FriendProfileUiState {
        val vm = viewModel()
        vm.refresh()
        advanceUntilIdle()
        return vm.uiState.value
    }

    @Test
    fun theBackgroundAndTheStats_comeFromTheFriend() = runTest {
        addFriend(display = ProfileDisplaySettings(profileBg = 4))

        val state = loaded()

        assertEquals(4, state.profileBg)
        assertEquals(BattleStats(15, 10, 4, 1), state.stats)
        assertEquals(67, state.stats!!.winratePercent)
        assertTrue(state.extrasAvailable)
    }

    @Test
    fun withoutBattles_theWinrateIsNotCalculated() = runTest {
        addFriend(stats = BattleStats(0, 0, 0, 0))

        val state = loaded()

        assertEquals(BattleStats(0, 0, 0, 0), state.stats)
        assertNull(state.stats!!.winratePercent)
    }

    @Test
    fun allTheSectionsOpen_areDrawnInTheOrderOfTheDesign() = runTest {
        addFriend()

        val content = loaded().toProfileContent(hiddenNotice = "oculta")

        assertEquals(4, content.fighterLevel)
        assertEquals(1, content.exercises)
        assertEquals(1, content.labs)
        assertNotNull(content.badges)
        assertNotNull(content.stats)
        assertNotNull(content.history)
        assertNull(content.hiddenNotice)
    }

    @Test
    fun theClosedEyeOfTheStats_leavesThemOut_andTheRestStays() = runTest {
        addFriend(display = ProfileDisplaySettings(showStats = false))

        val state = loaded()
        val content = state.toProfileContent("oculta")

        assertFalse(state.showStats)
        assertNull(content.stats)
        assertNotNull(content.badges)
        assertEquals(3, content.history!!.size)
    }

    @Test
    fun theClosedEyeOfTheHistory_leavesItOut_butNotAnEmptyHistory() = runTest {
        addFriend(display = ProfileDisplaySettings(showHistory = false), history = emptyList())
        val hiddenHistory = loaded().toProfileContent("oculta")

        assertNull(hiddenHistory.history)
        assertNotNull(hiddenHistory.stats)
    }

    @Test
    fun anOpenHistoryWithoutBattles_isAnEmptyListNotNull() = runTest {
        addFriend(history = emptyList())

        val content = loaded().toProfileContent("oculta")

        assertEquals(emptyList<ProfileBattleRow>(), content.history)
    }

    @Test
    fun theClosedEyeOfTheBadges_leavesOutOnlyTheBadges_andKeepsTheLevelAndTheCounters() = runTest {
        addFriend(display = ProfileDisplaySettings(showBadges = false))

        val content = loaded().toProfileContent("oculta")

        assertNull(content.badges)
        assertEquals(4, content.fighterLevel)
        assertEquals(1, content.exercises)
        assertEquals(1, content.labs)
        assertNull(content.hiddenNotice)
        assertNotNull(content.stats)
        assertNotNull(content.history)
    }

    @Test
    fun theBadgesAreNotShown_whenTheFactsDoNotCome_evenWithTheEyeOpen() = runTest {
        addFriend(progress = progress.copy(badgeFacts = null))

        val state = loaded()
        val content = state.toProfileContent("oculta")

        assertTrue(state.showBadges)
        assertNull(state.unlockedBadges)
        assertNull(content.badges)
        assertEquals(4, content.fighterLevel)
        assertEquals(1, content.exercises)
    }

    @Test
    fun anOlderServerWithoutCounters_hidesTheCountersButKeepsTheLevel() = runTest {
        addFriend(progress = progress.copy(viewedCount = null, labsCount = null, badgeFacts = null))

        val content = loaded().toProfileContent("oculta")

        assertEquals(4, content.fighterLevel)
        assertNull(content.exercises)
        assertNull(content.labs)
        assertNull(content.badges)
    }

    @Test
    fun hidingTheProgress_hidesEverythingOfIt_evenWithTheBadgesEyeOpen_butNotTheStatsOrTheHistory() = runTest {
        addFriend(hidden = true, display = ProfileDisplaySettings(showBadges = true))

        val content = loaded().toProfileContent("oculta")

        assertNull(content.badges)
        assertNull(content.fighterLevel)
        assertNull(content.exercises)
        assertNull(content.labs)
        assertEquals("oculta", content.hiddenNotice)
        assertNotNull(content.stats)
        assertEquals(3, content.history!!.size)
    }

    @Test
    fun theHistory_showsAtMostTheLastThree_withTheFriendAsRival() = runTest {
        addFriend(history = battles(5))

        val content = loaded().toProfileContent("oculta")

        assertEquals(3, content.history!!.size)
        assertEquals("valentina", content.history!!.first().rival)
        assertEquals(2, content.history!!.first().daysAgo)
    }

    @Test
    fun aServerWithout0012_keepsTheProfile_andSaysTheExtrasAreNotAvailable() = runTest {
        addFriend(stats = null, extrasAvailable = false)

        val state = loaded()
        val content = state.toProfileContent("oculta")

        assertTrue(state.loaded)
        assertFalse(state.extrasAvailable)
        assertEquals(0, state.profileBg)
        assertNull(content.stats)
        assertNotNull(content.badges)
        assertEquals("valentina", content.username)
    }
}
