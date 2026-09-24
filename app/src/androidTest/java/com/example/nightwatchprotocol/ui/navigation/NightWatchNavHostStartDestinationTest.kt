package com.example.nightwatchprotocol.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import org.junit.Rule
import org.junit.Test

class NightWatchNavHostStartDestinationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun startDestination_canBeOverriddenToMainMenu() {
        composeTestRule.setContent {
            NightWatchProtocolTheme {
                NightWatchNavHost(startDestination = MainMenu)
            }
        }

        composeTestRule.onNodeWithText("Cerrar sesión").assertExists()
    }
}
