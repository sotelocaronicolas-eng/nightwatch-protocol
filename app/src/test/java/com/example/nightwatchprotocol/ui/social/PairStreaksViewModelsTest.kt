package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.common.FailureKind
import com.example.nightwatchprotocol.data.learning.FakeLearningProgressRepository
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.social.FakeFriendsRepository
import com.example.nightwatchprotocol.data.social.FakePairStreakRepository
import com.example.nightwatchprotocol.data.social.Friend
import com.example.nightwatchprotocol.data.social.PairMetrics
import com.example.nightwatchprotocol.data.social.PairState
import com.example.nightwatchprotocol.data.social.PairStatus
import com.example.nightwatchprotocol.data.social.PairStreak
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialRejection
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PairStreaksViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val pairs = FakePairStreakRepository().apply {
        friends["NIGHT-9H4M5P"] = "mateo"
        friends["NIGHT-AAAAAA"] = "sofia"
    }
    private val friends = FakeFriendsRepository().apply {
        this.friends += Friend("mateo", "NIGHT-9H4M5P", 0, false)
        this.friends += Friend("sofia", "NIGHT-AAAAAA", 2, true)
    }
    private val progress = FakeLearningProgressRepository()

    private fun listViewModel() = PairStreaksViewModel(pairs, friends)

    // ---- list ----

    @Test
    fun list_showsThePairsAndOnlyOffersFriendsWithoutAPair() = runTest {
        pairs.pairs += FakePairStreakRepository.activePair("mateo", "NIGHT-9H4M5P", 3)
        val vm = listViewModel()

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf("mateo"), state.pairs!!.map { it.username })
        assertEquals(listOf("sofia"), state.inviteCandidates.map { it.username })
        assertFalse(state.loading)
    }

    @Test
    fun list_withoutPairs_isAnEmptyList_notAnError() = runTest {
        val vm = listViewModel()

        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pairs!!.isEmpty())
        assertEquals(null, vm.uiState.value.loadError)
        assertEquals(2, vm.uiState.value.inviteCandidates.size)
    }

    @Test
    fun list_aFailedRead_isAnErrorWithRetry() = runTest {
        pairs.failure = SocialError.Offline
        val vm = listViewModel()

        vm.refresh()
        advanceUntilIdle()
        assertEquals(SocialError.Offline, vm.uiState.value.loadError)
        assertEquals(null, vm.uiState.value.pairs)

        pairs.failure = null
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.pairs != null)
    }

    @Test
    fun list_aFailedReadOfTheFriends_doesNotBreakThePairs() = runTest {
        pairs.pairs += FakePairStreakRepository.activePair("mateo", "NIGHT-9H4M5P", 3)
        friends.failure = SocialError.Offline
        val vm = listViewModel()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.pairs!!.size)
        assertEquals(null, vm.uiState.value.loadError)
        assertTrue(vm.uiState.value.inviteCandidates.isEmpty())
    }

    @Test
    fun list_inviting_createsAPendingPair_andTheFriendLeavesTheCandidates() = runTest {
        val vm = listViewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.onInvite("NIGHT-9H4M5P")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(PairStatus.PendingSent, state.pairs!!.single().status)
        assertEquals(listOf("sofia"), state.inviteCandidates.map { it.username })
        assertTrue(state.busy.isEmpty())
    }

    @Test
    fun list_acceptingAnInvitation_makesTheStreakActive() = runTest {
        pairs.pairs += PairStreak("mateo", "NIGHT-9H4M5P", PairStatus.PendingReceived, Instant.parse("2026-10-24T00:00:00Z"), null)
        val vm = listViewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.onAccept("NIGHT-9H4M5P")
        advanceUntilIdle()

        assertEquals(PairStatus.Active, vm.uiState.value.pairs!!.single().status)
    }

    @Test
    fun list_decliningAndCancelling_removeThePending() = runTest {
        pairs.pairs += PairStreak("mateo", "NIGHT-9H4M5P", PairStatus.PendingReceived, null, null)
        pairs.pairs += PairStreak("sofia", "NIGHT-AAAAAA", PairStatus.PendingSent, null, null)
        val vm = listViewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.onDecline("NIGHT-9H4M5P")
        advanceUntilIdle()
        vm.onCancel("NIGHT-AAAAAA")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.pairs!!.isEmpty())
    }

    @Test
    fun list_anInvitationThatIsGone_showsItsMessage_andReadsAgain() = runTest {
        pairs.pairs += PairStreak("mateo", "NIGHT-9H4M5P", PairStatus.PendingReceived, null, null)
        val vm = listViewModel()
        vm.refresh()
        advanceUntilIdle()
        pairs.pairs.clear() // the other person cancelled it

        vm.onAccept("NIGHT-9H4M5P")
        advanceUntilIdle()

        assertEquals(SocialError.Rejected(SocialRejection.PairGone), vm.uiState.value.actionError)
        assertTrue(vm.uiState.value.pairs!!.isEmpty())
    }

    @Test
    fun list_aFailedAction_keepsTheState_andShowsTheError() = runTest {
        val vm = listViewModel()
        vm.refresh()
        advanceUntilIdle()
        pairs.failure = SocialError.RateLimited

        vm.onInvite("NIGHT-9H4M5P")
        advanceUntilIdle()

        assertEquals(SocialError.RateLimited, vm.uiState.value.actionError)
        assertTrue(vm.uiState.value.busy.isEmpty())
    }

    @Test
    fun list_aSecondTapOnTheSameFriend_isIgnored() = runTest {
        val vm = listViewModel()
        vm.refresh()
        advanceUntilIdle()
        val before = pairs.calls

        vm.onInvite("NIGHT-9H4M5P")
        vm.onInvite("NIGHT-9H4M5P")
        advanceUntilIdle()

        // one invite and one read of the list afterwards
        assertEquals(before + 2, pairs.calls)
    }

    // ---- detail ----

    private val bogotaClock = Clock.fixed(Instant.parse("2026-10-21T15:00:00Z"), ColombiaZone) // Wednesday in Bogotá

    private fun detailViewModel(code: String = "NIGHT-9H4M5P") = PairDetailViewModel(
        SavedStateHandle(mapOf(PairDetailViewModel.KEY_FRIEND_CODE to code)),
        pairs,
        progress,
        bogotaClock,
    )

    private fun activePair(
        days: Int,
        meDone: Boolean,
        partnerDone: Boolean,
        state: PairState = PairState.AtRisk,
        shared: List<LocalDate> = emptyList(),
    ) {
        pairs.pairs += PairStreak(
            "mateo", "NIGHT-9H4M5P", PairStatus.Active, null,
            PairMetrics(days, days, meDone, partnerDone, state, shared),
        )
    }

    @Test
    fun detail_showsTheServersNumbers_andPlacesTheSharedDaysOnTheWeekOfColombia() = runTest {
        activePair(2, meDone = false, partnerDone = true, shared = listOf(LocalDate.parse("2026-10-19"), LocalDate.parse("2026-10-20")))
        val vm = detailViewModel()

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("mateo", state.username)
        assertEquals(2, state.metrics!!.currentDays)
        assertEquals(7, state.week.size)
        assertEquals(LocalDate.parse("2026-10-19"), state.week.first().date) // Monday
        assertEquals(listOf(true, true, false, false, false, false, false), state.week.map { it.shared })
        assertEquals(listOf(false, false, true, false, false, false, false), state.week.map { it.today })
    }

    @Test
    fun detail_theDayIsTheOneInColombia_notTheOneOfThePhone() = runTest {
        // 02:00 UTC on Thursday is still Wednesday night in Colombia.
        val late = Clock.fixed(Instant.parse("2026-10-22T02:00:00Z"), ColombiaZone)
        activePair(0, meDone = false, partnerDone = false, state = PairState.None)
        val vm = PairDetailViewModel(
            SavedStateHandle(mapOf(PairDetailViewModel.KEY_FRIEND_CODE to "NIGHT-9H4M5P")), pairs, progress, late,
        )

        vm.refresh()
        advanceUntilIdle()

        assertEquals(LocalDate.parse("2026-10-21"), vm.uiState.value.week.single { it.today }.date)
    }

    @Test
    fun detail_theExerciseButton_opensTheLessonOfTheNextIsland() = runTest {
        activePair(1, meDone = false, partnerDone = true)
        val vm = detailViewModel()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(TodayExercise.OpenLesson(LearningTopic.CONTRASENAS), vm.uiState.value.exercise)
    }

    @Test
    fun detail_withTheLessonSeen_theButtonOpensTheLab() = runTest {
        progress.viewedLessons += LearningTopic.CONTRASENAS
        activePair(1, meDone = false, partnerDone = true)
        val vm = detailViewModel()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(TodayExercise.OpenLab(LearningTopic.CONTRASENAS), vm.uiState.value.exercise)
    }

    @Test
    fun detail_withTheSixIslandsDone_nothingIsOffered() = runTest {
        LearningTopic.entries.forEach {
            progress.viewedLessons += it
            progress.completedLabs += it
        }
        activePair(1, meDone = false, partnerDone = true, state = PairState.RouteComplete)
        val vm = detailViewModel()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(TodayExercise.AllDone, vm.uiState.value.exercise)
    }

    @Test
    fun detail_ifTheProgressCannotBeRead_theButtonIsOffAndThePairStillShows() = runTest {
        progress.readFailure = FailureKind.Offline
        activePair(1, meDone = false, partnerDone = true)
        val vm = detailViewModel()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(TodayExercise.Unknown, vm.uiState.value.exercise)
        assertEquals(1, vm.uiState.value.metrics!!.currentDays)
    }

    @Test
    fun detail_aPairThatDoesNotExistAnyMore_goesBack() = runTest {
        val vm = detailViewModel("NIGHT-ZZZZZZ")

        vm.events.test {
            vm.refresh()
            advanceUntilIdle()
            // not found as a pair: the list is the place to see it
            assertEquals(SocialError.Rejected(SocialRejection.PairGone), vm.uiState.value.loadError)
            expectNoEvents()
        }
    }

    @Test
    fun detail_aPairThatIsOnlyAnInvitation_goesBackToTheList() = runTest {
        pairs.pairs += PairStreak("mateo", "NIGHT-9H4M5P", PairStatus.PendingSent, null, null)
        val vm = detailViewModel()

        vm.events.test {
            vm.refresh()
            advanceUntilIdle()
            assertEquals(PairDetailEvent.Gone, awaitItem())
        }
    }

    @Test
    fun detail_ending_needsConfirmation_andThenGoesBack() = runTest {
        activePair(1, meDone = true, partnerDone = true, state = PairState.Active)
        val vm = detailViewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onEndClicked()
            assertTrue(vm.uiState.value.confirmingEnd)
            assertEquals(1, pairs.pairs.size)

            vm.onEndConfirmed()
            advanceUntilIdle()

            assertEquals(PairDetailEvent.Gone, awaitItem())
        }
        assertTrue(pairs.pairs.isEmpty())
    }

    @Test
    fun detail_cancellingTheConfirmation_endsNothing() = runTest {
        activePair(1, meDone = true, partnerDone = true, state = PairState.Active)
        val vm = detailViewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.onEndClicked()
        vm.onEndDismissed()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.confirmingEnd)
        assertEquals(1, pairs.pairs.size)
    }

    @Test
    fun detail_aFailedEnd_staysWithTheError() = runTest {
        activePair(1, meDone = true, partnerDone = true, state = PairState.Active)
        val vm = detailViewModel()
        vm.refresh()
        advanceUntilIdle()
        pairs.failure = SocialError.Offline

        vm.events.test {
            vm.onEndClicked()
            vm.onEndConfirmed()
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(SocialError.Offline, vm.uiState.value.actionError)
        assertFalse(vm.uiState.value.ending)
    }

    // ---- invitation from the profile of a friend ----

    @Test
    fun profile_invitingToAPair_sendsItOnce() = runTest {
        friends.friends.clear()
        friends.friends += Friend("mateo", "NIGHT-9H4M5P", 0, false)
        val profile = FriendProfileViewModel(
            SavedStateHandle(mapOf(FriendProfileViewModel.KEY_FRIEND_CODE to "NIGHT-9H4M5P")), friends, pairs,
        )

        profile.onInvitePairClicked()
        profile.onInvitePairClicked()
        advanceUntilIdle()

        assertTrue(profile.uiState.value.pairInviteSent)
        assertEquals(1, pairs.pairs.size)
        assertEquals(PairStatus.PendingSent, pairs.pairs.single().status)
    }

    @Test
    fun profile_invitingWhenThereIsAlreadyAPair_showsTheMessage() = runTest {
        pairs.pairs += FakePairStreakRepository.activePair("mateo", "NIGHT-9H4M5P", 1)
        val profile = FriendProfileViewModel(
            SavedStateHandle(mapOf(FriendProfileViewModel.KEY_FRIEND_CODE to "NIGHT-9H4M5P")), friends, pairs,
        )

        profile.onInvitePairClicked()
        advanceUntilIdle()

        assertFalse(profile.uiState.value.pairInviteSent)
        assertEquals(SocialError.Rejected(SocialRejection.PairExists), profile.uiState.value.actionError)
    }
}
