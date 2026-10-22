package com.example.nightwatchprotocol.ui.navigation

import com.example.nightwatchprotocol.data.learning.LearningTopic
import com.example.nightwatchprotocol.ui.home.HomeTab
import com.example.nightwatchprotocol.ui.verifycode.CodePurpose
import kotlinx.serialization.Serializable

@Serializable
object Login

@Serializable
object Register

/** Password recovery by code: the new password, set under the session that the code opened. */
@Serializable
object NewPassword

/** "¡Contraseña actualizada!" at the end of the recovery; it goes back to Login. */
@Serializable
object PasswordReset

/** The 6-digit code screen, shared by sign-up, email change and password recovery. */
@Serializable
data class VerifyCode(val email: String, val purpose: CodePurpose)

/** The container with the bottom bar (Inicio, Ruta, Labs). [tab] is the one shown first: Inicio by default, Ruta after the quiz. */
@Serializable
data class Home(val tab: HomeTab = HomeTab.Inicio)

/** A lesson, full screen and without the bottom bar. */
@Serializable
data class Lesson(val topic: LearningTopic)

/** The terminal of one lab, full screen and without the bottom bar. */
@Serializable
data class Lab(val topic: LearningTopic)

/** The report of a finished attempt: the objectives done decide success or failure. Full screen, without the bottom bar. */
@Serializable
data class LabReport(val topic: LearningTopic, val completedObjectives: List<Int>)

/** The introduction of the final exam, full screen and without the bottom bar. */
@Serializable
object ExamIntro

/** The questions of one attempt of the final exam, one per screen. A new entry is a new attempt from question 1. */
@Serializable
object Exam

/** The result of a finished attempt: [answers] is the option chosen (0 to 3) for each question, in order. */
@Serializable
data class ExamResult(val answers: List<Int>)

/** "Amigos" of Social: the list of friends, full screen and without the bottom bar. */
@Serializable
object Friends

/** "Agregar amigos": search by username or friend code, the own code and the way to the requests. */
@Serializable
object AddFriend

@Serializable
object FriendRequestsRoute

/** The profile of one friend. [friendCode] is the public code of the friend (never an internal id). */
@Serializable
data class FriendProfileRoute(val friendCode: String)

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

/** [notice] is a non-blocking warning shown under the confirmation (e.g. the old Google account was not unlinked). */
@Serializable
data class EmailUpdated(val notice: String? = null)

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
