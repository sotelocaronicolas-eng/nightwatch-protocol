package com.example.nightwatchprotocol.ui.home

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NightWatchBottomBarTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private var profileClicks = 0
    private val selectedTabs = mutableListOf<HomeTab>()

    private fun setBar(selected: HomeTab = HomeTab.Inicio) {
        composeTestRule.setContent {
            NightWatchProtocolTheme {
                NightWatchBottomBar(
                    selected = selected,
                    onTabSelected = { selectedTabs += it },
                    onProfileClick = { profileClicks++ },
                )
            }
        }
    }

    @Test
    fun showsTheFiveFigmaLabels() {
        setBar()

        listOf("Inicio", "Ruta", "Labs", "Social", "Perfil").forEach { composeTestRule.onNodeWithText(it).assertExists() }
    }

    @Test
    fun theSelectedTab_isMarkedSelected() {
        setBar(selected = HomeTab.Ruta)

        composeTestRule.onNodeWithText("Ruta").assertIsSelected()
    }

    @Test
    fun social_isATab_itReportsItAsSelected_andProfileOnlyReportsItsOwnClick() {
        setBar()

        composeTestRule.onNodeWithText("Social").performClick()
        composeTestRule.onNodeWithText("Perfil").performClick()

        assertEquals(listOf(HomeTab.Social), selectedTabs)
        assertEquals(1, profileClicks)
    }

    @Test
    fun theSocialTab_isMarkedSelectedWhenItIsTheCurrentOne() {
        setBar(selected = HomeTab.Social)

        composeTestRule.onNodeWithText("Social").assertIsSelected()
    }

    @Test
    fun tappingATab_reportsIt() {
        setBar()

        composeTestRule.onNodeWithText("Labs").performClick()

        assertEquals(listOf(HomeTab.Labs), selectedTabs)
    }
}
