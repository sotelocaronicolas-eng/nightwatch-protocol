package com.example.nightwatchprotocol.ui.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import org.junit.Rule
import org.junit.Test

class NightWatchNavHostStartDestinationTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun startDestination_canBeOverriddenToHome() {
        composeTestRule.setContent {
            NightWatchProtocolTheme {
                NightWatchNavHost(startDestination = Home())
            }
        }

        // Inicio shows the exit icon (its description is "Cerrar sesión"); it replaced the old Main Menu button.
        composeTestRule.onNodeWithContentDescription("Cerrar sesión").assertExists()
    }
}
