package com.example.nightwatchprotocol.ui.navigation

import com.example.nightwatchprotocol.ui.verifycode.CodePurpose
import kotlinx.serialization.Serializable

@Serializable
object Login

@Serializable
object Register

@Serializable
object ForgotPassword

/** The 6-digit code screen, shared by sign-up, email change and password recovery. */
@Serializable
data class VerifyCode(val email: String, val purpose: CodePurpose)

@Serializable
object MainMenu

@Serializable
object UserMenu

@Serializable
object Profile

@Serializable
object ProfileUpdated

@Serializable
object EditProfile

@Serializable
object ConfirmIdentity

@Serializable
object PrivacySecurity

@Serializable
object ChangePassword

@Serializable
object PasswordUpdated

@Serializable
data class ChangeEmail(val newEmail: String)

@Serializable
data class VerifyEmailLink(val newEmail: String)

@Serializable
object EmailUpdated

@Serializable
object NotificationSettings

@Serializable
object Appearance

@Serializable
object Language

@Serializable
object Achievements

@Serializable
object Devices

@Serializable
object DeviceSignedOut

@Serializable
object Help

@Serializable
object About

@Serializable
object RecoveryEmail

@Serializable
object RecoveryEmailSaved

@Serializable
object PostAuthGate

@Serializable
object PlacementQuiz

@Serializable
data class PlacementResultRoute(
    val level: String,
    val weakCategories: List<String>,
    val saveFailed: Boolean = false,
)
