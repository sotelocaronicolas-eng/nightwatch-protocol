package com.example.nightwatchprotocol.ui.navigation

import androidx.compose.runtime.Composable
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
import com.example.nightwatchprotocol.ui.profile.myprofile.MyProfileScreen
import com.example.nightwatchprotocol.ui.profile.usermenu.UserMenuScreen
import com.example.nightwatchprotocol.ui.quiz.PlacementQuizScreen
import com.example.nightwatchprotocol.ui.quiz.PlacementResultScreen

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
                onSignedOut = {
                    navController.navigate(Login) { popUpTo<MainMenu> { inclusive = true } }
                },
            )
        }
        composable<Profile> {
            MyProfileScreen(onClose = { navController.popBackStack() })
        }
    }
}
