package com.example.nightwatchprotocol.ui.home

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NightWatchBottomBarTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var socialClicks = 0
    private var profileClicks = 0
    private val selectedTabs = mutableListOf<HomeTab>()

    private fun setBar(selected: HomeTab = HomeTab.Inicio) {
        composeTestRule.setContent {
            NightWatchProtocolTheme {
                NightWatchBottomBar(
                    selected = selected,
                    onTabSelected = { selectedTabs += it },
                    onSocialClick = { socialClicks++ },
                    onProfileClick = { profileClicks++ },
                )
            }
        }
    }

    @Test
    fun showsTheFiveFigmaLabels() {
        setBar()

        listOf("Inicio", "Ruta", "Labs", "Perfil").forEach { composeTestRule.onNodeWithText(it).assertExists() }
        // Social is read as "Social, próximamente" (its label is replaced by the description for TalkBack).
        composeTestRule.onNodeWithContentDescription("Social, próximamente").assertExists()
    }

    @Test
    fun theSelectedTab_isMarkedSelected() {
        setBar(selected = HomeTab.Ruta)

        composeTestRule.onNodeWithText("Ruta").assertIsSelected()
    }

    @Test
    fun social_onlyReportsTheClick_andProfileOnlyReportsItsOwn() {
        setBar()

        composeTestRule.onNodeWithContentDescription("Social, próximamente").performClick()
        composeTestRule.onNodeWithText("Perfil").performClick()

        assertEquals(1, socialClicks)
        assertEquals(1, profileClicks)
        assertEquals(emptyList<HomeTab>(), selectedTabs)
    }

    @Test
    fun tappingATab_reportsIt() {
        setBar()

        composeTestRule.onNodeWithText("Labs").performClick()

        assertEquals(listOf(HomeTab.Labs), selectedTabs)
    }
}
