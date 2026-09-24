package com.example.nightwatchprotocol.ui.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.nightwatchprotocol.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NightWatchNavHostTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startDestinationIsLogin() {
        composeTestRule.onNodeWithText("INICIAR SESIÓN").assertExists()
    }

    @Test
    fun registerLink_navigatesToRegisterScreen_andBackReturnsToLogin() {
        composeTestRule.onNodeWithText("Regístrate aquí").performClick()

        composeTestRule.onNodeWithText("Registro de Usuario").assertExists()

        pressBack()

        composeTestRule.onNodeWithText("INICIAR SESIÓN").assertExists()
    }
}
