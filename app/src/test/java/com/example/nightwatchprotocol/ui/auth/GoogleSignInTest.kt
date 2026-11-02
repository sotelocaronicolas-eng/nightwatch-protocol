package com.example.nightwatchprotocol.ui.auth

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import com.example.nightwatchprotocol.ui.auth.login.ErrorDialogKind
import com.example.nightwatchprotocol.data.common.UiText
import com.example.nightwatchprotocol.text
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
            ErrorDialogKind.NO_GOOGLE_ACCOUNT.message(unknown = UiText.dynamic("x")).text(),
        )
        assertEquals("x", ErrorDialogKind.UNKNOWN.message(unknown = UiText.dynamic("x")).text())
        assertEquals(MESSAGE_ERROR_DIALOG_NETWORK, ErrorDialogKind.NETWORK.message(unknown = UiText.dynamic("x")))
        assertEquals(
            "Sin conexión a internet. Verifica tu conexión e intenta de nuevo.",
            MESSAGE_ERROR_DIALOG_NETWORK.text(),
        )
    }

    @Test
    fun anyOtherFailure_isFailed() {
        assertEquals(GoogleCredentialResult.Failed, classifyGoogleFailure(GetCredentialUnknownException()))
        assertEquals(GoogleCredentialResult.Failed, classifyGoogleFailure(RuntimeException("x")))
    }
}
