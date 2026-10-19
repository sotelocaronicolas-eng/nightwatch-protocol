package com.example.nightwatchprotocol.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.nightwatchprotocol.data.quiz.QuizCategory
import com.example.nightwatchprotocol.ui.auth.forgotpassword.NewPasswordScreen
import com.example.nightwatchprotocol.ui.auth.login.LoginScreen
import com.example.nightwatchprotocol.ui.auth.register.RegisterScreen
import com.example.nightwatchprotocol.ui.home.HomeScreen
import com.example.nightwatchprotocol.ui.home.HomeTab
import com.example.nightwatchprotocol.ui.exam.ExamIntroScreen
import com.example.nightwatchprotocol.ui.exam.ExamScreen
import com.example.nightwatchprotocol.ui.examresult.ExamDestination
import com.example.nightwatchprotocol.ui.examresult.ExamResultScreen
import com.example.nightwatchprotocol.ui.lab.LabScreen
import com.example.nightwatchprotocol.ui.labreport.LabReportScreen
import com.example.nightwatchprotocol.ui.lesson.LessonScreen
import com.example.nightwatchprotocol.ui.postauth.PostAuthGateScreen
import com.example.nightwatchprotocol.ui.profile.changeemail.ChangeEmailScreen
import com.example.nightwatchprotocol.ui.profile.changepassword.ChangePasswordScreen
import com.example.nightwatchprotocol.ui.profile.achievements.AchievementsScreen
import com.example.nightwatchprotocol.ui.profile.appearance.AppearanceScreen
import com.example.nightwatchprotocol.ui.profile.appearance.LanguageScreen
import com.example.nightwatchprotocol.ui.profile.confirmation.DeviceSignedOutScreen
import com.example.nightwatchprotocol.ui.profile.confirmation.EmailUpdatedScreen
import com.example.nightwatchprotocol.ui.profile.confirmation.PasswordUpdatedScreen
import com.example.nightwatchprotocol.ui.profile.confirmation.ProfileUpdatedScreen
import com.example.nightwatchprotocol.ui.profile.confirmation.RecoveryEmailSavedScreen
import com.example.nightwatchprotocol.ui.profile.confirmidentity.ConfirmIdentityScreen
import com.example.nightwatchprotocol.ui.profile.editprofile.EditProfileScreen
import com.example.nightwatchprotocol.ui.profile.devices.DevicesScreen
import com.example.nightwatchprotocol.ui.profile.help.AboutScreen
import com.example.nightwatchprotocol.ui.profile.help.HelpScreen
import com.example.nightwatchprotocol.ui.profile.myprofile.MyProfileScreen
import com.example.nightwatchprotocol.ui.profile.notifications.NotificationSettingsScreen
import com.example.nightwatchprotocol.ui.profile.privacy.PrivacySecurityScreen
import com.example.nightwatchprotocol.ui.profile.recoveryemail.RecoveryEmailScreen
import com.example.nightwatchprotocol.ui.profile.usermenu.UserMenuScreen
import com.example.nightwatchprotocol.ui.quiz.PlacementQuizScreen
import com.example.nightwatchprotocol.ui.quiz.PlacementResultScreen
import com.example.nightwatchprotocol.ui.verifycode.CodePurpose
import com.example.nightwatchprotocol.ui.verifycode.VerifyCodeScreen

/** Key under which "Confirma tu identidad" tells the screen below it that the password was verified. */
private const val KEY_IDENTITY_CONFIRMED = "identity_confirmed"

/** Returns to a fresh "Mi Perfil" (stack: Home > Menú de usuario > Mi Perfil) so it shows the saved data. */
private fun NavHostController.backToProfile() {
    navigate(Profile) { popUpTo<UserMenu> { inclusive = false } }
}

