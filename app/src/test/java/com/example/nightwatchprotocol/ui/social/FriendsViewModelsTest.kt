package com.example.nightwatchprotocol.ui.social

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.learning.BadgeFacts
import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.data.social.BattleOutcome
import com.example.nightwatchprotocol.data.social.FakeBattleLobbyRepository
import com.example.nightwatchprotocol.data.social.FakeFriendsRepository
import com.example.nightwatchprotocol.data.social.FakePairStreakRepository
import com.example.nightwatchprotocol.data.social.FakeSocialProfileRepository
import com.example.nightwatchprotocol.data.social.Friend
import com.example.nightwatchprotocol.data.social.FriendBattleEntry
import com.example.nightwatchprotocol.data.social.FriendProfile
import com.example.nightwatchprotocol.data.social.FriendProgress
import com.example.nightwatchprotocol.data.social.FriendRelation
import com.example.nightwatchprotocol.data.social.PersonRef
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialProfile
import com.example.nightwatchprotocol.data.social.SocialRejection
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

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val friends = FakeFriendsRepository().apply {
        addPerson("mateo", "NIGHT-9H4M5P")
        addPerson("sofia", "NIGHT-AAAAAA")
        addPerson("valeNightwatch", "NIGHT-7F3K2A")
    }
    private val profiles = FakeSocialProfileRepository(SocialProfile("yo", "NIGHT-MINE22", hideProgress = false))
    private val pairs = FakePairStreakRepository()
    private val lobby = FakeBattleLobbyRepository()

    // ---- list ----

    @Test
    fun theList_showsTheFriends_andCountsWhoTrainedToday() = runTest {
        friends.friends += Friend("valentina", "NIGHT-VVVVVV", 12, trainedToday = true)
        friends.friends += Friend("diego", "NIGHT-DDDDDD", 0, trainedToday = false)
        val vm = FriendsViewModel(friends)

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(2, state.friends!!.size)
        assertEquals(1, state.keepingToday)
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @Test
    fun aFailedRead_keepsTheLastList_andReportsTheError() = runTest {
        friends.friends += Friend("diego", "NIGHT-DDDDDD", 0, trainedToday = false)
        val vm = FriendsViewModel(friends)
        vm.refresh()
        advanceUntilIdle()

        friends.failure = SocialError.Offline
        vm.refresh()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.friends!!.size)
        assertEquals(SocialError.Offline, vm.uiState.value.error)
    }

    @Test
    fun aFirstReadThatFails_leavesNoListAndAnError() = runTest {
        friends.failure = SocialError.Offline
        val vm = FriendsViewModel(friends)

        vm.refresh()
        advanceUntilIdle()

        assertNull(vm.uiState.value.friends)
        assertEquals(SocialError.Offline, vm.uiState.value.error)
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun aReadAlreadyRunning_isNotDuplicated() = runTest {
        val vm = FriendsViewModel(friends)

        vm.refresh()
        vm.refresh()
        advanceUntilIdle()

        assertEquals(1, friends.calls)
    }

    // ---- add ----

    private fun addViewModel() = AddFriendViewModel(friends, profiles)

    @Test
    fun add_readsTheOwnCode_andTheCountOfRequests() = runTest {
        friends.received += PersonRef("mateo", "NIGHT-9H4M5P")
        val vm = addViewModel()

        vm.refresh()
        advanceUntilIdle()

        assertEquals("NIGHT-MINE22", vm.uiState.value.myFriendCode)
        assertEquals(1, vm.uiState.value.pendingRequests)
    }

    @Test
    fun add_aQueryOutsideThreeToFortyCharacters_isNotSent() = runTest {
        val vm = addViewModel()
        vm.onQueryChanged("ab")

        vm.onSearch()
        advanceUntilIdle()

        assertEquals(SocialError.InvalidInput, vm.uiState.value.error)
        assertEquals(0, friends.calls)
        vm.onQueryChanged("x".repeat(60))
        assertEquals(40, vm.uiState.value.query.length)
    }

    @Test
    fun add_searchingByUsername_showsTheRelation() = runTest {
        val vm = addViewModel()
        vm.onQueryChanged(" MATEO ")

        vm.onSearch()
        advanceUntilIdle()

        val hit = vm.uiState.value.hit!!
        assertEquals("mateo", hit.username)
        assertEquals(FriendRelation.None, hit.relation)
        assertFalse(vm.uiState.value.searching)
    }

    @Test
    fun add_searchingByFriendCode_works() = runTest {
        val vm = addViewModel()
        vm.onQueryChanged("night-aaaaaa")

        vm.onSearch()
        advanceUntilIdle()

        assertEquals("sofia", vm.uiState.value.hit!!.username)
    }

    @Test
    fun add_aPersonThatDoesNotExist_isNotFound_andTypingClearsTheError() = runTest {
        val vm = addViewModel()
        vm.onQueryChanged("nadie")

        vm.onSearch()
        advanceUntilIdle()
        assertEquals(SocialError.Rejected(SocialRejection.NotFound), vm.uiState.value.error)
        assertNull(vm.uiState.value.hit)

        vm.onQueryChanged("nadie2")
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun add_aRateLimitedSearch_showsItsMessage() = runTest {
        friends.failure = SocialError.RateLimited
        val vm = addViewModel()
        vm.onQueryChanged("mateo")

        vm.onSearch()
        advanceUntilIdle()

        assertEquals(SocialError.RateLimited, vm.uiState.value.error)
    }

    @Test
    fun add_inviting_sendsTheRequest_andTheButtonBecomesSent() = runTest {
        val vm = addViewModel()
        vm.onQueryChanged("mateo")
        vm.onSearch()
        advanceUntilIdle()

        vm.onInvite()
        advanceUntilIdle()

        assertEquals(listOf("NIGHT-9H4M5P"), friends.sent.map { it.friendCode })
        assertEquals(FriendRelation.Sent, vm.uiState.value.hit!!.relation)
        assertEquals(AddFriendNotice.Invited("mateo"), vm.uiState.value.notice)
        assertFalse(vm.uiState.value.working)
    }

    @Test
    fun add_aSecondInvite_isIgnored() = runTest {
        val vm = addViewModel()
        vm.onQueryChanged("mateo")
        vm.onSearch()
        advanceUntilIdle()

        vm.onInvite()
        vm.onInvite()
        advanceUntilIdle()

        assertEquals(1, friends.sent.size)
    }

    @Test
    fun add_ifTheServerSaysTheyAreAlreadyFriends_theButtonStopsOfferingAnInvite() = runTest {
        val vm = addViewModel()
        vm.onQueryChanged("mateo")
        vm.onSearch()
        advanceUntilIdle()
        // They became friends after the search (on another phone).
        friends.friends += Friend("mateo", "NIGHT-9H4M5P", 0, false)

        vm.onInvite()
        advanceUntilIdle()

        assertEquals(FriendRelation.Friends, vm.uiState.value.hit!!.relation)
        assertEquals(SocialError.Rejected(SocialRejection.AlreadyFriends), vm.uiState.value.error)
    }

    @Test
    fun add_aPersonWhoAlreadyInvitedMe_canBeAccepted() = runTest {
        friends.received += PersonRef("sofia", "NIGHT-AAAAAA")
        val vm = addViewModel()
        vm.refresh()
        vm.onQueryChanged("sofia")
        vm.onSearch()
        advanceUntilIdle()
        assertEquals(FriendRelation.Received, vm.uiState.value.hit!!.relation)

        vm.onInvite() // does nothing: the relation is not "none"
        vm.onAcceptIncoming()
        advanceUntilIdle()

        assertEquals(FriendRelation.Friends, vm.uiState.value.hit!!.relation)
        assertEquals(AddFriendNotice.Accepted("sofia"), vm.uiState.value.notice)
        assertEquals(0, vm.uiState.value.pendingRequests)
        assertEquals(listOf("sofia"), friends.friends.map { it.username })
    }

    // ---- requests ----

    @Test
    fun requests_showReceivedAndSent() = runTest {
        friends.received += PersonRef("mateo", "NIGHT-9H4M5P")
        friends.sent += PersonRef("sofia", "NIGHT-AAAAAA")
        val vm = FriendRequestsViewModel(friends)

        vm.refresh()
        advanceUntilIdle()

        val requests = vm.uiState.value.requests!!
        assertEquals(listOf("mateo"), requests.received.map { it.username })
        assertEquals(listOf("sofia"), requests.sent.map { it.username })
    }

    @Test
    fun requests_accepting_makesAFriend_andTheRowLeaves() = runTest {
        friends.received += PersonRef("mateo", "NIGHT-9H4M5P")
        val vm = FriendRequestsViewModel(friends)
        vm.refresh()
        advanceUntilIdle()

        vm.onAccept("NIGHT-9H4M5P")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.requests!!.received.isEmpty())
        assertEquals(listOf("mateo"), friends.friends.map { it.username })
        assertTrue(vm.uiState.value.busy.isEmpty())
    }

    @Test
    fun requests_declining_andCancelling_removeTheRows() = runTest {
        friends.received += PersonRef("mateo", "NIGHT-9H4M5P")
        friends.sent += PersonRef("sofia", "NIGHT-AAAAAA")
        val vm = FriendRequestsViewModel(friends)
        vm.refresh()
        advanceUntilIdle()

        vm.onDecline("NIGHT-9H4M5P")
        vm.onCancel("NIGHT-AAAAAA")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.requests!!.received.isEmpty())
        assertTrue(vm.uiState.value.requests!!.sent.isEmpty())
        assertTrue(friends.friends.isEmpty())
    }

    @Test
    fun requests_aRequestThatIsGone_refreshesTheList() = runTest {
        friends.received += PersonRef("mateo", "NIGHT-9H4M5P")
        val vm = FriendRequestsViewModel(friends)
        vm.refresh()
        advanceUntilIdle()
        // The other person cancelled before the tap.
        friends.received.clear()

        vm.onAccept("NIGHT-9H4M5P")
        advanceUntilIdle()

        assertEquals(SocialError.Rejected(SocialRejection.RequestGone), vm.uiState.value.actionError)
        assertTrue(vm.uiState.value.requests!!.received.isEmpty())
        assertEquals(3, friends.calls) // list, accept, list again
    }

    @Test
    fun requests_aFailedAction_keepsTheRow_andShowsTheError() = runTest {
        friends.received += PersonRef("mateo", "NIGHT-9H4M5P")
        val vm = FriendRequestsViewModel(friends)
        vm.refresh()
        advanceUntilIdle()

        friends.failure = SocialError.Offline
        vm.onAccept("NIGHT-9H4M5P")
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.requests!!.received.size)
        assertEquals(SocialError.Offline, vm.uiState.value.actionError)
        assertTrue(vm.uiState.value.busy.isEmpty())
    }

    @Test
    fun requests_aSecondTapOnTheSameRow_isIgnored() = runTest {
        friends.received += PersonRef("mateo", "NIGHT-9H4M5P")
        val vm = FriendRequestsViewModel(friends)
        vm.refresh()
        advanceUntilIdle()
        val before = friends.calls

        vm.onAccept("NIGHT-9H4M5P")
        vm.onAccept("NIGHT-9H4M5P")
        advanceUntilIdle()

        assertEquals(before + 1, friends.calls)
    }

    // ---- friend profile ----

    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    private fun profileViewModel(code: String = "NIGHT-VVVVVV") =
        FriendProfileViewModel(SavedStateHandle(mapOf(FriendProfileViewModel.KEY_FRIEND_CODE to code)), friends, pairs, lobby, clock)

    private fun addValentina(hidden: Boolean = false) {
        friends.friends += Friend("valentina", "NIGHT-VVVVVV", 12, trainedToday = true)
        friends.profiles["NIGHT-VVVVVV"] = FriendProfile(
            username = "valentina",
            friendCode = "NIGHT-VVVVVV",
            streakDays = 12,
            trainedToday = true,
            hidden = hidden,
            progress = if (hidden) {
                null
            } else {
                FriendProgress(
                    fighterLevel = 3,
                    viewedCount = 2,
                    labsCount = 1,
                    badgeFacts = BadgeFacts(
                        viewedLessons = setOf(LearningTopic.CONTRASENAS, LearningTopic.PHISHING),
                        completedLabs = setOf(LearningTopic.CONTRASENAS),
                        examPassed = false,
                        examPerfect = false,
                        bestStreak = 12,
                    ),
                )
            },
            history = listOf(
                FriendBattleEntry(BattleOutcome.Win, Instant.parse("2026-10-19T10:00:00Z")),
                FriendBattleEntry(BattleOutcome.Loss, Instant.parse("2026-10-15T10:00:00Z")),
                FriendBattleEntry(BattleOutcome.Draw, null),
            ),
        )
    }

    @Test
    fun profile_showsTheCountersTheLevelAndTheBadgesThatAreMet() = runTest {
        addValentina()
        val vm = profileViewModel()

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals("valentina", state.username)
        assertEquals(12, state.streakDays)
        assertEquals(3, state.fighterLevel)
        assertEquals(2, state.exercises)
        assertEquals(1, state.labs)
        // "Primeros pasos" (a lesson seen), "Racha de 7 días" (best streak 12) and "Maestro de Contraseñas" (island done).
        assertEquals(
            listOf("Primeros pasos", "Racha de 7 días", "Maestro de Contraseñas"),
            state.unlockedBadges!!.map { it.definition.name },
        )
        assertFalse(state.hidden)
    }

    @Test
    fun profile_theHistory_saysHowManyDaysAgo() = runTest {
        addValentina()
        val vm = profileViewModel()

        vm.refresh()
        advanceUntilIdle()

        assertEquals(
            listOf(
                FriendBattleRow(BattleOutcome.Win, 2),
                FriendBattleRow(BattleOutcome.Loss, 6),
                FriendBattleRow(BattleOutcome.Draw, null),
            ),
            vm.uiState.value.history,
        )
    }

    @Test
    fun profile_aHiddenFriend_showsNoLevelNoBadgesAndNoCounters_butKeepsTheStreakAndTheHistory() = runTest {
        addValentina(hidden = true)
        val vm = profileViewModel()

        vm.refresh()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.hidden)
        assertNull(state.fighterLevel)
        assertNull(state.exercises)
        assertNull(state.labs)
        assertNull(state.unlockedBadges)
        assertEquals(12, state.streakDays)
        assertEquals(3, state.history.size)
    }

    @Test
    fun profile_aFriendThatIsNotMine_isNotFound() = runTest {
        val vm = profileViewModel("NIGHT-ZZZZZZ")

        vm.refresh()
        advanceUntilIdle()

        assertEquals(SocialError.Rejected(SocialRejection.NotFound), vm.uiState.value.loadError)
        assertFalse(vm.uiState.value.loaded)
    }

    @Test
    fun profile_removing_needsConfirmation_andThenGoesBack() = runTest {
        addValentina()
        val vm = profileViewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onRemoveClicked()
            assertTrue(vm.uiState.value.confirmingRemove)
            assertEquals(1, friends.friends.size)

            vm.onRemoveConfirmed()
            advanceUntilIdle()

            assertEquals(FriendProfileEvent.Removed, awaitItem())
        }
        assertTrue(friends.friends.isEmpty())
    }

    @Test
    fun profile_battle_sendsTheChallenge_andGoesToTheLobby() = runTest {
        addValentina()
        val vm = profileViewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onBattleClicked()
            advanceUntilIdle()

            assertEquals(FriendProfileEvent.ChallengeSent, awaitItem())
        }
        assertEquals(listOf("NIGHT-VVVVVV"), lobby.sentChallengesTo)
    }

    @Test
    fun profile_battle_withARivalBusy_showsTheMessage_andStays() = runTest {
        addValentina()
        lobby.sendChallengeResult = com.example.nightwatchprotocol.data.social.SocialResult.Failure(
            SocialError.Rejected(SocialRejection.RivalBusy),
        )
        val vm = profileViewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.events.test {
            vm.onBattleClicked()
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(SocialError.Rejected(SocialRejection.RivalBusy), vm.uiState.value.actionError)
        assertFalse(vm.uiState.value.challenging)
    }

    @Test
    fun profile_cancellingTheConfirmation_removesNothing() = runTest {
        addValentina()
        val vm = profileViewModel()
        vm.refresh()
        advanceUntilIdle()

        vm.onRemoveClicked()
        vm.onRemoveDismissed()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.confirmingRemove)
        assertEquals(1, friends.friends.size)
    }

    @Test
    fun profile_aFailedRemoval_staysOnTheScreenWithTheError() = runTest {
        addValentina()
        val vm = profileViewModel()
        vm.refresh()
        advanceUntilIdle()
        friends.failure = SocialError.Offline

        vm.events.test {
            vm.onRemoveClicked()
            vm.onRemoveConfirmed()
            advanceUntilIdle()

            expectNoEvents()
        }
        assertEquals(SocialError.Offline, vm.uiState.value.actionError)
        assertFalse(vm.uiState.value.removing)
        assertNotNull(vm.uiState.value.username)
    }

    @Test
    fun daysAgo_isWholeDays_neverNegative_andNullWithoutADate() {
        assertEquals(0, daysAgo(now.minusSeconds(3_600), now))
        assertEquals(1, daysAgo(now.minusSeconds(86_400 + 5), now))
        assertEquals(0, daysAgo(now.plusSeconds(600), now))
        assertNull(daysAgo(null, now))
    }
}
