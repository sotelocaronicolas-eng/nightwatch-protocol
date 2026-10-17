package com.example.nightwatchprotocol.ui.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** What Credential Manager gave back for "Continuar con Google". */
sealed interface GoogleCredentialResult {
    data class IdToken(val idToken: String) : GoogleCredentialResult

    /** The user closed the account chooser. Not an error. */
    data object Cancelled : GoogleCredentialResult

    data object Failed : GoogleCredentialResult
}

/** Only a user cancellation is told apart; every other Credential Manager failure is [GoogleCredentialResult.Failed]. */
fun classifyGoogleFailure(error: Throwable): GoogleCredentialResult =
    if (error is GetCredentialCancellationException) GoogleCredentialResult.Cancelled else GoogleCredentialResult.Failed

/**
 * Opens the Google account chooser and returns its ID token. [hashedNonce] (SHA-256 in hex) is set on the request
 * only when the backend checks nonces; with null the request is exactly the one Firebase has always used.
 * Nothing about the token or the nonce is logged.
 */
suspend fun requestGoogleIdToken(
    context: Context,
    serverClientId: String,
    hashedNonce: String?,
): GoogleCredentialResult {
    val option = GetSignInWithGoogleOption.Builder(serverClientId = serverClientId)
        .apply { hashedNonce?.let(::setNonce) }
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
    return try {
        val credential = CredentialManager.create(context).getCredential(context, request).credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            GoogleCredentialResult.IdToken(GoogleIdTokenCredential.createFrom(credential.data).idToken)
        } else {
            GoogleCredentialResult.Failed
        }
    } catch (e: GetCredentialException) {
        classifyGoogleFailure(e)
    } catch (e: GoogleIdTokenParsingException) {
        GoogleCredentialResult.Failed
    }
}
