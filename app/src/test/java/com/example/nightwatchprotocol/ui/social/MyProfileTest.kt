package com.example.nightwatchprotocol.ui.social

import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.learning.BadgeFacts
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.learning.ProgressSummary
import com.example.nightwatchprotocol.data.learning.Streak
import com.example.nightwatchprotocol.data.learning.evaluateBadges
import com.example.nightwatchprotocol.data.social.BattleHistoryEntry
import com.example.nightwatchprotocol.data.social.BattleOutcome
import com.example.nightwatchprotocol.data.social.BattleStats
import com.example.nightwatchprotocol.data.social.FakeFighterRepository
import com.example.nightwatchprotocol.data.social.FakeSocialProfileRepository
import com.example.nightwatchprotocol.data.social.FighterSummary
import com.example.nightwatchprotocol.data.social.ProfileDisplaySettings
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialProfile
import com.example.nightwatchprotocol.data.social.SocialResult
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

/** "Tu perfil": the loader that reads the three sources, the sections it draws and the view model that holds them. */
@OptIn(ExperimentalCoroutinesApi::class)
class MyProfileTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC)
    private val profiles = FakeSocialProfileRepository(SocialProfile("Juan", "NIGHT-JUAN22", hideProgress = false))
    private val fighter = FakeFighterRepository(fighterSummary())
    private var progress: suspend () -> ProgressSummary = { progressSummary() }

    private fun loader() = MyProfileLoader(profiles, fighter, { progress() }, clock)

    private fun entry(outcome: BattleOutcome, rival: String, finished: String?) =
        BattleHistoryEntry("b-$rival-$finished", outcome, 10, 50, rival, finished?.let { Instant.parse(it) })

    private fun fighterSummary(history: List<BattleHistoryEntry> = defaultHistory()) = FighterSummary(
        xp = 120, level = 2, nextLevelXp = 200, wins = 10, losses = 4, draws = 1, battleStreak = 1, history = history,
    )

    private fun defaultHistory() = listOf(
        entry(BattleOutcome.Win, "Mateo", "2026-10-23T10:00:00Z"),
        entry(BattleOutcome.Loss, "Sofía", "2026-10-19T10:00:00Z"),
        entry(BattleOutcome.Draw, "Diego", null),
        entry(BattleOutcome.Win, "Ana", "2026-10-01T10:00:00Z"),
        entry(BattleOutcome.Win, "Luis", "2026-09-01T10:00:00Z"),
    )

    private fun progressSummary(trainedToday: Boolean = true) = ProgressSummary(
        streak = Streak(current = 2, best = 5),
        badges = evaluateBadges(BadgeFacts(setOf(LearningTopic.CONTRASENAS), setOf(LearningTopic.CONTRASENAS), false, false, 5)),
        trainedToday = trainedToday,
        viewedLessonCount = 47,
        completedLabCount = 5,
    )

    private suspend fun loadData(): MyProfileData = (loader().load() as SocialResult.Success).value

    @Test
    fun theLoader_readsTheLevelTheCountersTheStreakAndTheBadges() = runTest {
        val data = loadData()

        assertEquals("Juan", data.username)
        assertEquals(2, data.fighterLevel)
        assertEquals(47, data.exercises)
        assertEquals(5, data.labs)
        assertEquals(2, data.streakDays)
        assertTrue(data.trainedToday)
        assertTrue(data.badges!!.isNotEmpty())
        assertTrue(data.badges!!.all { it.unlocked })
        assertNull(data.partialError)
    }

    @Test
    fun theStats_areTheTotalsOfEveryBattleAgainstAnyone() = runTest {
        val data = loadData()

        assertEquals(BattleStats(played = 15, wins = 10, losses = 4, draws = 1), data.stats)
        assertEquals(67, data.stats!!.winratePercent)
    }

    @Test
    fun withoutBattles_theWinrateIsNull_notZeroDividedByZero() = runTest {
        fighter.summary = FighterSummary(0, 1, 100, 0, 0, 0, 0, emptyList())

        val data = loadData()

        assertEquals(BattleStats(0, 0, 0, 0), data.stats)
        assertNull(data.stats!!.winratePercent)
        assertEquals(emptyList<ProfileBattleRow>(), data.history)
    }

    @Test
    fun theHistory_isTheLastThreeAgainstAnyRival_withHowLongAgo() = runTest {
        val data = loadData()

        assertEquals(
            listOf(
                ProfileBattleRow(BattleOutcome.Win, "Mateo", 2),
                ProfileBattleRow(BattleOutcome.Loss, "Sofía", 6),
                ProfileBattleRow(BattleOutcome.Draw, "Diego", null),
            ),
            data.history,
        )
    }

    @Test
    fun withAllTheEyesOpen_everySectionIsDrawn() = runTest {
        val content = loadData().toProfileContent()

        assertEquals(2, content.fighterLevel)
        assertNotNull(content.badges)
        assertNotNull(content.stats)
        assertNotNull(content.history)
        assertTrue(content.isOwn)
    }

    @Test
    fun aClosedEye_leavesTheSectionOut_andTheOthersStay() = runTest {
        val data = loadData()

        val noStats = data.toProfileContent(ProfileDisplaySettings(showStats = false))
        assertNull(noStats.stats)
        assertNotNull(noStats.badges)
        assertNotNull(noStats.history)

        val noHistory = data.toProfileContent(ProfileDisplaySettings(showHistory = false))
        assertNull(noHistory.history)
        assertNotNull(noHistory.stats)

        val noBadges = data.toProfileContent(ProfileDisplaySettings(showBadges = false))
        assertNull(noBadges.badges)
        assertNull(noBadges.exercises)
        assertNull(noBadges.labs)
        assertNull(noBadges.fighterLevel)
        assertNotNull(noBadges.stats)
    }

    @Test
    fun theEditor_drawsEverySection_evenTheClosedOnes() = runTest {
        val content = loadData().toProfileContent(ProfileDisplaySettings(showBadges = false, showStats = false, showHistory = false), forEditor = true)

        assertNotNull(content.badges)
        assertNotNull(content.stats)
        assertNotNull(content.history)
        assertEquals(47, content.exercises)
    }

    @Test
    fun hidingTheProgressForOthers_doesNotHideTheOwnProfile() = runTest {
        profiles.profile = profiles.profile!!.copy(hideProgress = true)

        val content = loadData().toProfileContent()

        assertNotNull(content.badges)
        assertEquals(47, content.exercises)
    }

    @Test
    fun aFailedBattleRead_keepsTheProfile_withoutStatsAndHistory_andReportsTheError() = runTest {
        fighter.failure = SocialError.Offline

        val data = loadData()

        assertEquals("Juan", data.username)
        assertNull(data.stats)
        assertNull(data.history)
        assertNull(data.fighterLevel)
        assertEquals(47, data.exercises)
        assertEquals(SocialError.Offline, data.partialError)
    }

    @Test
    fun aFailedProgressRead_keepsTheProfile_withoutTheProgressBlock() = runTest {
        progress = { throw IllegalStateException("sin conexión") }

        val data = loadData()

        assertNull(data.badges)
        assertNull(data.exercises)
        assertEquals(10, data.stats!!.wins)
        assertEquals(SocialError.Unknown, data.partialError)
    }

    @Test
    fun withoutASocialProfile_theLoadFails() = runTest {
        profiles.profile = null

        assertEquals(SocialError.NotEnabled, (loader().load() as SocialResult.Failure).error)
    }

    @Test
    fun theViewModel_loads_andAFailedReRead_keepsTheLastData() = runTest {
        val vm = MyProfileViewModel(loader())

        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.loaded)
        assertFalse(vm.uiState.value.loading)
        assertEquals("Juan", vm.uiState.value.data!!.username)

        profiles.failure = SocialError.Offline
        vm.refresh()
        advanceUntilIdle()

        assertEquals(SocialError.Offline, vm.uiState.value.loadError)
        assertEquals("Juan", vm.uiState.value.data!!.username)
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun theViewModel_whileLoadingTheFirstTime_isLoadingWithoutData() = runTest {
        profiles.failure = SocialError.Offline
        val vm = MyProfileViewModel(loader())

        vm.refresh()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loaded)
        assertEquals(SocialError.Offline, vm.uiState.value.loadError)
    }
}
