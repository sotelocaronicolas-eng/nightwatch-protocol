package com.example.nightwatchprotocol.ui.quiz

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import org.junit.Rule
import org.junit.Test

class PlacementQuizScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun selectingAnOption_enablesNextButton() {
        composeTestRule.setContent {
            NightWatchProtocolTheme {
                PlacementQuizScreen(onFinished = { _, _, _ -> })
            }
        }

        composeTestRule.onNodeWithText("Siguiente").assertIsNotEnabled()

        composeTestRule.onNodeWithTag("quiz_option_0").performClick()

        composeTestRule.onNodeWithText("Siguiente").assertIsEnabled()
    }
}
