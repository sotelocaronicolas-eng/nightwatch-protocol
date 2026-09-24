package com.example.nightwatchprotocol.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nightwatchprotocol.ui.auth.forgotpassword.ForgotPasswordScreen
import com.example.nightwatchprotocol.ui.auth.login.LoginScreen
import com.example.nightwatchprotocol.ui.auth.register.RegisterScreen
import com.example.nightwatchprotocol.ui.mainmenu.MainMenuScreen

@Composable
fun NightWatchNavHost(
    navController: NavHostController = rememberNavController(),
    startDestination: Any = Login,
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable<Login> {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(MainMenu) { popUpTo<Login> { inclusive = true } }
                },
                onNavigateToRegister = { navController.navigate(Register) },
                onNavigateToForgotPassword = { navController.navigate(ForgotPassword) },
            )
        }
        composable<Register> {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(MainMenu) { popUpTo<Login> { inclusive = true } }
                },
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable<ForgotPassword> {
            ForgotPasswordScreen(onNavigateBack = { navController.popBackStack() })
        }
        composable<MainMenu> {
            MainMenuScreen(
                onSignOut = {
                    navController.navigate(Login) { popUpTo<MainMenu> { inclusive = true } }
                },
            )
        }
    }
}
