package com.example.nightwatchprotocol.ui.profile.appearance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Instrumented tests: they are compiled with the project but only run on a device or emulator.
class AppearanceAndLanguageScreensTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appearance_showsTheHintAndTheThreeOptions() {
        composeTestRule.setContent { NightWatchProtocolTheme { AppearanceScreen(onClose = {}) } }

        composeTestRule.onNodeWithText("Elige cómo se ve NightWatch Protocol en tu dispositivo.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Oscuro").assertIsDisplayed()
        composeTestRule.onNodeWithText("Claro").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Automático").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun language_showsBothLanguages_andTheCloseButtonWorks() {
        var closed = false
        composeTestRule.setContent { NightWatchProtocolTheme { LanguageScreen(onClose = { closed = true }) } }

        composeTestRule.onNodeWithText("Español").assertIsDisplayed()
        composeTestRule.onNodeWithText("English").assertIsDisplayed()
        composeTestRule.onNodeWithText("X").performClick()

        assertTrue(closed)
    }
}
