package com.example.nightwatchprotocol.ui.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.example.nightwatchprotocol.ui.auth.login.ErrorDialogKind
import com.example.nightwatchprotocol.ui.auth.login.MESSAGE_ERROR_DIALOG_NETWORK
import com.example.nightwatchprotocol.ui.auth.login.message
import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleSignInTest {

    @Test
    fun aUserCancellation_isCancelled() {
        assertEquals(GoogleCredentialResult.Cancelled, classifyGoogleFailure(GetCredentialCancellationException()))
    }

    @Test
    fun aPhoneWithoutAGoogleAccount_isNoAccount() {
        assertEquals(GoogleCredentialResult.NoAccount, classifyGoogleFailure(NoCredentialException()))
    }

    @Test
    fun noAccountDialog_asksToAddAGoogleAccount_andOtherKindsKeepTheirTexts() {
        assertEquals(
            "Agrega una cuenta de Google en tu teléfono para continuar",
            ErrorDialogKind.NO_GOOGLE_ACCOUNT.message(unknown = "x"),
        )
        assertEquals("x", ErrorDialogKind.UNKNOWN.message(unknown = "x"))
        assertEquals(MESSAGE_ERROR_DIALOG_NETWORK, ErrorDialogKind.NETWORK.message(unknown = "x"))
    }

    @Test
    fun anyOtherFailure_isFailed() {
        assertEquals(GoogleCredentialResult.Failed, classifyGoogleFailure(GetCredentialUnknownException()))
        assertEquals(GoogleCredentialResult.Failed, classifyGoogleFailure(RuntimeException("x")))
    }
}
