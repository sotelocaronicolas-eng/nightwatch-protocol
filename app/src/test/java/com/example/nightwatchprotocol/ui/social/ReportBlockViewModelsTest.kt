package com.example.nightwatchprotocol.ui.social

import com.example.nightwatchprotocol.MainDispatcherRule
import com.example.nightwatchprotocol.data.social.FakeFriendsRepository
import com.example.nightwatchprotocol.data.social.FakeSocialProfileRepository
import com.example.nightwatchprotocol.data.social.Friend
import com.example.nightwatchprotocol.data.social.REPORT_NOTE_MAX_LENGTH
import com.example.nightwatchprotocol.data.social.ReportReason
import com.example.nightwatchprotocol.data.social.SocialError
import com.example.nightwatchprotocol.data.social.SocialProfile
import com.example.nightwatchprotocol.data.social.SocialRejection
import com.example.nightwatchprotocol.data.social.FriendRelation
import com.example.nightwatchprotocol.data.social.FriendSearchHit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReportBlockViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val friends = FakeFriendsRepository().apply {
        addPerson("mateo", "NIGHT-9H4M5P")
        addPerson("sofia", "NIGHT-AAAAAA")
    }

    // ---- Reportar ----

    @Test
    fun theReportCannotBeSentWithoutAReason() = runTest {
        val vm = ReportViewModel("mateo", friends)

        assertFalse(vm.uiState.value.canSend)
        vm.onSend()
        advanceUntilIdle()

        assertTrue(friends.reports.isEmpty())
        assertFalse(vm.uiState.value.sent)
    }

    @Test
    fun aReportWithReasonAndNote_isSentOnce_andSaysSo() = runTest {
        val vm = ReportViewModel("mateo", friends)
        vm.onReasonSelected(ReportReason.Harassment)
        vm.onNoteChanged("  me insulta  ")
        assertEquals("  me insulta  ", vm.uiState.value.note)

        // A second tap while the first is in flight is ignored.
        vm.onSend()
        vm.onSend()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.sent)
        assertFalse(vm.uiState.value.sending)
        assertEquals(1, friends.reports.size)
        assertEquals(FakeFriendsRepository.ReportCall("NIGHT-9H4M5P", ReportReason.Harassment, "me insulta"), friends.reports[0])
    }

    @Test
    fun theNote_isCutAtTheLimit_andTheCounterShowsTheLength() = runTest {
        val vm = ReportViewModel("mateo", friends)

        vm.onNoteChanged("x".repeat(REPORT_NOTE_MAX_LENGTH + 50))

        assertEquals(REPORT_NOTE_MAX_LENGTH, vm.uiState.value.note.length)
        assertEquals(REPORT_NOTE_MAX_LENGTH, vm.uiState.value.noteLength)
    }

    @Test
    fun aReportWithoutNote_isSent() = runTest {
        val vm = ReportViewModel("sofia", friends)
        vm.onReasonSelected(ReportReason.InappropriatePhoto)

        vm.onSend()
        advanceUntilIdle()

        assertEquals("", friends.reports.single().note)
        assertTrue(vm.uiState.value.sent)
    }

    @Test
    fun aStranger_canBeReported_whetherOrNotTheyAreAFriend() = runTest {
        // "sofia" is nobody's friend here: any person can be reported.
        val vm = ReportViewModel("sofia", friends)
        vm.onReasonSelected(ReportReason.OffensiveName)

        vm.onSend()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.sent)
        assertTrue(friends.friends.isEmpty())
    }

    @Test
    fun whenTheServerDoesNotHaveReports_thePersonSeesTheUnavailableMessage_andCanTryAgain() = runTest {
        friends.failure = SocialError.Unavailable
        val vm = ReportViewModel("mateo", friends)
        vm.onReasonSelected(ReportReason.Other)

        vm.onSend()
        advanceUntilIdle()

        assertEquals(SocialError.Unavailable, vm.uiState.value.error)
        assertFalse(vm.uiState.value.sent)
        assertFalse(vm.uiState.value.sending)
        assertTrue(vm.uiState.value.canSend)
    }

    @Test
    fun aReasonOrNoteChangeAfterAnError_clearsIt_andAfterSendingIsIgnored() = runTest {
        friends.failure = SocialError.Offline
        val vm = ReportViewModel("mateo", friends)
        vm.onReasonSelected(ReportReason.Other)
        vm.onSend()
        advanceUntilIdle()
        assertEquals(SocialError.Offline, vm.uiState.value.error)

        friends.failure = null
        vm.onNoteChanged("hola")
        assertNull(vm.uiState.value.error)
        vm.onSend()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.sent)

        vm.onReasonSelected(ReportReason.Harassment)
        vm.onNoteChanged("otra cosa")
        assertEquals(ReportReason.Other, vm.uiState.value.reason)
        assertEquals("hola", vm.uiState.value.note)
    }

    @Test
    fun reportingDoesNotBlockNorRemoveAnyone() = runTest {
        friends.friends += Friend("mateo", "NIGHT-9H4M5P", 0, false)
        val vm = ReportViewModel("mateo", friends)
        vm.onReasonSelected(ReportReason.Harassment)

        vm.onSend()
        advanceUntilIdle()

        assertEquals(1, friends.friends.size)
        assertTrue(friends.blocked.isEmpty())
    }

    // ---- Bloquear ----

    @Test
    fun blocking_endsTheFriendship_andIsDoneOnce() = runTest {
        friends.friends += Friend("mateo", "NIGHT-9H4M5P", 0, false)
        val vm = BlockViewModel("mateo", friends)

        vm.onConfirm()
        vm.onConfirm()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.blocked)
        assertEquals(1, friends.blockCalls)
        assertTrue(friends.friends.isEmpty())
        assertEquals("mateo", friends.blocked.single().username)
    }

    @Test
    fun blocking_whenTheServerDoesNotHaveIt_showsTheUnavailableMessage() = runTest {
        friends.failure = SocialError.Unavailable
        val vm = BlockViewModel("mateo", friends)

        vm.onConfirm()
        advanceUntilIdle()

        assertEquals(SocialError.Unavailable, vm.uiState.value.error)
        assertFalse(vm.uiState.value.blocked)
        assertFalse(vm.uiState.value.blocking)
    }

    @Test
    fun blocking_someoneWhoDoesNotExist_isNotFound() = runTest {
        val vm = BlockViewModel("zz_nadie", friends)

        vm.onConfirm()
        advanceUntilIdle()

        assertEquals(SocialError.Rejected(SocialRejection.NotFound), vm.uiState.value.error)
    }

    // ---- Personas bloqueadas ----

    @Test
    fun theBlockedList_showsTheNewestFirst() = runTest {
        friends.block("mateo")
        friends.block("sofia")
        val vm = BlockedPeopleViewModel(friends)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(listOf("sofia", "mateo"), vm.uiState.value.people!!.map { it.username })
        assertFalse(vm.uiState.value.loading)
    }

    @Test
    fun anEmptyBlockedList_isEmptyNotAnError() = runTest {
        val vm = BlockedPeopleViewModel(friends)

        vm.refresh()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.people!!.isEmpty())
        assertNull(vm.uiState.value.loadError)
    }

    @Test
    fun unblocking_removesTheRowOnly_andDoesNotBringBackTheFriendship() = runTest {
        friends.friends += Friend("mateo", "NIGHT-9H4M5P", 0, false)
        friends.block("mateo")
        friends.block("sofia")
        val vm = BlockedPeopleViewModel(friends)
        vm.refresh()
        advanceUntilIdle()

        vm.onUnblock("mateo")
        advanceUntilIdle()

        val after = vm.uiState.value
        assertTrue(after.busy.isEmpty())
        assertEquals(listOf("sofia"), after.people!!.map { it.username })
        assertEquals(1, friends.unblockCalls)
        assertTrue(friends.friends.isEmpty())
    }

    @Test
    fun aFailedUnblock_keepsTheRow_andReportsTheError() = runTest {
        friends.block("mateo")
        val vm = BlockedPeopleViewModel(friends)
        vm.refresh()
        advanceUntilIdle()

        friends.failure = SocialError.Offline
        vm.onUnblock("mateo")
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.people!!.size)
        assertEquals(SocialError.Offline, vm.uiState.value.actionError)
    }

    @Test
    fun unblockingSomeoneWhoIsNotBlockedAnyMore_readsTheListAgain() = runTest {
        friends.block("mateo")
        val vm = BlockedPeopleViewModel(friends)
        vm.refresh()
        advanceUntilIdle()
        // Another phone lifted the block first.
        friends.blocked.clear()

        vm.onUnblock("mateo")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.people!!.isEmpty())
    }

    @Test
    fun whenTheServerDoesNotHaveBlocks_theListSaysUnavailable_andNothingBreaks() = runTest {
        friends.failure = SocialError.Unavailable
        val vm = BlockedPeopleViewModel(friends)

        vm.refresh()
        advanceUntilIdle()

        assertEquals(SocialError.Unavailable, vm.uiState.value.loadError)
        assertNull(vm.uiState.value.people)
        assertFalse(vm.uiState.value.loading)
    }

    // ---- the search result goes away when the person is blocked from it ----

    @Test
    fun blockingFromTheSearchResult_clearsTheResult() = runTest {
        val vm = AddFriendViewModel(friends, FakeSocialProfileRepository(SocialProfile("yo", "NIGHT-MINE22", hideProgress = false)))
        vm.onQueryChanged("mateo")
        vm.onSearch()
        advanceUntilIdle()
        assertEquals(FriendSearchHit("mateo", "NIGHT-9H4M5P", FriendRelation.None), vm.uiState.value.hit)

        vm.onPersonBlocked()
        advanceUntilIdle()

        assertNull(vm.uiState.value.hit)
    }
}
