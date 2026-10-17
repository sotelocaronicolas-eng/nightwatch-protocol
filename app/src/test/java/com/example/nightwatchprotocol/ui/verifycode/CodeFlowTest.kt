package com.example.nightwatchprotocol.ui.verifycode

import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.auth.AuthError
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import com.example.nightwatchprotocol.data.session.SessionPreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CodeFlowTest {

    @Test
    fun signUp_confirmsAndResendsThroughTheAuthRepository() = runTest {
        val auth = FakeAuthRepository()
        val account = FakeAccountRepository()
        val flow = codeFlowFor(CodePurpose.SIGN_UP, "ana@example.com", auth, account)

        flow.confirm("123456")
        flow.resend()

        assertEquals(listOf("ana@example.com" to "123456"), auth.verifySignUpCodeCalls)
        assertEquals(listOf("ana@example.com"), auth.resendSignUpCodeCalls)
        assertTrue(account.verifyEmailChangeCodeCalls.isEmpty())
    }

    @Test
    fun recovery_confirmsWithTheRecoveryCode_andResendingAsksForANewRecoveryEmail() = runTest {
        val auth = FakeAuthRepository()
        val flow = RecoveryCodeFlow(auth, SessionPreferences.inMemory(), "ana@example.com")

        flow.confirm("111222")
        flow.resend()

        assertEquals(listOf("ana@example.com" to "111222"), auth.verifyRecoveryCodeCalls)
        assertEquals(1, auth.sendPasswordResetEmailCallCount)
        assertTrue(auth.resendSignUpCodeCalls.isEmpty())
    }

    @Test
    fun recovery_aVerifiedCode_leavesTheRecoveryMark() = runTest {
        val preferences = SessionPreferences.inMemory()
        val flow = RecoveryCodeFlow(FakeAuthRepository(), preferences, "ana@example.com")

        flow.confirm("111222")

        assertTrue(preferences.isRecoveryInProgress())
    }

    @Test
    fun recovery_aRefusedCode_doesNotLeaveTheRecoveryMark() = runTest {
        val auth = FakeAuthRepository().apply { verifyRecoveryCodeResult = Result.failure(AuthError.InvalidCode) }
        val preferences = SessionPreferences.inMemory()
        val flow = RecoveryCodeFlow(auth, preferences, "ana@example.com")

        try {
            flow.confirm("000000")
            fail("debía lanzar")
        } catch (e: AuthError) {
            assertEquals(AuthError.InvalidCode, e)
        }

        assertFalse(preferences.isRecoveryInProgress())
    }

    @Test
    fun emailChange_confirmsAndResendsThroughTheAccountRepository_withTheNewEmail() = runTest {
        val auth = FakeAuthRepository()
        val account = FakeAccountRepository()
        val flow = codeFlowFor(CodePurpose.EMAIL_CHANGE, "nuevo@example.com", auth, account)

        flow.confirm("654321")
        flow.resend()

        assertEquals(listOf("nuevo@example.com" to "654321"), account.verifyEmailChangeCodeCalls)
        assertEquals(listOf("nuevo@example.com"), account.resendEmailChangeCodeCalls)
        assertTrue(auth.verifySignUpCodeCalls.isEmpty())
    }

    @Test
    fun emailChange_afterConfirming_unlinksTheOldGoogleAccounts_withNoNoticeWhenItWorks() = runTest {
        val account = FakeAccountRepository()
        val flow = EmailChangeCodeFlow(account, "nuevo@example.com")

        flow.confirm("654321")

        assertEquals(listOf("nuevo@example.com"), account.unlinkOutdatedGoogleAccountsCalls)
        assertNull(flow.completionNotice)
    }

    @Test
    fun emailChange_ifTheUnlinkFails_theChangeStands_andThereIsANotice() = runTest {
        val account = FakeAccountRepository().apply { unlinkOutdatedGoogleAccountsResult = false }
        val flow = EmailChangeCodeFlow(account, "nuevo@example.com")

        flow.confirm("654321")

        assertEquals("nuevo@example.com", account.profile?.email)
        assertEquals(MESSAGE_GOOGLE_UNLINK_FAILED, flow.completionNotice)
    }

    @Test
    fun emailChange_aRefusedCode_doesNotTouchTheGoogleAccounts() = runTest {
        val account = FakeAccountRepository().apply {
            verifyEmailChangeCodeResult = Result.failure(AuthError.InvalidCode)
        }
        val flow = EmailChangeCodeFlow(account, "nuevo@example.com")

        try {
            flow.confirm("000000")
            fail("debía lanzar")
        } catch (e: AuthError) {
            assertEquals(AuthError.InvalidCode, e)
        }

        assertTrue(account.unlinkOutdatedGoogleAccountsCalls.isEmpty())
    }
}
