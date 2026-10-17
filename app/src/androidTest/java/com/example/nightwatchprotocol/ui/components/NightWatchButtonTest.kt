package com.example.nightwatchprotocol.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class NightWatchButtonTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun disabledButton_doesNotFireOnClick() {
        var clicked = false
        composeTestRule.setContent {
            NightWatchProtocolTheme {
                NightWatchButton(text = "INICIAR SESIÓN", onClick = { clicked = true }, enabled = false)
            }
        }

        composeTestRule.onNodeWithText("INICIAR SESIÓN").performClick()

        assertFalse("onClick fired on a disabled button", clicked)
    }
}
