package com.example.nightwatchprotocol.ui.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleSignInTest {

    @Test
    fun aUserCancellation_isCancelled() {
        assertEquals(GoogleCredentialResult.Cancelled, classifyGoogleFailure(GetCredentialCancellationException()))
    }

    @Test
    fun anyOtherFailure_isFailed() {
        assertEquals(GoogleCredentialResult.Failed, classifyGoogleFailure(NoCredentialException()))
        assertEquals(GoogleCredentialResult.Failed, classifyGoogleFailure(GetCredentialUnknownException()))
        assertEquals(GoogleCredentialResult.Failed, classifyGoogleFailure(RuntimeException("x")))
    }
}
