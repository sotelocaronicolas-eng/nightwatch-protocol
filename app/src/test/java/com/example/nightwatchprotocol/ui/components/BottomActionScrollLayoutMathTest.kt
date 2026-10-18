package com.example.nightwatchprotocol.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class BottomActionScrollLayoutMathTest {

    @Test
    fun withoutTheKeyboard_theContentIsAsTallAsTheArea() {
        assertEquals(2000, contentMinHeightPx(viewportPx = 2000, imeBottomPx = 0))
    }

    @Test
    fun withTheKeyboard_theContentKeepsTheHeightTheScreenHadWithoutIt() {
        // 2000 px with the keyboard closed. The keyboard takes 900: the area shrinks to 1100, the content stays at 2000.
        val imeBottomPx = 900
        val viewportWithKeyboard = 2000 - imeBottomPx

        assertEquals(2000, contentMinHeightPx(viewportWithKeyboard, imeBottomPx))
    }

    @Test
    fun theHeightIsTheSameAtEveryFrameOfTheKeyboardAnimation() {
        val heights = (0..900 step 150).map { ime -> contentMinHeightPx(viewportPx = 2000 - ime, imeBottomPx = ime) }

        assertEquals(setOf(2000), heights.toSet())
    }

    @Test
    fun aNegativeInsetNeverShrinksTheContent() {
        assertEquals(2000, contentMinHeightPx(viewportPx = 2000, imeBottomPx = -50))
    }
}
