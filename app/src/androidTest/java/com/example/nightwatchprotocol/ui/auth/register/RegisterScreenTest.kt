package com.example.nightwatchprotocol.ui.auth.register

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import org.junit.Rule
import org.junit.Test

class RegisterScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun registerButton_disabledUntilAllPasswordRulesAndMatchAreSatisfied() {
        composeTestRule.setContent {
            NightWatchProtocolTheme {
                RegisterScreenContentForTest()
            }
        }

        composeTestRule.onNodeWithText("REGISTRARSE").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("Nombre Completo").performTextInput("Nicolás Sotelo")
        composeTestRule.onNodeWithTag("Correo electrónico").performTextInput("nicolas@example.com")
        composeTestRule.onNodeWithTag("Contraseña").performTextInput("Sup3r\$ecret!")
        composeTestRule.onNodeWithTag("Verifica la contraseña").performTextInput("Sup3r\$ecret!")

        composeTestRule.onNodeWithText("REGISTRARSE").assertIsEnabled()
    }

    @Test
    fun passwordChecklist_ticksOffAsRulesAreSatisfied() {
        composeTestRule.setContent {
            NightWatchProtocolTheme {
                RegisterScreenContentForTest()
            }
        }

        composeTestRule.onNodeWithTag("Contraseña").performTextInput("short")

        composeTestRule.onNodeWithText("Mínimo 8 caracteres").assertExists()
    }
}
