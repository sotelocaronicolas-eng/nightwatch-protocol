package com.example.nightwatchprotocol.data.biometric

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG

enum class BiometricAvailability {
    Available,
    NoneEnrolled,
    NoHardware,
    TemporarilyUnavailable,
    Unsupported,
}

interface BiometricCapabilities {
    /** Whether a strong biometric (fingerprint or face) can be used right now. */
    fun availability(): BiometricAvailability
}

class AndroidBiometricCapabilities(private val context: Context) : BiometricCapabilities {

    override fun availability(): BiometricAvailability =
        when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.Available
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NoneEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NoHardware
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAvailability.TemporarilyUnavailable
            else -> BiometricAvailability.Unsupported
        }
}