@Composable
fun NightWatchNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: Any = Login,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable<Login> {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(PostAuthGate) { popUpTo<Login> { inclusive = true } }
                },
                onNavigateToRegister = { navController.navigate(Register) },
                onNavigateToVerifyCode = { email -> navController.navigate(VerifyCode(email, CodePurpose.SIGN_UP)) },
                onNavigateToNewPassword = { navController.navigate(NewPassword) },
            )
        }
        composable<NewPassword> {
            NewPasswordScreen(
                onPasswordReset = {
                    navController.navigate(PasswordReset) { popUpTo(0) { inclusive = true } }
                },
                onCancelled = {
                    navController.navigate(Login) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable<PasswordReset> {
            // The recovery session was closed by the new-password screen: Login is the only way on.
            PasswordUpdatedScreen(
                onDone = { navController.navigate(Login) { popUpTo(0) { inclusive = true } } },
                headerTitle = "Nueva Contraseña",
            )
        }
        composable<Register> {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(PostAuthGate) { popUpTo<Login> { inclusive = true } }
                },
                onNavigateToVerifyCode = { email -> navController.navigate(VerifyCode(email, CodePurpose.SIGN_UP)) },
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<VerifyCode> { entry ->
            val purpose = entry.toRoute<VerifyCode>().purpose
            VerifyCodeScreen(
                onVerified = { notice ->
                    when (purpose) {
                        // The session is open now: the same road as a normal sign-up (quiz or Home).
                        CodePurpose.SIGN_UP ->
                            navController.navigate(PostAuthGate) { popUpTo<Login> { inclusive = true } }
                        // Same exit as the link flow: "¡Correo actualizado!" replaces the email-change screens.
                        CodePurpose.EMAIL_CHANGE ->
                            navController.navigate(EmailUpdated(notice)) { popUpTo<ChangeEmail> { inclusive = true } }
                        // The server saved the recovery email when it accepted the code.
                        CodePurpose.RECOVERY_EMAIL ->
                            navController.navigate(RecoveryEmailSaved) { popUpTo<RecoveryEmail> { inclusive = true } }
                    }
                },
                onClose = {
                    when (purpose) {
                        CodePurpose.SIGN_UP -> navController.popBackStack<Login>(inclusive = false)
                        CodePurpose.EMAIL_CHANGE -> navController.popBackStack<EditProfile>(inclusive = false)
                        CodePurpose.RECOVERY_EMAIL -> navController.popBackStack<RecoveryEmail>(inclusive = false)
                    }
                },
            )
        }
        composable<PostAuthGate> {
            PostAuthGateScreen(
                onNavigateToMainMenu = {
                    navController.navigate(Home(HomeTab.Inicio)) { popUpTo<PostAuthGate> { inclusive = true } }
                },
                onNavigateToPlacementQuiz = {
                    navController.navigate(PlacementQuiz) { popUpTo<PostAuthGate> { inclusive = true } }
                },
            )
        }
        composable<PlacementQuiz> {
            PlacementQuizScreen(
                onFinished = { level, weakCategories, saveFailed ->
                    navController.navigate(
                        PlacementResultRoute(
                            level = level.name,
                            weakCategories = weakCategories.map(QuizCategory::name),
                            saveFailed = saveFailed,
                        ),
                    ) { popUpTo<PlacementQuiz> { inclusive = true } }
                },
            )
        }
        composable<PlacementResultRoute> {
            PlacementResultScreen(
                onContinue = {
                    // Decision P4: after the quiz the person lands on Ruta.
                    navController.navigate(Home(HomeTab.Ruta)) { popUpTo<PlacementResultRoute> { inclusive = true } }
                },
            )
        }
        composable<Home> {
            HomeScreen(
                onSignedOut = {
                    navController.navigate(Login) { popUpTo<Home> { inclusive = true } }
                },
                // "Perfil" of the bar (decision P15): the user menu opens above Home, which keeps its tab.
                onOpenUserMenu = { navController.navigate(UserMenu) },
                // From a map island or "Lección diaria": full screen, without the bar.
                onOpenLesson = { topic -> navController.navigate(Lesson(topic)) },
                onOpenLab = { topic -> navController.navigate(Lab(topic)) },
                // From the "Prueba Final" island or the "Examen Final" card, when it is unlocked.
                onOpenExam = { navController.navigate(ExamIntro) },
            )
        }
        composable<ExamIntro> {
            ExamIntroScreen(
                onClose = { navController.popBackStack() },
                onStart = { navController.navigate(Exam) },
            )
        }
        composable<Exam> {
            ExamScreen(
                // "X": back to where the exam was opened from, without saving anything.
                onClose = { navController.popBackStack<ExamIntro>(inclusive = true) },
                // The questions leave the stack: "atrás" from the result does not go back to the answered exam.
                onFinished = { answers ->
                    navController.navigate(ExamResult(answers)) { popUpTo<Exam> { inclusive = true } }
                },
            )
        }
        composable<ExamResult> {
            ExamResultScreen(
                onGo = { destination ->
                    when (destination) {
                        // Home is created again and reads the progress, so the map already counts a passed attempt.
                        ExamDestination.Path -> navController.navigate(Home(HomeTab.Ruta)) { popUpTo<Home> { inclusive = true } }
                        ExamDestination.Labs -> navController.navigate(Home(HomeTab.Labs)) { popUpTo<Home> { inclusive = true } }
                        ExamDestination.Home -> navController.navigate(Home(HomeTab.Inicio)) { popUpTo<Home> { inclusive = true } }
                        // A new attempt from question 1, in the same order (decision P11: no limit of attempts).
                        ExamDestination.NewAttempt -> navController.navigate(Exam) { popUpTo<ExamResult> { inclusive = true } }
                    }
                },
            )
        }
        composable<Lesson> {
            LessonScreen(
                onClose = { navController.popBackStack() },
                // The lab replaces the lesson (SUPUESTO): closing the lab goes back to Home, not to the lesson just read.
                onOpenLab = { topic ->
                    navController.navigate(Lab(topic)) { popUpTo<Lesson> { inclusive = true } }
                },
            )
        }
        composable<Lab> {
            LabScreen(
                onClose = { navController.popBackStack() },
                onOpenReport = { topic, completed -> navController.navigate(LabReport(topic, completed)) },
            )
        }
        composable<LabReport> {
            LabReportScreen(
                // The map shows the new progress: Home is created again and reads it.
                onBackToPath = {
                    navController.navigate(Home(HomeTab.Ruta)) { popUpTo<Home> { inclusive = true } }
                },
                // The same lab from scratch: the terminal and the report leave the stack and a new terminal opens.
                onRetryLab = { topic ->
                    navController.navigate(Lab(topic)) { popUpTo<Lab> { inclusive = true } }
                },
            )
        }
        composable<UserMenu> {
            UserMenuScreen(
                onClose = { navController.popBackStack() },
                onOpenProfile = { navController.navigate(Profile) },
                onOpenEditProfile = { navController.navigate(EditProfile) },
                onOpenAchievements = { navController.navigate(Achievements) },
                onOpenNotifications = { navController.navigate(NotificationSettings) },
                onOpenAppearance = { navController.navigate(Appearance) },
                onOpenLanguage = { navController.navigate(Language) },
                onOpenPrivacy = { navController.navigate(PrivacySecurity) },
                onOpenHelp = { navController.navigate(Help) },
                onOpenAbout = { navController.navigate(About) },
                onSignedOut = {
                    navController.navigate(Login) { popUpTo<Home> { inclusive = true } }
                },
            )
        }
        composable<Profile> {
            MyProfileScreen(
                onClose = { navController.popBackStack() },
                onEditProfile = { navController.navigate(EditProfile) },
            )
        }
        composable<EditProfile> { entry ->
            val identityConfirmed by entry.savedStateHandle
                .getStateFlow(KEY_IDENTITY_CONFIRMED, false)
                .collectAsStateWithLifecycle()
            EditProfileScreen(
                identityConfirmed = identityConfirmed,
                onIdentityConfirmedHandled = { entry.savedStateHandle[KEY_IDENTITY_CONFIRMED] = false },
                onClose = { navController.popBackStack() },
                onNavigateToConfirmIdentity = { navController.navigate(ConfirmIdentity) },
                onNavigateToChangeEmail = { newEmail -> navController.navigate(ChangeEmail(newEmail)) },
                onNavigateToProfileUpdated = { navController.navigate(ProfileUpdated) },
            )
        }
        composable<ChangeEmail> { entry ->
            val identityConfirmed by entry.savedStateHandle
                .getStateFlow(KEY_IDENTITY_CONFIRMED, false)
                .collectAsStateWithLifecycle()
            ChangeEmailScreen(
                identityConfirmed = identityConfirmed,
                onIdentityConfirmedHandled = { entry.savedStateHandle[KEY_IDENTITY_CONFIRMED] = false },
                onClose = { navController.popBackStack() },
                onNavigateToConfirmIdentity = { navController.navigate(ConfirmIdentity) },
                onCodeSent = { newEmail -> navController.navigate(VerifyCode(newEmail, CodePurpose.EMAIL_CHANGE)) },
            )
        }
        composable<EmailUpdated> { entry ->
            EmailUpdatedScreen(
                notice = entry.toRoute<EmailUpdated>().notice,
                onBackToEditProfile = {
                    // A fresh Editar Perfil, so it shows the new email.
                    navController.navigate(EditProfile) { popUpTo<EditProfile> { inclusive = true } }
                },
            )
        }
        composable<ConfirmIdentity> {
            ConfirmIdentityScreen(
                onClose = { navController.popBackStack() },
                onConfirmed = {
                    navController.previousBackStackEntry?.savedStateHandle?.set(KEY_IDENTITY_CONFIRMED, true)
                    navController.popBackStack()
                },
            )
        }
        composable<PrivacySecurity> {
            PrivacySecurityScreen(
                onClose = { navController.popBackStack() },
                onOpenChangePassword = { navController.navigate(ChangePassword) },
                onOpenRecoveryEmail = { navController.navigate(RecoveryEmail) },
                onOpenDevices = { navController.navigate(Devices) },
            )
        }
        composable<ChangePassword> {
            ChangePasswordScreen(
                onClose = { navController.popBackStack() },
                onPasswordChanged = {
                    navController.navigate(PasswordUpdated) { popUpTo<ChangePassword> { inclusive = true } }
                },
            )
        }
        composable<RecoveryEmail> {
            RecoveryEmailScreen(
                onClose = { navController.popBackStack() },
                onCodeSent = { email -> navController.navigate(VerifyCode(email, CodePurpose.RECOVERY_EMAIL)) },
            )
        }
        composable<RecoveryEmailSaved> {
            RecoveryEmailSavedScreen(onBackToAccounts = { navController.popBackStack<PrivacySecurity>(inclusive = false) })
        }
        composable<NotificationSettings> { NotificationSettingsScreen(onClose = { navController.popBackStack() }) }
        composable<Appearance> { AppearanceScreen(onClose = { navController.popBackStack() }) }
        composable<Language> { LanguageScreen(onClose = { navController.popBackStack() }) }
        composable<Achievements> { AchievementsScreen(onClose = { navController.popBackStack() }) }
        composable<Devices> {
            DevicesScreen(
                onClose = { navController.popBackStack() },
                onDeviceSignedOut = { navController.navigate(DeviceSignedOut) },
                onSessionEnded = {
                    navController.navigate(Login) { popUpTo(0) { inclusive = true } }
                },
            )
        }
        composable<DeviceSignedOut> {
            DeviceSignedOutScreen(onBackToDevices = { navController.popBackStack<Devices>(inclusive = false) })
        }
        composable<Help> { HelpScreen(onClose = { navController.popBackStack() }) }
        composable<About> { AboutScreen(onClose = { navController.popBackStack() }) }
        composable<PasswordUpdated> {
            PasswordUpdatedScreen(onDone = { navController.popBackStack<PrivacySecurity>(inclusive = false) })
        }
        composable<ProfileUpdated> {
            ProfileUpdatedScreen(onBackToProfile = { navController.backToProfile() })
        }
    }
}
