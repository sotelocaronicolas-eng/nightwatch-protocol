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
import com.example.nightwatchprotocol.ui.mainmenu.MainMenuScreen
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

/** Returns to a fresh "Mi Perfil" (stack: Main Menu > Menú de usuario > Mi Perfil) so it shows the saved data. */
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
                        // The session is open now: the same road as a normal sign-up (quiz or main menu).
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
                    navController.navigate(MainMenu) { popUpTo<PostAuthGate> { inclusive = true } }
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
                    navController.navigate(MainMenu) { popUpTo<PlacementResultRoute> { inclusive = true } }
                },
            )
        }
        composable<MainMenu> {
            MainMenuScreen(
                onSignOut = {
                    navController.navigate(Login) { popUpTo<MainMenu> { inclusive = true } }
                },
                onOpenAccount = { navController.navigate(UserMenu) },
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
                    navController.navigate(Login) { popUpTo<MainMenu> { inclusive = true } }
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
