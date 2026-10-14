package com.example.nightwatchprotocol.ui.biometric

import android.os.Build
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

sealed interface BiometricResult {
    data object Success : BiometricResult

    /** The user cancelled, or the prompt ended with an error (too many attempts, lockout, ...). */
    data object Failed : BiometricResult
}

/** Thin wrapper over the system [BiometricPrompt]; needs a [FragmentActivity] (decision P14). */
class BiometricPrompter(private val activity: FragmentActivity) {

    /**
     * @param allowDeviceCredential lets the phone's PIN, pattern or password stand in for the biometric. Only
     * offered on Android 11+, where the library supports combining it with a strong biometric; on older
     * versions the prompt is biometric-only with a "Cancelar" button.
     */
    fun authenticate(
        title: String,
        subtitle: String,
        allowDeviceCredential: Boolean,
        onResult: (BiometricResult) -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(BiometricResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(BiometricResult.Failed)
            }
            // onAuthenticationFailed is a single bad attempt: the system keeps the prompt open, so nothing to do.
        }

        val builder = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle)
        if (allowDeviceCredential && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        } else {
            builder.setAllowedAuthenticators(BIOMETRIC_STRONG).setNegativeButtonText("Cancelar")
        }

        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(builder.build())
    }
}
