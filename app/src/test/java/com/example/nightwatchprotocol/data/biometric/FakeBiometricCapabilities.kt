package com.example.nightwatchprotocol.data.biometric

class FakeBiometricCapabilities(
    var availability: BiometricAvailability = BiometricAvailability.Available,
) : BiometricCapabilities {
    override fun availability(): BiometricAvailability = availability
}
