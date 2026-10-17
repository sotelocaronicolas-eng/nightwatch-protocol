package com.example.nightwatchprotocol.ui.verifycode

import com.example.nightwatchprotocol.data.auth.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CodeFlowTest {

    @Test
    fun signUp_confirmsAndResendsThroughTheAuthRepository() = runTest {
        val auth = FakeAuthRepository()
        val flow = codeFlowFor(CodePurpose.SIGN_UP, "ana@example.com", auth)

        flow.confirm("123456")
        flow.resend()

        assertEquals(listOf("ana@example.com" to "123456"), auth.verifySignUpCodeCalls)
        assertEquals(listOf("ana@example.com"), auth.resendSignUpCodeCalls)
    }
}
