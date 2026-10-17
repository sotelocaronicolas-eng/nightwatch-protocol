package com.example.nightwatchprotocol.ui.verifycode

import com.example.nightwatchprotocol.data.account.FakeAccountRepository
import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
