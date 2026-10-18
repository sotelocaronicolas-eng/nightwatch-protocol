package com.example.nightwatchprotocol.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.nightwatchprotocol.ui.theme.NightWatchProtocolTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** A keyboard inset that the test can change, standing in for `WindowInsets.ime`. */
private class FakeKeyboard : WindowInsets {
    var bottomPx by mutableIntStateOf(0)

    override fun getLeft(density: Density, layoutDirection: LayoutDirection) = 0
    override fun getTop(density: Density) = 0
    override fun getRight(density: Density, layoutDirection: LayoutDirection) = 0
    override fun getBottom(density: Density) = bottomPx
}

/**
 * The button must not move when the keyboard opens. The test plays the role of `NightWatchScreenBackground`: it
 * shrinks the area by the keyboard height (what `imePadding()` does) and passes the same inset to the layout.
 * Needs a device or emulator (`connectedDebugAndroidTest`); it is compiled by `compileDebugAndroidTestKotlin`.
 */
class BottomActionScrollLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val keyboard = FakeKeyboard()

    private fun show() {
        composeTestRule.setContent {
            NightWatchProtocolTheme {
                val density = LocalDensity.current
                Box(modifier = Modifier.width(400.dp).height(800.dp)) {
                    Box(modifier = Modifier.fillMaxSize().padding(bottom = with(density) { keyboard.bottomPx.toDp() })) {
                        BottomActionScrollLayout(
                            actionBottomPadding = 70.dp,
                            imeInsets = keyboard,
                            action = { Box(modifier = Modifier.size(width = 200.dp, height = 47.dp).testTag("action")) },
                        ) {
                            Text("Contenido")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun withoutTheKeyboard_theButtonIsAtTheBottom_70dpAboveTheEdge() {
        show()

        val bounds = composeTestRule.onNodeWithTag("action").getUnclippedBoundsInRoot()

        assertEquals(800f - 70f, bounds.bottom.value, 1f)
    }

    @Test
    fun whenTheKeyboardOpens_theButtonKeepsItsPosition() {
        show()
        val before = composeTestRule.onNodeWithTag("action").getUnclippedBoundsInRoot()

        composeTestRule.runOnIdle { keyboard.bottomPx = 900 } // about 300 dp at a 3x density
        composeTestRule.waitForIdle()
        val after = composeTestRule.onNodeWithTag("action").getUnclippedBoundsInRoot()

        assertEquals(before.top.value, after.top.value, 1f)
        assertEquals(before.bottom.value, after.bottom.value, 1f)
    }
}
