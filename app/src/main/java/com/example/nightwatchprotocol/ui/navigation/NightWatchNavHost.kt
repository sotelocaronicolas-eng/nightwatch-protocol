package com.example.nightwatchprotocol.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nightwatchprotocol.data.quiz.QuizCategory
import com.example.nightwatchprotocol.ui.auth.forgotpassword.ForgotPasswordScreen
import com.example.nightwatchprotocol.ui.auth.login.LoginScreen
import com.example.nightwatchprotocol.ui.auth.register.RegisterScreen
import com.example.nightwatchprotocol.ui.mainmenu.MainMenuScreen
import com.example.nightwatchprotocol.ui.postauth.PostAuthGateScreen
import com.example.nightwatchprotocol.ui.profile.changepassword.ChangePasswordScreen
import com.example.nightwatchprotocol.ui.profile.confirmation.PasswordUpdatedScreen
import com.example.nightwatchprotocol.ui.profile.confirmation.ProfileUpdatedScreen
import com.example.nightwatchprotocol.ui.profile.confirmation.RecoveryEmailSavedScreen
import com.example.nightwatchprotocol.ui.profile.confirmidentity.ConfirmIdentityScreen
import com.example.nightwatchprotocol.ui.profile.editprofile.EditProfileScreen
import com.example.nightwatchprotocol.ui.profile.myprofile.MyProfileScreen
import com.example.nightwatchprotocol.ui.profile.privacy.PrivacySecurityScreen
import com.example.nightwatchprotocol.ui.profile.recoveryemail.RecoveryEmailScreen
import com.example.nightwatchprotocol.ui.profile.usermenu.UserMenuScreen
import com.example.nightwatchprotocol.ui.quiz.PlacementQuizScreen
import com.example.nightwatchprotocol.ui.quiz.PlacementResultScreen

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
                onNavigateToForgotPassword = { navController.navigate(ForgotPassword) },
            )
        }
        composable<Register> {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(PostAuthGate) { popUpTo<Login> { inclusive = true } }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<ForgotPassword> {
            ForgotPasswordScreen(onNavigateBack = { navController.popBackStack() })
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
                onOpenPrivacy = { navController.navigate(PrivacySecurity) },
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
                // The email-change flow arrives in a later stage; until then nothing is navigated here.
                onNavigateToChangeEmail = {},
                onNavigateToProfileUpdated = { navController.navigate(ProfileUpdated) },
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
                // The device list arrives in a later stage.
                onOpenDevices = {},
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
                onSaved = {
                    navController.navigate(RecoveryEmailSaved) { popUpTo<RecoveryEmail> { inclusive = true } }
                },
            )
        }
        composable<RecoveryEmailSaved> {
            RecoveryEmailSavedScreen(onBackToAccounts = { navController.popBackStack<PrivacySecurity>(inclusive = false) })
        }
        composable<PasswordUpdated> {
            PasswordUpdatedScreen(onDone = { navController.popBackStack<PrivacySecurity>(inclusive = false) })
        }
        composable<ProfileUpdated> {
            ProfileUpdatedScreen(onBackToProfile = { navController.backToProfile() })
        }
    }
}
