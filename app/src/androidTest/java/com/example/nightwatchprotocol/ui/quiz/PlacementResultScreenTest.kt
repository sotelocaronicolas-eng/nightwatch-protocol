package com.example.nightwatchprotocol.ui.quiz

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nightwatchprotocol.ui.navigation.PlacementResultRoute
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import org.junit.Rule
import org.junit.Test

class PlacementResultScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun showsLevelAndStudyPlan_andContinueFiresCallback() {
        var continueClicked = false

        composeTestRule.setContent {
            NightWatchProtocolTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = PlacementResultRoute(
                        level = "MEDIO",
                        weakCategories = listOf("CONTRASENAS"),
                        saveFailed = false,
                    ),
                ) {
                    composable<PlacementResultRoute> {
                        PlacementResultScreen(onContinue = { continueClicked = true })
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Tu nivel: MEDIO").assertExists()

        composeTestRule.onNodeWithText("Continuar").performClick()

        assert(continueClicked) { "onContinue was not called after tapping Continuar" }
    }
}
